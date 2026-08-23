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
     * Developer API key sent as the `X-Api-Key` header. Sourced from the `ACCORDION_API_KEY`
     * environment variable at build time (see `fastlane/env/user.env-example` and
     * `dependencies.gradle.kts`), surfaced through `BuildConfig` / [Settings]. Must match an active
     * row in the Supabase `ApiKeys` table, otherwise the Accordion API returns 401.
     *
     * This is a build-time constant compiled into the APK, so it is extractable by anyone holding
     * the app. Treat it as a public client identifier for attribution and rate limiting, never as
     * an authorization secret: the server must not grant anything on the strength of the key alone.
     *
     * NOTE: requires the server to read the key from the `X-Api-Key` header, alongside the
     * `verifyApiKey` service-role fix. Until both are deployed to accordion.live the API 401s.
     */
    const val API_KEY = Settings.ACCORDION_API_KEY
}
