package app.wend.sea.feature.analysis

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.wend.sea.R

/**
 * 시안 ⑲ `#prefs-rescan`: 처음부터 다시 찾기 확인. 여행 찾기는 초기화라(넣기·빼기·새 여행·베스트샷·이름이 지워짐)
 * 설정에서 누를 때와 사진을 모두 허용해 다시 찾을 때 모두 이 창을 거친다.
 */
@Composable
fun ReanalyzeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.TravelExplore, contentDescription = null) },
        title = { Text(stringResource(R.string.reanalyze_title)) },
        text = { Text(stringResource(R.string.reanalyze_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = DialogAction)) { Text(stringResource(R.string.reanalyze_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = DialogAction)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 시안 대화상자 글자 버튼: 종이색 위 대비 4.5:1 을 넘는 진한 코랄 */
private val DialogAction = Color(0xFF9A2A14)
