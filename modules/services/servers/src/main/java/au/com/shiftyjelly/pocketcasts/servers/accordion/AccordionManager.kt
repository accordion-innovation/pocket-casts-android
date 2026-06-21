package au.com.shiftyjelly.pocketcasts.servers.accordion

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the available audio [AccordionVariant]s for an episode from the Accordion API.
 */
@Singleton
class AccordionManager @Inject constructor(
    private val service: AccordionService,
) {
    /**
     * Returns the audio variants for the episode identified by [podcastHash] / [episodeHash].
     * Returns an empty list if the episode has no variants. Throws if the network call fails so the
     * caller can surface or swallow the error as appropriate.
     */
    suspend fun getVariants(podcastHash: String, episodeHash: String): List<AccordionVariant> {
        val response = service.getEpisode(
            podcastHash = podcastHash,
            episodeHash = episodeHash,
            apiKey = AccordionConfig.API_KEY,
        )
        return response.podcast?.episode?.audioFiles.orEmpty().mapNotNull { audioFile ->
            val url = audioFile.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AccordionVariant(
                url = url,
                durationSeconds = audioFile.durationSeconds?.toLong() ?: 0L,
            )
        }
    }
}
