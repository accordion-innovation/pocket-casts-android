package au.com.shiftyjelly.pocketcasts.servers.accordion

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the Accordion content API.
 *
 * Episodes are looked up by podcast + title because Pocket Casts does not store the RSS `<guid>`
 * that Accordion's `episode_hash` is derived from:
 *  - `podcastHash` path segment = md5(`Podcast.podcastUrl`), the podcast's website link as the
 *    feed spells it in `<channel><link>`, hashed verbatim - Accordion hashes the same string
 *  - `episode_title` query param = raw episode title; the server resolves the matching episode
 *
 * Authentication is via the `X-Api-Key` header (see [AccordionConfig.API_KEY]). It is deliberately
 * not a query parameter: query strings are recorded verbatim by access logs, proxies and the debug
 * build's OkHttp logging interceptor, which would spread the key well beyond the request.
 */
interface AccordionService {
    @GET("api_v1/content/{podcastHash}/by-title")
    suspend fun getEpisodeByTitle(
        @Path("podcastHash") podcastHash: String,
        @Query("episode_title") episodeTitle: String,
        @Header("X-Api-Key") apiKey: String,
    ): Response<AccordionEpisodeResponse>
}
