package app.wend.sea.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.wend.sea.core.designsystem.component.WendTopAppBar

private data class Notice(val title: String, val body: String)

/** 앱에 넣은 데이터·글꼴의 출처와 라이선스 */
private val Notices = listOf(
    Notice("도시 이름", "도시 데이터: GeoNames (geonames.org), CC BY 4.0\n이름 일부: Wikidata (wikidata.org), CC0"),
    Notice("기본 지도", "Natural Earth (naturalearthdata.com), 퍼블릭 도메인"),
    Notice("Gowun Batang", "SIL Open Font License 1.1\n줄 높이 값만 고쳐 Wend Batang 으로 이름을 바꿈"),
    Notice("IBM Plex Sans KR", "SIL Open Font License 1.1\n줄 높이 값만 고쳐 Wend Sans KR 로 이름을 바꿈"),
)

/** 오픈소스 라이선스. 설정 화면이 생기면 설정 › 정보 아래로 옮긴다 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        WendTopAppBar(onBack = onBack, title = { Text("오픈소스 라이선스") })
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
            items(Notices, key = { it.title }) { notice ->
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(notice.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        notice.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
