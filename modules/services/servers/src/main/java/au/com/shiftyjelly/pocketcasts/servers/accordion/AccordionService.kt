package au.com.shiftyjelly.pocketcasts.servers.accordion

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the Accordion content API.
 *
 * Authentication is via the `api_key` query parameter (see [AccordionConfig.API_KEY]); without a
 * valid key the API responds with 401.
 */
interface AccordionService {
    @GET("accordion/episode")
    suspend fun getEpisode(
        @Query("podcast_hash") podcastHash: String,
        @Query("episode_hash") episodeHash: String,
        @Query("api_key") apiKey: String,
    ): AccordionEpisodeResponse
}
