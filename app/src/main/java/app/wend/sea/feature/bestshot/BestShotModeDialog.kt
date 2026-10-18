package app.wend.sea.feature.bestshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.wend.sea.R
import app.wend.sea.data.BestShotSaveMode

/** 최초 확정과 설정 화면에서 공유한다. 큰 글꼴에서도 전체 내용과 버튼을 스크롤할 수 있다. */
@Composable
fun BestShotModeDialog(
    current: BestShotSaveMode,
    onSelect: (BestShotSaveMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp).widthIn(max = 360.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(shape = CircleShape, color = colors.secondaryContainer) {
                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(24.dp), tint = colors.onSecondaryContainer)
                }
                Text(stringResource(R.string.best_save_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(stringResource(R.string.best_save_description), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onSelect(BestShotSaveMode.GALLERY) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = current == BestShotSaveMode.GALLERY },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) { ModeLabel(R.string.best_save_gallery, current == BestShotSaveMode.GALLERY) }
                    FilledTonalButton(
                        onClick = { onSelect(BestShotSaveMode.APP_ONLY) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = current == BestShotSaveMode.APP_ONLY },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) { ModeLabel(R.string.best_save_app_only, current == BestShotSaveMode.APP_ONLY) }
                }
                Text(stringResource(R.string.best_save_settings_note), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerHighest) {
                    Text(stringResource(R.string.best_save_retain), modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ModeLabel(label: Int, isSelected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), textAlign = TextAlign.Center)
        if (isSelected) Text(stringResource(R.string.best_save_current), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}
