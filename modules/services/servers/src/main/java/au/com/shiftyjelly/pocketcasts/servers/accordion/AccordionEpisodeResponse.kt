package au.com.shiftyjelly.pocketcasts.servers.accordion

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from the Accordion content API:
 *
 * ```
 * { "podcast": { "title": "...", "episode": { "title": "...", "audioFiles": [ { "url": "...",
 *   "durationSeconds": 1234 } ] } } }
 * ```
 *
 * `audioFiles` is the list of variants — different lengths or edits of the same episode.
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
