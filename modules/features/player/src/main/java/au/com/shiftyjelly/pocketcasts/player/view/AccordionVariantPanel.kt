package au.com.shiftyjelly.pocketcasts.player.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.compose.preview.ThemePreviewParameterProvider
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.player.viewmodel.AccordionVariantViewModel
import au.com.shiftyjelly.pocketcasts.player.viewmodel.AccordionVariantViewModel.UiState
import au.com.shiftyjelly.pocketcasts.servers.accordion.AccordionVariant
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import kotlin.math.roundToInt
import au.com.shiftyjelly.pocketcasts.localization.R as LR

/**
 * Panel shown in the playback effects sheet that lets the user switch between the available
 * Accordion audio variants of the current episode.
 *
 * Hidden unless the episode is streaming and has at least two variants — a downloaded episode plays
 * from its local file, so the player has no stream url to swap.
 */
@Composable
fun AccordionVariantPanel(
    viewModel: AccordionVariantViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccordionVariantContent(
        state = state,
        onVariantSelect = viewModel::onVariantSelected,
        modifier = modifier,
    )
}

@Composable
private fun AccordionVariantContent(
    state: UiState,
    onVariantSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is UiState.Hidden -> Unit

        is UiState.Loading -> {
            Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                PanelTitle()
                CircularProgressIndicator(
                    color = MaterialTheme.theme.colors.playerContrast02,
                    modifier = Modifier.padding(top = 12.dp).height(24.dp),
                )
            }
        }

        is UiState.Loaded -> {
            val variants = state.variants
            Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                PanelTitle()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    // Dot markers, one per variant, sitting above the slider track.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(horizontal = 10.dp), // approximates the Slider's thumb inset
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        variants.forEachIndexed { index, _ ->
                            val selected = index == state.selectedIndex
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 14.dp else 10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.theme.colors.playerContrast01
                                        } else {
                                            MaterialTheme.theme.colors.playerContrast04
                                        },
                                    )
                                    .clickable { onVariantSelect(index) },
                            )
                        }
                    }
                    Slider(
                        value = state.selectedIndex.toFloat(),
                        onValueChange = { value ->
                            onVariantSelect(value.roundToInt().coerceIn(0, variants.lastIndex))
                        },
                        valueRange = 0f..variants.lastIndex.toFloat(),
                        steps = (variants.size - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.theme.colors.playerContrast01,
                            activeTrackColor = MaterialTheme.theme.colors.playerContrast01,
                            inactiveTrackColor = MaterialTheme.theme.colors.playerContrast05,
                            // The dot markers above the track already mark each variant.
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = variantLabel(0, variants.first().durationSeconds),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.theme.colors.playerContrast02,
                    )
                    Text(
                        text = variantLabel(variants.lastIndex, variants.last().durationSeconds),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.theme.colors.playerContrast02,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelTitle(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(LR.string.player_effects_audio_version),
        style = MaterialTheme.typography.subtitle1,
        color = MaterialTheme.theme.colors.playerContrast01,
        modifier = modifier,
    )
}

private fun variantLabel(index: Int, durationSeconds: Long): String {
    if (durationSeconds <= 0L) return "V${index + 1}"
    val totalMinutes = (durationSeconds / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Preview(name = "Loaded")
@Composable
private fun AccordionVariantLoadedPreview(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) {
    AppThemeWithBackground(themeType) {
        AccordionVariantContent(
            state = UiState.Loaded(
                variants = listOf(
                    AccordionVariant(url = "https://cdn.accordion.live/short.mp3", durationSeconds = 5 * 60),
                    AccordionVariant(url = "https://cdn.accordion.live/medium.mp3", durationSeconds = 28 * 60),
                    AccordionVariant(url = "https://cdn.accordion.live/full.mp3", durationSeconds = 82 * 60),
                ),
                selectedIndex = 2,
            ),
            onVariantSelect = {},
        )
    }
}

/** Two variants is the minimum the panel ever shows, and puts the slider at its coarsest. */
@Preview(name = "Loaded - two variants")
@Composable
private fun AccordionVariantTwoVariantsPreview(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) {
    AppThemeWithBackground(themeType) {
        AccordionVariantContent(
            state = UiState.Loaded(
                variants = listOf(
                    AccordionVariant(url = "https://cdn.accordion.live/short.mp3", durationSeconds = 12 * 60),
                    AccordionVariant(url = "https://cdn.accordion.live/full.mp3", durationSeconds = 63 * 60),
                ),
                selectedIndex = 0,
            ),
            onVariantSelect = {},
        )
    }
}

/** Variants whose duration the API omitted, so the labels fall back to positional names. */
@Preview(name = "Loaded - unknown durations")
@Composable
private fun AccordionVariantUnknownDurationPreview(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) {
    AppThemeWithBackground(themeType) {
        AccordionVariantContent(
            state = UiState.Loaded(
                variants = listOf(
                    AccordionVariant(url = "https://cdn.accordion.live/a.mp3", durationSeconds = 0),
                    AccordionVariant(url = "https://cdn.accordion.live/b.mp3", durationSeconds = 0),
                ),
                selectedIndex = 0,
            ),
            onVariantSelect = {},
        )
    }
}

@Preview(name = "Loading")
@Composable
private fun AccordionVariantLoadingPreview(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) {
    AppThemeWithBackground(themeType) {
        AccordionVariantContent(
            state = UiState.Loading,
            onVariantSelect = {},
        )
    }
}
