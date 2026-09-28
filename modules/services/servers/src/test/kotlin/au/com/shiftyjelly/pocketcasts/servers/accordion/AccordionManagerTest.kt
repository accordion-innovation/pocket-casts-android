package au.com.shiftyjelly.pocketcasts.servers.accordion

import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AccordionManagerTest {
    @Test
    fun `keeps https variants`() = runTest {
        val variants = variantsFor(
            audioFile("https://cdn.accordion.live/a.mp3", 600.0),
            audioFile("https://cdn.accordion.live/b.mp3", 1200.0),
        )

        assertEquals(
            listOf(
                AccordionVariant("https://cdn.accordion.live/a.mp3", 600L),
                AccordionVariant("https://cdn.accordion.live/b.mp3", 1200L),
            ),
            variants,
        )
    }

    @Test
    fun `drops variants that are not https`() = runTest {
        val variants = variantsFor(
            audioFile("http://cdn.accordion.live/cleartext.mp3", 600.0),
            audioFile("file:///data/data/au.com.shiftyjelly.pocketcasts/databases/pocketcasts.db", 600.0),
            audioFile("content://media/external/audio/media/1", 600.0),
            audioFile("asset:///secret.mp3", 600.0),
            audioFile("not a url at all", 600.0),
            audioFile(null, 600.0),
            audioFile("", 600.0),
        )

        assertTrue(variants.isEmpty())
    }

    @Test
    fun `treats implausible durations as unknown`() = runTest {
        val variants = variantsFor(
            audioFile("https://cdn.accordion.live/nan.mp3", Double.NaN),
            audioFile("https://cdn.accordion.live/infinite.mp3", Double.POSITIVE_INFINITY),
            audioFile("https://cdn.accordion.live/negative.mp3", -600.0),
            audioFile("https://cdn.accordion.live/zero.mp3", 0.0),
            audioFile("https://cdn.accordion.live/absurd.mp3", 25.0 * 60 * 60),
            audioFile("https://cdn.accordion.live/missing.mp3", null),
        )

        assertEquals(6, variants.size)
        assertTrue(variants.all { it.durationSeconds == 0L })
    }

    @Test
    fun `caps the number of variants`() = runTest {
        val variants = variantsFor(
            *Array(30) { index -> audioFile("https://cdn.accordion.live/$index.mp3", 600.0) },
        )

        assertEquals(8, variants.size)
    }

    @Test
    fun `returns nothing when the response has no audio files`() = runTest {
        assertTrue(variantsFor().isEmpty())
        assertTrue(managerFor(AccordionEpisodeResponse()).getVariants(HASH, TITLE).isEmpty())
    }

    @Test
    fun `fails with a message naming the missing api key`() = runTest {
        val manager = AccordionManager(FakeService(Response.success(AccordionEpisodeResponse())), apiKey = "")

        val error = runCatching { manager.getVariants(HASH, TITLE) }.exceptionOrNull()

        assertTrue(error is AccordionException)
        assertTrue(error!!.message!!.contains("ACCORDION_API_KEY"))
    }

    @Test
    fun `reports an auth redirect rather than a parse error`() = runTest {
        // accordion.live answers an unauthenticated content request with a 303 to its HTML login
        // page. The failure has to name that, not surface as malformed JSON.
        val redirect = Response.error<AccordionEpisodeResponse>(
            "".toResponseBody(null),
            okhttp3.Response.Builder()
                .code(303)
                .message("See Other")
                .header("Location", "/direct-login")
                .protocol(Protocol.HTTP_1_1)
                .request(Request.Builder().url("https://www.accordion.live/api_v1/content/$HASH/by-title").build())
                .build(),
        )

        val error = runCatching { AccordionManager(FakeService(redirect), API_KEY).getVariants(HASH, TITLE) }
            .exceptionOrNull()

        assertTrue(error is AccordionException)
        assertTrue(error!!.message!!.contains("/direct-login"))
    }

    @Test
    fun `reports a rejected api key`() = runTest {
        val unauthorized = Response.error<AccordionEpisodeResponse>(401, "".toResponseBody(null))

        val error = runCatching { AccordionManager(FakeService(unauthorized), API_KEY).getVariants(HASH, TITLE) }
            .exceptionOrNull()

        assertTrue(error is AccordionException)
        assertTrue(error!!.message!!.contains("rejected the API key"))
    }

    @Test
    fun `reports a missing route rather than an empty result`() = runTest {
        val notFound = Response.error<AccordionEpisodeResponse>(404, "".toResponseBody(null))

        val error = runCatching { AccordionManager(FakeService(notFound), API_KEY).getVariants(HASH, TITLE) }
            .exceptionOrNull()

        assertTrue(error is AccordionException)
        assertTrue(error!!.message!!.contains("by-title"))
    }

    @Test
    fun `reports an empty body`() = runTest {
        val emptyBody = Response.success<AccordionEpisodeResponse>(null)

        val error = runCatching { AccordionManager(FakeService(emptyBody), API_KEY).getVariants(HASH, TITLE) }
            .exceptionOrNull()

        assertTrue(error is AccordionException)
        assertTrue(error!!.message!!.contains("empty body"))
    }

    @Test
    fun `keeps the order the api returned so the slider matches the server's ordering`() = runTest {
        val variants = variantsFor(
            audioFile("https://cdn.accordion.live/long.mp3", 3600.0),
            audioFile("https://cdn.accordion.live/short.mp3", 300.0),
            audioFile("https://cdn.accordion.live/medium.mp3", 1800.0),
        )

        assertEquals(listOf(3600L, 300L, 1800L), variants.map { it.durationSeconds })
    }

    @Test
    fun `drops unplayable variants without dropping the playable ones alongside them`() = runTest {
        val variants = variantsFor(
            audioFile("https://cdn.accordion.live/good.mp3", 600.0),
            audioFile("http://cdn.accordion.live/cleartext.mp3", 600.0),
            audioFile("https://cdn.accordion.live/also-good.mp3", 1200.0),
        )

        assertEquals(
            listOf("https://cdn.accordion.live/good.mp3", "https://cdn.accordion.live/also-good.mp3"),
            variants.map { it.url },
        )
    }

    private suspend fun variantsFor(vararg audioFiles: AccordionAudioFile): List<AccordionVariant> {
        val response = AccordionEpisodeResponse(
            podcast = AccordionPodcast(
                title = "Podcast",
                episode = AccordionEpisodeData(title = TITLE, audioFiles = audioFiles.toList()),
            ),
        )
        return managerFor(response).getVariants(HASH, TITLE)
    }

    private fun managerFor(response: AccordionEpisodeResponse) = AccordionManager(FakeService(Response.success(response)), API_KEY)

    private fun audioFile(url: String?, durationSeconds: Double?) = AccordionAudioFile(url, durationSeconds)

    private class FakeService(private val response: Response<AccordionEpisodeResponse>) : AccordionService {
        override suspend fun getEpisodeByTitle(
            podcastHash: String,
            episodeTitle: String,
            apiKey: String,
        ) = response
    }

    companion object {
        private const val HASH = "9e107d9d372bb6826bd81d3542a419d6"
        private const val TITLE = "Episode 1"
        private const val API_KEY = "test-api-key"
    }
}
