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
    @AccordionApiKey private val apiKey: String,
) {
    /**
     * Returns the audio variants for the episode identified by [podcastHash] (md5 of the podcast's
     * website link, i.e. `Podcast.podcastUrl`) and [episodeTitle]. Returns an empty list if the
     * episode has no variants. Throws if the network call fails so the caller can surface or
     * swallow the error as appropriate.
     *
     * Variants the API returns that this app cannot safely play are dropped here rather than
     * further down, so nothing unvalidated ever reaches the player or the episode.
     */
    suspend fun getVariants(podcastHash: String, episodeTitle: String): List<AccordionVariant> {
        // An empty key is a build-configuration mistake, not a network condition: the request is
        // guaranteed to be rejected, so fail with a message that names the actual problem instead of
        // letting it surface as an opaque auth redirect.
        if (apiKey.isBlank()) {
            throw AccordionException(
                "No Accordion API key in this build. Set the ACCORDION_API_KEY env var or the " +
                    "accordionApiKey secret property and rebuild.",
            )
        }

        val response = service.getEpisodeByTitle(
            podcastHash = podcastHash,
            episodeTitle = episodeTitle,
            apiKey = apiKey,
        )
        if (!response.isSuccessful) {
            throw AccordionException(describeFailure(response.code(), response.raw().header("Location")))
        }
        val body = response.body() ?: throw AccordionException("Accordion API returned an empty body")

        return body.podcast?.episode?.audioFiles.orEmpty()
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

    /**
     * Turns a non-2xx into a message that identifies the cause. The redirect case is called out
     * specifically: accordion.live sends a 303 to `/direct-login` for any content request it does
     * not consider authenticated, which is the symptom of a rejected or unsupported API key rather
     * than of a missing episode.
     */
    private fun describeFailure(code: Int, location: String?): String = when {
        code == 303 || code == 302 || code == 301 -> {
            "Accordion API redirected to $location (HTTP $code) instead of returning JSON. The " +
                "request was not accepted as authenticated - check that the API key is valid and " +
                "that the server reads it from the X-Api-Key header."
        }
        code == 401 || code == 403 -> "Accordion API rejected the API key (HTTP $code)"
        code == 404 -> {
            "Accordion API has no such route or episode (HTTP 404). Confirm the deployed server " +
                "exposes the api_v1/content/{podcastHash}/by-title lookup."
        }
        else -> "Accordion API request failed (HTTP $code)"
    }

    companion object {
        /** The panel lays every variant out across a single row, so cap the list at what can fit. */
        private const val MAX_VARIANTS = 8

        /** Longer than any plausible episode. Keeps a bogus duration off the player's progress bar. */
        private const val MAX_DURATION_SECONDS = 24 * 60 * 60.0
    }
}
