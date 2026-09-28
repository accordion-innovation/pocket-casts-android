package au.com.shiftyjelly.pocketcasts.servers.accordion

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Fetches the available audio [AccordionVariant]s for an episode.
 *
 * Variants this app cannot safely play are dropped here rather than further down, so nothing
 * unvalidated reaches the player. See `docs/accordion.md`.
 */
@Singleton
class AccordionManager @Inject constructor(
    private val service: AccordionService,
    @AccordionApiKey private val apiKey: String,
) {
    /**
     * Returns the audio variants for the episode identified by [podcastHash] and [episodeTitle], or
     * an empty list if it has none. Throws [AccordionException] if the lookup fails, so the caller
     * can surface or swallow the error as appropriate.
     */
    suspend fun getVariants(podcastHash: String, episodeTitle: String): List<AccordionVariant> {
        // A build-configuration mistake, not a network condition: the request is guaranteed to be
        // rejected, so fail with a message naming the actual problem.
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
            .mapNotNull { it.toVariantOrNull() }
            .take(MAX_VARIANTS)
            .toList()
    }

    /** https only: the player resolves `file://`, `content://` and `asset://` just as readily. */
    private fun AccordionAudioFile.toVariantOrNull(): AccordionVariant? {
        val httpsUrl = url?.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        return AccordionVariant(url = httpsUrl.toString(), durationSeconds = sanitizedDurationSeconds())
    }

    /** 0 when the API omits a duration or reports one that cannot describe an episode. */
    private fun AccordionAudioFile.sanitizedDurationSeconds(): Long {
        val seconds = durationSeconds ?: return 0L
        val isPlausible = seconds.isFinite() && seconds > 0 && seconds <= MAX_DURATION_SECONDS
        return if (isPlausible) seconds.toLong() else 0L
    }

    private fun describeFailure(code: Int, location: String?): String = when (code) {
        // accordion.live redirects to its HTML login page for anything it does not consider
        // authenticated, so this means a rejected key rather than a missing episode.
        301, 302, 303 -> {
            "Accordion API redirected to $location (HTTP $code) instead of returning JSON. The " +
                "request was not accepted as authenticated - check that the API key is valid and " +
                "that the server reads it from the X-Api-Key header."
        }

        401, 403 -> "Accordion API rejected the API key (HTTP $code)"

        404 -> {
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
