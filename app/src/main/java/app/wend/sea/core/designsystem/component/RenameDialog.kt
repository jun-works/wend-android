package app.wend.sea.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** 시안 대화상자의 글자 버튼: 종이색 위 대비 4.5:1 을 넘게 진한 코랄 */
private val DialogAction = Color(0xFF9A2A14)

/**
 * 시안 `#trip-name` · `#place-name`: 이름 바꾸기. 어느 여행·장소인지 한 줄([context]) 보여 주고,
 * 입력창 아래에 Wend 가 붙인 처음 이름을 남겨 되돌리기 쉽게 한다. 빈 이름은 저장하지 않는다.
 */
@Composable
fun RenameDialog(
    title: String,
    contextIcon: ImageVector,
    context: String,
    label: String,
    current: String,
    autoName: String,
    maxLength: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    var error by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(contextIcon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(context, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                TextField(
                    value = value,
                    onValueChange = {
                        if (it.text.length <= maxLength) {
                            value = it
                            error = false
                        }
                    },
                    label = { Text(label) },
                    supportingText = { Text(if (error) "이름을 입력해 주세요" else "처음 이름: $autoName") },
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = value.text.trim()
                    if (name.isEmpty()) error = true else onSave(name)
                },
                colors = ButtonDefaults.textButtonColors(contentColor = DialogAction),
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = DialogAction)) { Text("취소") }
        },
    )
}

/**
 * 시안 `#place-same`: 바꾸려는 장소와 지금 이름이 같은 장소가 더 있을 때 한 번 묻는다.
 * [where] 는 그 장소들의 "3일째 · 오후 3:10". 바깥을 누르거나 뒤로 가면 아무것도 바꾸지 않는다.
 */
@Composable
fun SameNameDialog(
    oldName: String,
    where: List<String>,
    onDismiss: () -> Unit,
    onOnlyThis: () -> Unit,
    onAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.EditLocationAlt, contentDescription = null) },
        title = { Text("같은 이름이 ${where.size}곳 더 있어요") },
        text = { Text("‘$oldName’ — ${where.joinToString(", ")}에 들른 곳도 같이 바꿀까요?") },
        confirmButton = {
            TextButton(onClick = onAll, colors = ButtonDefaults.textButtonColors(contentColor = DialogAction)) { Text("같이 바꾸기") }
        },
        dismissButton = {
            TextButton(onClick = onOnlyThis, colors = ButtonDefaults.textButtonColors(contentColor = DialogAction)) { Text("이 장소만") }
        },
    )
}

/** 시안 `.renamed`: 방금 바뀐 이름 뒤가 그 색으로 한 번 빛났다가 사라진다. [token] 이 바뀔 때마다, [active] 일 때만 */
@Composable
fun Modifier.renameGlow(active: Boolean, token: Long, color: Color): Modifier {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(token) {
        if (active && token > 0) {
            alpha.snapTo(.3f)
            alpha.animateTo(0f, tween(1600, easing = LinearOutSlowInEasing))
        }
    }
    return drawBehind {
        if (alpha.value > 0f) {
            val pad = 4.dp.toPx()
            drawRoundRect(
                color.copy(alpha = alpha.value),
                topLeft = Offset(-pad, -pad / 2),
                size = Size(size.width + pad * 2, size.height + pad),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
    }
}
