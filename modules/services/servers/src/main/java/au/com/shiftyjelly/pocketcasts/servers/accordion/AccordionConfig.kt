package au.com.shiftyjelly.pocketcasts.servers.accordion

import au.com.shiftyjelly.pocketcasts.preferences.Settings

/** Configuration for the Accordion content API. See `docs/accordion.md`. */
object AccordionConfig {
    /** Base URL of the Accordion API. Must end with a trailing slash for Retrofit. */
    const val BASE_URL = "https://www.accordion.live/"

    /**
     * Developer API key sent as the `X-Api-Key` header, resolved at build time from the
     * `ACCORDION_API_KEY` env var, then `accordionApiKey` in `secret.properties`, then empty.
     *
     * This is compiled into the APK and so is extractable by anyone holding the app. Treat it as a
     * public client identifier for attribution and rate limiting, never as an authorization secret.
     */
    const val API_KEY = Settings.ACCORDION_API_KEY
}
