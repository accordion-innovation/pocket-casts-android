package au.com.shiftyjelly.pocketcasts.servers.accordion

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the Accordion content API.
 *
 * Episodes are looked up by podcast + title because Pocket Casts does not store the RSS `<guid>`
 * that Accordion's `episode_hash` is derived from:
 *  - `podcastHash` path segment = md5(podcast RSS feed url)
 *  - `episode_title` query param = raw episode title; the server resolves the matching episode
 *
 * Authentication is via the `api_key` query parameter (see [AccordionConfig.API_KEY]).
 */
interface AccordionService {
    @GET("api_v1/content/{podcastHash}/by-title")
    suspend fun getEpisodeByTitle(
        @Path("podcastHash") podcastHash: String,
        @Query("episode_title") episodeTitle: String,
        @Query("api_key") apiKey: String,
    ): AccordionEpisodeResponse
}
