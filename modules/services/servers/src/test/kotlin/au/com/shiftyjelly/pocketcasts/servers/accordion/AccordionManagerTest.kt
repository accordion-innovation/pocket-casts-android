package au.com.shiftyjelly.pocketcasts.servers.accordion

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue(AccordionManager(FakeService(AccordionEpisodeResponse())).getVariants(HASH, TITLE).isEmpty())
    }

    private suspend fun variantsFor(vararg audioFiles: AccordionAudioFile): List<AccordionVariant> {
        val response = AccordionEpisodeResponse(
            podcast = AccordionPodcast(
                title = "Podcast",
                episode = AccordionEpisodeData(title = TITLE, audioFiles = audioFiles.toList()),
            ),
        )
        return AccordionManager(FakeService(response)).getVariants(HASH, TITLE)
    }

    private fun audioFile(url: String?, durationSeconds: Double?) = AccordionAudioFile(url, durationSeconds)

    private class FakeService(private val response: AccordionEpisodeResponse) : AccordionService {
        override suspend fun getEpisodeByTitle(
            podcastHash: String,
            episodeTitle: String,
            apiKey: String,
        ) = response
    }

    companion object {
        private const val HASH = "9e107d9d372bb6826bd81d3542a419d6"
        private const val TITLE = "Episode 1"
    }
}
