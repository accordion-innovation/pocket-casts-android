package au.com.shiftyjelly.pocketcasts.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.servers.accordion.AccordionManager
import au.com.shiftyjelly.pocketcasts.servers.accordion.AccordionVariant
import au.com.shiftyjelly.pocketcasts.utils.extensions.md5
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
 * The episode is identified to the Accordion API the same way as the existing web content API:
 * `podcast_hash` = md5(podcast RSS feed url) and `episode_hash` = md5(episode title).
 *
 * NOTE: Pocket Casts does not store the original RSS `<guid>`, so [episodeHash] is derived from the
 * episode title (matching the Accordion server's no-guid fallback). If the server keys episodes on
 * md5(guid) instead, the guid must be plumbed through here.
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

    /** Fetch the variants for whatever episode is currently loaded in the player. */
    fun loadVariantsForCurrentEpisode() {
        val episode = playbackManager.getCurrentEpisode() as? PodcastEpisode
        if (episode == null) {
            _uiState.value = UiState.Hidden
            return
        }
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val variants = try {
                val podcast = podcastManager.findPodcastByUuid(episode.podcastUuid)
                val feedUrl = podcast?.podcastUrl
                val podcastHash = feedUrl?.takeIf { it.isNotBlank() }?.md5()
                val episodeHash = episode.title.takeIf { it.isNotBlank() }?.md5()
                if (podcastHash == null || episodeHash == null) {
                    emptyList()
                } else {
                    accordionManager.getVariants(podcastHash = podcastHash, episodeHash = episodeHash)
                }
            } catch (e: Exception) {
                LogBuffer.e(LogBuffer.TAG_PLAYBACK, e, "Failed to load Accordion variants")
                emptyList()
            }

            // Only show the panel when there is an actual choice to make.
            _uiState.value = if (variants.size < 2) {
                UiState.Hidden
            } else {
                UiState.Loaded(variants = variants, selectedIndex = 0)
            }
        }
    }

    /** Select the variant at [index] and swap the player to its url. */
    fun onVariantSelected(index: Int) {
        val state = _uiState.value as? UiState.Loaded ?: return
        if (index == state.selectedIndex) return
        val variant = state.variants.getOrNull(index) ?: return
        _uiState.value = state.copy(selectedIndex = index)
        viewModelScope.launch {
            playbackManager.swapToVariantUrl(variant.url)
        }
    }
}
