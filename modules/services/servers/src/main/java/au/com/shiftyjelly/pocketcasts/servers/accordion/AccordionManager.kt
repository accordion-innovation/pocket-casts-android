package au.com.shiftyjelly.pocketcasts.servers.accordion

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Fetches the available audio [AccordionVariant]s for an episode from the Accordion API.
 */
@Singleton
class AccordionManager @Inject constructor(
    private val service: AccordionService,
) {
    /**
     * Returns the audio variants for the episode identified by [podcastHash] (md5 of the RSS feed
     * url) and [episodeTitle]. Returns an empty list if the episode has no variants. Throws if the
     * network call fails so the caller can surface or swallow the error as appropriate.
     *
     * Variants the API returns that this app cannot safely play are dropped here rather than
     * further down, so nothing unvalidated ever reaches the player or the episode.
     */
    suspend fun getVariants(podcastHash: String, episodeTitle: String): List<AccordionVariant> {
        val response = service.getEpisodeByTitle(
            podcastHash = podcastHash,
            episodeTitle = episodeTitle,
            apiKey = AccordionConfig.API_KEY,
        )
        return response.podcast?.episode?.audioFiles.orEmpty()
            .asSequence()
            .mapNotNull { audioFile -> audioFile.toVariantOrNull() }
            .take(MAX_VARIANTS)
            .toList()
    }

    private fun AccordionAudioFile.toVariantOrNull(): AccordionVariant? {
        // This url is handed to ExoPlayer and written onto the playing episode, and the player's
        // DefaultDataSource resolves file://, content:// and asset:// just as readily as http. Only
        // accept https so a tampered or compromised response cannot aim the player at local storage
        // or at a cleartext connection.
        val url = url?.toHttpUrlOrNull()?.takeIf { it.isHttps }?.toString() ?: return null
        return AccordionVariant(url = url, durationSeconds = sanitizedDurationSeconds())
    }

    /** 0 when the API omits a duration or reports one that cannot describe an episode. */
    private fun AccordionAudioFile.sanitizedDurationSeconds(): Long {
        val seconds = durationSeconds ?: return 0L
        val isPlausible = seconds.isFinite() && seconds > 0 && seconds <= MAX_DURATION_SECONDS
        return if (isPlausible) seconds.toLong() else 0L
    }

    companion object {
        /** The panel lays every variant out across a single row, so cap the list at what can fit. */
        private const val MAX_VARIANTS = 8

        /** Longer than any plausible episode. Keeps a bogus duration off the player's progress bar. */
        private const val MAX_DURATION_SECONDS = 24 * 60 * 60.0
    }
}
