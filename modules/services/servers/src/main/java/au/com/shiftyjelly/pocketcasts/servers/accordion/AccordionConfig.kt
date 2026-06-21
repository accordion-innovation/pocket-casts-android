package au.com.shiftyjelly.pocketcasts.servers.accordion

/**
 * Configuration for the Accordion API (audio "variants" of an episode published to Google Cloud
 * Storage and served behind the Accordion content API).
 *
 * TODO: Move [BASE_URL] and [API_KEY] into BuildConfig / local.properties (per build flavor) so the
 * key is not committed to source control. They are kept here for now to isolate the single place
 * that needs to change once the secrets pipeline is wired up.
 */
object AccordionConfig {
    /** Base URL of the Accordion API. Must end with a trailing slash for Retrofit. */
    const val BASE_URL = "https://accordion.live/"

    /**
     * Developer API key sent as the `api_key` query parameter. This must match an active row in the
     * Supabase `ApiKeys` table, otherwise the Accordion API returns 401 and no variants are loaded.
     */
    const val API_KEY = "REPLACE_WITH_ACCORDION_API_KEY"
}
