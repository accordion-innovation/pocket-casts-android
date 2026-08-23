package au.com.shiftyjelly.pocketcasts.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.servers.accordion.AccordionManager
import au.com.shiftyjelly.pocketcasts.servers.accordion.AccordionVariant
import au.com.shiftyjelly.pocketcasts.utils.extensions.md5
import au.com.shiftyjelly.pocketcasts.utils.featureflag.Feature
import au.com.shiftyjelly.pocketcasts.utils.featureflag.FeatureFlag
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads the available Accordion audio "variants" for the currently-playing episode and swaps the
 * player to a different variant when the user selects one.
 *
 * The episode is identified to the Accordion API by podcast + title (feed+title lookup):
 * `podcast_hash` = md5(podcast RSS feed url) and the raw `episode_title`. Pocket Casts does not
 * store the RSS `<guid>` that Accordion's `episode_hash` is derived from, so the server resolves
 * the episode by title within the podcast instead.
 */
@HiltViewModel
class AccordionVariantViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val podcastManager: PodcastManager,
    private val accordionManager: AccordionManager,
) : ViewModel() {

    sealed interface UiState {
        /** No variants to show (single variant, no episode, or failed to load): panel stays hidden. */
        data object Hidden : UiState

        /** Fetching variants from the Accordion API. */
        data object Loading : UiState

        /** Two or more variants are available. */
        data class Loaded(
            val variants: List<AccordionVariant>,
            val selectedIndex: Int,
        ) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Hidden)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Uuid of the episode [uiState]'s variants belong to, so a stale panel is never acted on. */
    private var loadedEpisodeUuid: String? = null

    /**
     * Fetch the variants for whatever episode is currently loaded in the player. Safe to call on
     * every playback change: it is a no-op while already showing the current episode's variants, so
     * the user's selection survives unrelated updates.
     */
    fun loadVariantsForCurrentEpisode() {
        // Looking an episode up tells accordion.live what the user is listening to, so nothing is
        // requested unless the feature is switched on for this install.
        if (!FeatureFlag.isEnabled(Feature.ACCORDION_AUDIO_VARIANTS)) {
            hide()
            return
        }
        val episode = playbackManager.getCurrentEpisode() as? PodcastEpisode
        if (episode == null) {
            hide()
            return
        }
        // A downloaded episode plays from its local file, so the player cannot swap its stream url.
        // Don't offer a choice that can't be applied (and don't spend a request discovering it).
        if (episode.isDownloaded) {
            hide()
            return
        }
        if (episode.uuid == loadedEpisodeUuid) {
            return
        }
        loadedEpisodeUuid = episode.uuid
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val variants = try {
                val podcast = podcastManager.findPodcastByUuid(episode.podcastUuid)
                // Only the hash is ever logged. A feed url can carry a per-user access token for a
                // private or premium feed, and LogBuffer ends up in the debug log users attach to
                // support emails.
                val podcastHash = podcast?.podcastUrl?.takeIf { it.isNotBlank() }?.md5()
                val episodeTitle = episode.title.takeIf { it.isNotBlank() }
                if (podcastHash == null || episodeTitle == null) {
                    emptyList()
                } else {
                    accordionManager.getVariants(podcastHash = podcastHash, episodeTitle = episodeTitle)
                }
            } catch (e: Exception) {
                LogBuffer.e(LogBuffer.TAG_PLAYBACK, e, "Accordion: failed to load variants")
                emptyList()
            }

            // The episode may have changed while this request was in flight; a late response must not
            // replace the panel belonging to whatever is playing now.
            if (loadedEpisodeUuid != episode.uuid) {
                return@launch
            }

            // Only show the panel when there is an actual choice to make. Keep loadedEpisodeUuid set
            // so this stays resolved for the episode and we don't re-request on every playback change.
            if (variants.size < 2) {
                _uiState.value = UiState.Hidden
                return@launch
            }

            // Default to the longest variant: it best represents the full RSS audio that is
            // currently playing, so the displayed selection matches what the user hears.
            // Immediately swap to it so the player's duration and stream URL are both accurate
            // from the moment the panel appears.
            val longestIndex = variants.indices.maxByOrNull { variants[it].durationSeconds } ?: 0
            _uiState.value = UiState.Loaded(variants = variants, selectedIndex = longestIndex)

            val longestVariant = variants[longestIndex]
            if (!playbackManager.swapToVariantUrl(longestVariant.url, longestVariant.durationSeconds)) {
                hide()
            }
        }
    }

    /** Select the variant at [index] and swap the player to its url. */
    fun onVariantSelected(index: Int) {
        val state = _uiState.value as? UiState.Loaded ?: return
        if (index == state.selectedIndex) return
        val variant = state.variants.getOrNull(index) ?: return

        // The player applies the swap to whatever is playing now. If that is no longer the episode
        // these variants were loaded for, applying one would put the wrong audio on the wrong
        // episode, so drop the panel instead.
        val currentUuid = playbackManager.getCurrentEpisode()?.uuid
        if (currentUuid == null || currentUuid != loadedEpisodeUuid) {
            hide()
            return
        }

        // Move the selection straight away so the control feels responsive, then reconcile below if
        // the player could not actually apply it.
        _uiState.value = state.copy(selectedIndex = index)
        viewModelScope.launch {
            if (!playbackManager.swapToVariantUrl(variant.url, variant.durationSeconds)) {
                // Variant switching no longer applies to this episode (it finished downloading while
                // the panel was open). Leaving the control up would silently do nothing on every drag.
                hide()
            }
        }
    }

    /** Hide the panel and forget the episode, so the next load re-evaluates from scratch. */
    private fun hide() {
        loadedEpisodeUuid = null
        _uiState.value = UiState.Hidden
    }
}
