package au.com.shiftyjelly.pocketcasts.servers.accordion

/**
 * A single playable audio "variant" of an episode returned by the Accordion API.
 *
 * @property url The stream URL ExoPlayer should play when this variant is selected.
 * @property durationSeconds Duration of this variant in seconds (0 if unknown).
 */
data class AccordionVariant(
    val url: String,
    val durationSeconds: Long,
)
