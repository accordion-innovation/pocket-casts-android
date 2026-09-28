package au.com.shiftyjelly.pocketcasts.player.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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

private val PanelPadding = 16.dp

/** Approximates the Slider's thumb inset so the dot markers line up with its stops. */
private val DotRowInset = 10.dp

/**
 * Panel in the playback effects sheet for switching between the Accordion audio variants of the
 * current episode. See `docs/accordion.md`.
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
        UiState.Hidden -> Unit

        UiState.Loading -> PanelColumn(modifier) {
            CircularProgressIndicator(
                color = MaterialTheme.theme.colors.playerContrast02,
                modifier = Modifier.padding(top = 12.dp).height(24.dp),
            )
        }

        is UiState.Loaded -> PanelColumn(modifier) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = PanelPadding)) {
                VariantDots(
                    variants = state.variants,
                    selectedIndex = state.selectedIndex,
                    onVariantSelect = onVariantSelect,
                )
                VariantSlider(
                    variantCount = state.variants.size,
                    selectedIndex = state.selectedIndex,
                    onVariantSelect = onVariantSelect,
                )
            }
            EndLabels(state.variants)
        }
    }
}

@Composable
private fun PanelColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = PanelPadding)) {
        Text(
            text = stringResource(LR.string.player_effects_audio_version),
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.theme.colors.playerContrast01,
        )
        content()
    }
}

@Composable
private fun BoxScope.VariantDots(
    variants: List<AccordionVariant>,
    selectedIndex: Int,
    onVariantSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.Center)
            .padding(horizontal = DotRowInset),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        variants.indices.forEach { index ->
            val selected = index == selectedIndex
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
}

@Composable
private fun BoxScope.VariantSlider(
    variantCount: Int,
    selectedIndex: Int,
    onVariantSelect: (Int) -> Unit,
) {
    val lastIndex = variantCount - 1
    Slider(
        value = selectedIndex.toFloat(),
        onValueChange = { value -> onVariantSelect(value.roundToInt().coerceIn(0, lastIndex)) },
        valueRange = 0f..lastIndex.toFloat(),
        steps = (variantCount - 2).coerceAtLeast(0),
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

@Composable
private fun EndLabels(variants: List<AccordionVariant>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf(0, variants.lastIndex).forEach { index ->
            Text(
                text = variantLabel(index, variants[index].durationSeconds),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.theme.colors.playerContrast02,
            )
        }
    }
}

private fun variantLabel(index: Int, durationSeconds: Long): String {
    if (durationSeconds <= 0L) return "V${index + 1}"
    val totalMinutes = (durationSeconds / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun previewVariants(vararg durationMinutes: Int) = durationMinutes.mapIndexed { index, minutes ->
    AccordionVariant(url = "https://cdn.accordion.live/variant$index.mp3", durationSeconds = minutes * 60L)
}

@Preview(name = "Loaded")
@Composable
private fun AccordionVariantLoadedPreview(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) = AccordionVariantPreview(themeType, UiState.Loaded(previewVariants(5, 28, 82), selectedIndex = 2))

/** Two variants is the fewest the panel ever shows, and puts the slider at its coarsest. */
@Preview(name = "Loaded - two variants")
@Composable
private fun AccordionVariantTwoVariantsPreview(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) = AccordionVariantPreview(themeType, UiState.Loaded(previewVariants(12, 63), selectedIndex = 0))

/** Durations the API omitted, so the labels fall back to positional names. */
@Preview(name = "Loaded - unknown durations")
@Composable
private fun AccordionVariantUnknownDurationPreview(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) = AccordionVariantPreview(themeType, UiState.Loaded(previewVariants(0, 0), selectedIndex = 0))

@Preview(name = "Loading")
@Composable
private fun AccordionVariantLoadingPreview(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) = AccordionVariantPreview(themeType, UiState.Loading)

@Composable
private fun AccordionVariantPreview(themeType: Theme.ThemeType, state: UiState) {
    AppThemeWithBackground(themeType) {
        AccordionVariantContent(state = state, onVariantSelect = {})
    }
}
