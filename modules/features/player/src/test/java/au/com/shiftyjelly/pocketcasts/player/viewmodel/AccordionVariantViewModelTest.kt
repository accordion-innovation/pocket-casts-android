package au.com.shiftyjelly.pocketcasts.player.viewmodel

import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.models.entity.UserEpisode
import au.com.shiftyjelly.pocketcasts.models.type.EpisodeDownloadStatus
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.servers.accordion.AccordionAudioFile
import au.com.shiftyjelly.pocketcasts.servers.accordion.AccordionEpisodeData
import au.com.shiftyjelly.pocketcasts.servers.accordion.AccordionEpisodeResponse
import au.com.shiftyjelly.pocketcasts.servers.accordion.AccordionManager
import au.com.shiftyjelly.pocketcasts.servers.accordion.AccordionPodcast
import au.com.shiftyjelly.pocketcasts.servers.accordion.AccordionService
import au.com.shiftyjelly.pocketcasts.sharedtest.InMemoryFeatureFlagRule
import au.com.shiftyjelly.pocketcasts.sharedtest.MainCoroutineRule
import au.com.shiftyjelly.pocketcasts.utils.featureflag.Feature
import au.com.shiftyjelly.pocketcasts.utils.featureflag.FeatureFlag
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import retrofit2.Response

@ExperimentalCoroutinesApi
class AccordionVariantViewModelTest {
    @get:Rule
    val coroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    // Without a modifiable provider registered, FeatureFlag.setEnabled is silently a no-op and
    // every flag reads back as its build-type default.
    @get:Rule
    val featureFlagRule = InMemoryFeatureFlagRule()

    private val playbackManager = mock<PlaybackManager>()
    private val podcastManager = mock<PodcastManager>()

    @Before
    fun setUp() {
        FeatureFlag.setEnabled(Feature.ACCORDION_AUDIO_VARIANTS, true)
    }

    @Test
    fun `shows the panel with the longest variant preselected`() = runTest {
        val viewModel = viewModel(variantDurations = listOf(300.0, 4900.0, 1800.0))

        viewModel.loadVariantsForCurrentEpisode()

        val state = viewModel.uiState.value as AccordionVariantViewModel.UiState.Loaded
        assertEquals(3, state.variants.size)
        assertEquals(1, state.selectedIndex)
        assertEquals(4900L, state.variants[state.selectedIndex].durationSeconds)
    }

    @Test
    fun `showing the panel does not touch the player`() = runTest {
        val viewModel = viewModel(variantDurations = listOf(300.0, 1800.0))

        viewModel.loadVariantsForCurrentEpisode()

        // Opening the effects sheet must not reload ExoPlayer; only an explicit pick may do that.
        verifyBlocking(playbackManager, never()) { swapToVariantUrl(any(), any()) }
    }

    @Test
    fun `stays hidden when the feature flag is off`() = runTest {
        FeatureFlag.setEnabled(Feature.ACCORDION_AUDIO_VARIANTS, false)
        val viewModel = viewModel(variantDurations = listOf(300.0, 1800.0))

        viewModel.loadVariantsForCurrentEpisode()

        assertHidden(viewModel)
        // Nothing may be sent to accordion.live about what the user is listening to.
        verifyBlocking(podcastManager, never()) { findPodcastByUuid(any()) }
    }

    @Test
    fun `stays hidden when the episode is downloaded`() = runTest {
        val viewModel = viewModel(
            variantDurations = listOf(300.0, 1800.0),
            episode = episode(downloadStatus = EpisodeDownloadStatus.Downloaded),
        )

        viewModel.loadVariantsForCurrentEpisode()

        assertHidden(viewModel)
        verifyBlocking(podcastManager, never()) { findPodcastByUuid(any()) }
    }

    @Test
    fun `stays hidden when only one variant is available`() = runTest {
        val viewModel = viewModel(variantDurations = listOf(1800.0))

        viewModel.loadVariantsForCurrentEpisode()

        assertHidden(viewModel)
    }

    @Test
    fun `stays hidden when nothing is playing`() = runTest {
        val viewModel = viewModel(variantDurations = listOf(300.0, 1800.0), episode = null)

        viewModel.loadVariantsForCurrentEpisode()

        assertHidden(viewModel)
    }

    @Test
    fun `stays hidden for a user episode, which has no podcast to look up`() = runTest {
        val viewModel = viewModel(variantDurations = listOf(300.0, 1800.0))
        whenever(playbackManager.getCurrentEpisode()).thenReturn(UserEpisode(uuid = "user-uuid", publishedDate = Date()))

        viewModel.loadVariantsForCurrentEpisode()

        assertHidden(viewModel)
    }

    @Test
    fun `stays hidden when the podcast has no url to hash`() = runTest {
        val viewModel = viewModel(variantDurations = listOf(300.0, 1800.0), podcastUrl = "")

        viewModel.loadVariantsForCurrentEpisode()

        assertHidden(viewModel)
    }

    @Test
    fun `stays hidden when the api call fails`() = runTest {
        val viewModel = viewModel(
            service = FakeAccordionService(Response.error(500, "".toResponseBody())),
        )

        viewModel.loadVariantsForCurrentEpisode()

        assertHidden(viewModel)
    }

    @Test
    fun `selecting a variant swaps the player to its url`() = runTest {
        val viewModel = viewModel(variantDurations = listOf(300.0, 1800.0))
        whenever(playbackManager.swapToVariantUrl(any(), any())).thenReturn(true)
        viewModel.loadVariantsForCurrentEpisode()

        viewModel.onVariantSelected(0)

        val state = viewModel.uiState.value as AccordionVariantViewModel.UiState.Loaded
        assertEquals(0, state.selectedIndex)
        verifyBlocking(playbackManager) { swapToVariantUrl(VARIANT_URLS[0], 300L) }
    }

    @Test
    fun `reselecting the current variant does not reload the player`() = runTest {
        val viewModel = viewModel(variantDurations = listOf(300.0, 1800.0))
        viewModel.loadVariantsForCurrentEpisode()
        val selected = (viewModel.uiState.value as AccordionVariantViewModel.UiState.Loaded).selectedIndex

        viewModel.onVariantSelected(selected)

        verifyBlocking(playbackManager, never()) { swapToVariantUrl(any(), any()) }
    }

    @Test
    fun `hides instead of swapping when playback has moved to another episode`() = runTest {
        val viewModel = viewModel(variantDurations = listOf(300.0, 1800.0))
        viewModel.loadVariantsForCurrentEpisode()
        whenever(playbackManager.getCurrentEpisode()).thenReturn(episode(uuid = "a-different-episode"))

        viewModel.onVariantSelected(0)

        assertHidden(viewModel)
        // Applying the variant here would put one episode's audio onto another episode.
        verifyBlocking(playbackManager, never()) { swapToVariantUrl(any(), any()) }
    }

    @Test
    fun `hides when the player refuses the swap`() = runTest {
        val viewModel = viewModel(variantDurations = listOf(300.0, 1800.0))
        whenever(playbackManager.swapToVariantUrl(any(), any())).thenReturn(false)
        viewModel.loadVariantsForCurrentEpisode()

        viewModel.onVariantSelected(0)

        assertHidden(viewModel)
    }

    private fun assertHidden(viewModel: AccordionVariantViewModel) {
        assertTrue(
            "expected Hidden but was ${viewModel.uiState.value}",
            viewModel.uiState.value is AccordionVariantViewModel.UiState.Hidden,
        )
    }

    private suspend fun viewModel(
        variantDurations: List<Double> = listOf(300.0, 1800.0),
        episode: PodcastEpisode? = episode(),
        podcastUrl: String? = "https://example.com/show",
        service: AccordionService = FakeAccordionService(Response.success(responseWith(variantDurations))),
    ): AccordionVariantViewModel {
        whenever(playbackManager.getCurrentEpisode()).thenReturn(episode)
        whenever(podcastManager.findPodcastByUuid(PODCAST_UUID))
            .thenReturn(Podcast(uuid = PODCAST_UUID, podcastUrl = podcastUrl))
        return AccordionVariantViewModel(
            playbackManager = playbackManager,
            podcastManager = podcastManager,
            accordionManager = AccordionManager(service, apiKey = "test-api-key"),
        )
    }

    private fun episode(
        uuid: String = EPISODE_UUID,
        downloadStatus: EpisodeDownloadStatus = EpisodeDownloadStatus.DownloadNotRequested,
    ) = PodcastEpisode(
        uuid = uuid,
        podcastUuid = PODCAST_UUID,
        title = "Episode 1",
        publishedDate = Date(),
        downloadStatus = downloadStatus,
    )

    private fun responseWith(durations: List<Double>) = AccordionEpisodeResponse(
        podcast = AccordionPodcast(
            title = "Podcast",
            episode = AccordionEpisodeData(
                title = "Episode 1",
                audioFiles = durations.mapIndexed { index, duration ->
                    AccordionAudioFile(url = VARIANT_URLS[index], durationSeconds = duration)
                },
            ),
        ),
    )

    private class FakeAccordionService(
        private val response: Response<AccordionEpisodeResponse>,
    ) : AccordionService {
        override suspend fun getEpisodeByTitle(
            podcastHash: String,
            episodeTitle: String,
            apiKey: String,
        ) = response
    }

    companion object {
        private const val EPISODE_UUID = "episode-uuid"
        private const val PODCAST_UUID = "podcast-uuid"
        private val VARIANT_URLS = List(8) { "https://cdn.accordion.live/variant$it.mp3" }
    }
}
