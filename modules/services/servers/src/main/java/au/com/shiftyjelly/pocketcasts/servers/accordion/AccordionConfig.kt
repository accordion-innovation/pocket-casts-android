package au.com.shiftyjelly.pocketcasts.servers.accordion

import au.com.shiftyjelly.pocketcasts.preferences.Settings

/**
 * Configuration for the Accordion API (audio "variants" of an episode published to Google Cloud
 * Storage and served behind the Accordion content API).
 */
object AccordionConfig {
    /** Base URL of the Accordion API. Must end with a trailing slash for Retrofit. */
    const val BASE_URL = "https://www.accordion.live/"

    /**
     * Developer API key sent as the `api_key` query parameter. Sourced from the `ACCORDION_API_KEY`
     * environment variable at build time (see `fastlane/env/user.env-example` and
     * `dependencies.gradle.kts`), surfaced through `BuildConfig` / [Settings]. Must match an active
     * row in the Supabase `ApiKeys` table, otherwise the Accordion API returns 401.
     *
     * NOTE: api-key auth only works once the `verifyApiKey` service-role fix is deployed to
     * accordion.live; until then the production API rejects `?api_key=` with 401.
     */
    const val API_KEY = Settings.ACCORDION_API_KEY
}
