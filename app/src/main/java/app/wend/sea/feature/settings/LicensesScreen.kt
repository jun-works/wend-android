package app.wend.sea.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wend.sea.R
import app.wend.sea.core.designsystem.component.WendTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 오픈소스 라이선스(설정 › 정보). 기본 갤러리 앱처럼 쓴 라이선스 목록과 전문을 그대로 보여 준다 —
 * 내용은 assets/open_source_licenses.txt.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 문단 단위로 나눠 보이는 만큼만 그린다
    val paragraphs by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) {
            context.assets.open("open_source_licenses.txt").bufferedReader().use { it.readText() }.split("\n\n")
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        WendTopAppBar(onBack = onBack, title = { Text(stringResource(R.string.open_source_licenses)) })
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            items(paragraphs) { paragraph ->
                Text(paragraph, style = LicenseStyle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 14.dp))
            }
        }
    }
}

private val LicenseStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
