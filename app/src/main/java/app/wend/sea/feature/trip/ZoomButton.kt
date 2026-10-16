package app.wend.sea.feature.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 시안 `.zoom`: 베스트샷 고르기·사진 정리하기 사진 칸 왼쪽 아래의 크게 보기 버튼. 칸의 나머지는 고르기라 따로 둔다.
 * 보이는 원은 26dp, 누르는 곳은 40dp.
 */
@Composable
internal fun ZoomButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(26.dp).background(ZoomInk, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.OpenInFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** 시안 `rgba(31,26,22,.55)` */
private val ZoomInk = Color(0x8C1F1A16)
