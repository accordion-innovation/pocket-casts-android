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
 * Loads the Accordion audio variants for the currently-playing episode and swaps the player to a
 * different variant when the user selects one.
 *
 * See `docs/accordion.md` for how episodes are identified and why showing the panel deliberately
 * leaves the player alone.
 */
@HiltViewModel
class AccordionVariantViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val podcastManager: PodcastManager,
    private val accordionManager: AccordionManager,
) : ViewModel() {

    sealed interface UiState {
        data object Hidden : UiState

        data object Loading : UiState

        data class Loaded(
            val variants: List<AccordionVariant>,
            val selectedIndex: Int,
        ) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Hidden)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Uuid of the episode [uiState]'s variants belong to, so a stale panel is never acted on. */
    private var loadedEpisodeUuid: String? = null

    private var isApplyingVariant = false

    /** Fetch the variants for whatever episode is currently loaded in the player. */
    fun loadVariantsForCurrentEpisode() {
        // Swapping recreates the player, which re-fires the observer that calls this. Ignore that
        // transient re-entry rather than reading it as a real playback change.
        if (isApplyingVariant) return

        val episode = currentSwappableEpisode()
        if (episode == null) {
            hide()
            return
        }
        if (episode.uuid == loadedEpisodeUuid) return

        loadedEpisodeUuid = episode.uuid
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val variants = fetchVariants(episode)

            // A response that arrives after the episode changed must not replace the panel
            // belonging to whatever is playing now.
            if (loadedEpisodeUuid != episode.uuid) return@launch

            _uiState.value = if (variants.size < 2) {
                // Nothing to choose between. Keep loadedEpisodeUuid set so this stays resolved and
                // we don't re-request on every playback change.
                UiState.Hidden
            } else {
                UiState.Loaded(variants = variants, selectedIndex = indexOfLongest(variants))
            }
        }
    }

    /** Select the variant at [index] and swap the player to its url. */
    fun onVariantSelected(index: Int) {
        val state = _uiState.value as? UiState.Loaded ?: return
        if (index == state.selectedIndex) return
        val variant = state.variants.getOrNull(index) ?: return

        // The swap applies to whatever is playing now, so if that is no longer the episode these
        // variants belong to, applying one would put the wrong audio on the wrong episode.
        if (playbackManager.getCurrentEpisode()?.uuid != loadedEpisodeUuid) {
            hide()
            return
        }

        // Move the selection straight away so the control feels responsive, then reconcile if the
        // player could not actually apply it.
        _uiState.value = state.copy(selectedIndex = index)
        viewModelScope.launch {
            isApplyingVariant = true
            val applied = try {
                playbackManager.swapToVariantUrl(variant.url, variant.durationSeconds)
            } finally {
                isApplyingVariant = false
            }
            if (!applied) hide()
        }
    }

    /**
     * The playing episode when a variant could actually be applied to it, otherwise null.
     *
     * A downloaded episode plays from its local file and has no stream url to swap, so don't offer
     * a choice that cannot be applied, and don't spend a request discovering that.
     */
    private fun currentSwappableEpisode(): PodcastEpisode? {
        if (!FeatureFlag.isEnabled(Feature.ACCORDION_AUDIO_VARIANTS)) return null
        return (playbackManager.getCurrentEpisode() as? PodcastEpisode)?.takeUnless { it.isDownloaded }
    }

    private suspend fun fetchVariants(episode: PodcastEpisode): List<AccordionVariant> = try {
        val podcastHash = podcastManager.findPodcastByUuid(episode.podcastUuid)
            ?.podcastUrl
            ?.takeIf { it.isNotBlank() }
            ?.md5()
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

    /**
     * The longest variant is the closest match to the full RSS audio already playing, so the panel
     * opens showing roughly what the user is hearing.
     */
    private fun indexOfLongest(variants: List<AccordionVariant>) = variants.indices.maxByOrNull { variants[it].durationSeconds } ?: 0

    /** Hide the panel and forget the episode, so the next load re-evaluates from scratch. */
    private fun hide() {
        loadedEpisodeUuid = null
        _uiState.value = UiState.Hidden
    }
}
