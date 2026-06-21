package au.com.shiftyjelly.pocketcasts.servers.accordion

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the Accordion content API.
 *
 * Episodes are looked up by podcast + title (feed+title lookup), because Pocket Casts does not
 * store the RSS `<guid>` that Accordion's `episode_hash` is derived from:
 *  - `podcast_hash` = md5(podcast RSS feed url) — the same hash Accordion already keys podcasts by.
 *  - `episode_title` = the raw episode title; the server resolves the matching episode + its audio
 *    variants within that podcast.
 *
 * Authentication is via the `api_key` query parameter (see [AccordionConfig.API_KEY]); without a
 * valid key the API responds with 401.
 *
 * NOTE: this endpoint must be implemented server-side to accept these parameters. The existing
 * `/api_v1/content/{podcastHash}/{episodeHash}` route keys on md5(guid), which the app cannot
 * reproduce.
 */
interface AccordionService {
    @GET("accordion/episode")
    suspend fun getEpisode(
        @Query("podcast_hash") podcastHash: String,
        @Query("episode_title") episodeTitle: String,
        @Query("api_key") apiKey: String,
    ): AccordionEpisodeResponse
}
