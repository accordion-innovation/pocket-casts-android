package au.com.shiftyjelly.pocketcasts.servers.accordion

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from the Accordion content API. Mirrors the JSON shape already published for Accordion
 * episodes, e.g. `/api_v1/content/{podcastHash}/{episodeHash}`:
 *
 * ```
 * { "podcast": { "title": "...", "episode": { "title": "...", "audioFiles": [ { "url": "...",
 *   "durationSeconds": 1234 } ] } } }
 * ```
 *
 * The `audioFiles` array is the list of audio "variants" (different lengths/edits of the same
 * episode) that the user can switch between.
 */
@JsonClass(generateAdapter = true)
data class AccordionEpisodeResponse(
    @Json(name = "podcast") val podcast: AccordionPodcast? = null,
)

@JsonClass(generateAdapter = true)
data class AccordionPodcast(
    @Json(name = "title") val title: String? = null,
    @Json(name = "episode") val episode: AccordionEpisodeData? = null,
)

@JsonClass(generateAdapter = true)
data class AccordionEpisodeData(
    @Json(name = "title") val title: String? = null,
    @Json(name = "audioFiles") val audioFiles: List<AccordionAudioFile>? = null,
)

@JsonClass(generateAdapter = true)
data class AccordionAudioFile(
    @Json(name = "url") val url: String? = null,
    @Json(name = "durationSeconds") val durationSeconds: Double? = null,
)
