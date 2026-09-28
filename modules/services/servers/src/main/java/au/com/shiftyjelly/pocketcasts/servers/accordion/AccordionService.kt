package au.com.shiftyjelly.pocketcasts.servers.accordion

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the Accordion content API.
 *
 * Episodes are looked up by podcast + title rather than by Accordion's `episode_hash`, and the key
 * travels in a header rather than the query string. See `docs/accordion.md` for why, and for what
 * [podcastHash] is actually hashing.
 */
interface AccordionService {
    @GET("api_v1/content/{podcastHash}/by-title")
    suspend fun getEpisodeByTitle(
        @Path("podcastHash") podcastHash: String,
        @Query("episode_title") episodeTitle: String,
        @Header("X-Api-Key") apiKey: String,
    ): Response<AccordionEpisodeResponse>
}
