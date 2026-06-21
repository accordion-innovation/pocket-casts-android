package au.com.shiftyjelly.pocketcasts.player.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.player.viewmodel.AccordionVariantViewModel
import au.com.shiftyjelly.pocketcasts.player.viewmodel.AccordionVariantViewModel.UiState
import kotlin.math.roundToInt
import au.com.shiftyjelly.pocketcasts.localization.R as LR

/**
 * Panel shown in the playback effects sheet that lets the user switch between the available
 * Accordion audio variants of the current episode. Hidden unless there are at least two variants.
 */
@Composable
fun AccordionVariantPanel(
    viewModel: AccordionVariantViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccordionVariantContent(
        state = state,
        onVariantSelected = viewModel::onVariantSelected,
        modifier = modifier,
    )
}

@Composable
private fun AccordionVariantContent(
    state: UiState,
    onVariantSelected: (Int) -> Unit,
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
                Slider(
                    value = state.selectedIndex.toFloat(),
                    onValueChange = { value ->
                        onVariantSelected(value.roundToInt().coerceIn(0, variants.lastIndex))
                    },
                    valueRange = 0f..variants.lastIndex.toFloat(),
                    steps = (variants.size - 2).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.theme.colors.playerContrast01,
                        activeTrackColor = MaterialTheme.theme.colors.playerContrast01,
                        inactiveTrackColor = MaterialTheme.theme.colors.playerContrast05,
                        activeTickColor = MaterialTheme.theme.colors.playerContrast02,
                        inactiveTickColor = MaterialTheme.theme.colors.playerContrast05,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    variants.forEachIndexed { index, variant ->
                        val selected = index == state.selectedIndex
                        Text(
                            text = variantLabel(index, variant.durationSeconds),
                            style = MaterialTheme.typography.caption,
                            textAlign = TextAlign.Center,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.theme.colors.playerContrast01
                            } else {
                                MaterialTheme.theme.colors.playerContrast02
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
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
