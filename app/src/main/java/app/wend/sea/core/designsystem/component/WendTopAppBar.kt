package app.wend.sea.core.designsystem.component

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.wend.sea.R

/**
 * 시안 `.top-app-bar`: 높이 64, 좌우 4, 뒤로 버튼 48, 제목은 버튼 뒤 4dp 에서 시작(title-l),
 * 배경은 화면과 같은 종이색.
 */
@Composable
fun WendTopAppBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    /** 선택 모드 등에서 ✕ 로 바꿀 때 */
    backIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    backLabel: String = stringResource(R.string.action_back),
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(backIcon, contentDescription = backLabel, tint = colors.onSurfaceVariant)
            }
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = title,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}
