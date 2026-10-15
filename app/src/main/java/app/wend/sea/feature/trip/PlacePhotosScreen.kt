package app.wend.sea.feature.trip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wend.sea.core.designsystem.component.PhotoEasing
import app.wend.sea.core.designsystem.component.PhotoKey
import app.wend.sea.core.designsystem.component.WendTopAppBar
import app.wend.sea.core.designsystem.component.key
import app.wend.sea.core.designsystem.component.rememberThumbnail
import app.wend.sea.core.designsystem.component.sharedPhoto
import app.wend.sea.core.designsystem.theme.CssLineHeight
import app.wend.sea.core.designsystem.theme.GowunBatang
import app.wend.sea.core.designsystem.theme.IbmPlexSansKr
import app.wend.sea.feature.analysis.formatCount
import coil3.compose.AsyncImage
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale

private fun sans(weight: FontWeight, size: Int, line: Double) = TextStyle(
    fontFamily = IbmPlexSansKr, fontWeight = weight, fontSize = size.sp, lineHeight = (size * line).sp, lineHeightStyle = CssLineHeight,
)
private val BarTitleStyle = sans(FontWeight.SemiBold, 17, 1.4)
private val RouteStyle = sans(FontWeight.SemiBold, 12, 1.5)
private val StopStyle = sans(FontWeight.SemiBold, 12, 1.0)
private val CountStyle = sans(FontWeight.SemiBold, 14, 1.5)
private val NameStyle = TextStyle(fontFamily = GowunBatang, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 39.2.sp, lineHeightStyle = CssLineHeight)

/** 첫 네 줄(12장)만 30ms 씩 차례로 자리 잡는다 */
private const val IntroTiles = 12
private const val IntroMs = 90 + 3 * 30 + 240

/**
 * 시안 ㉑ 장소 전체 사진: 종이색 바탕, 세리프 장소명, 상세와 같은 방문 번호·날짜 색.
 * 촬영 시간과 전체 장수 아래 촬영순 3열 그리드(간격 4dp, 모서리 5dp). 사진을 누르면 크게 보기로 자란다.
 */
@Composable
fun PlacePhotosScreen(
    onBack: () -> Unit,
    onPhotoClick: (place: Int, index: Int) -> Unit,
    /** 사진을 읽고 그리드를 처음 그렸다 — 앨범을 펼치기 시작한다 */
    onReady: () -> Unit = {},
    viewModel: PlacePhotosViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lastViewed by viewModel.lastViewed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

    // 크게 보기에서 넘긴 사진이 화면 밖이면 그 사진이 가운데 오게(그리드 앞 1칸은 머리)
    LaunchedEffect(lastViewed, uiState.photos) {
        val key = lastViewed ?: return@LaunchedEffect
        if (!uiState.loaded) return@LaunchedEffect
        val index = uiState.photos.indexOfFirst { it.media.key == key }
        viewModel.consumeLastViewed()
        if (index < 0) return@LaunchedEffect
        val item = index + 1
        val layout = gridState.layoutInfo
        val info = layout.visibleItemsInfo.firstOrNull { it.index == item }
        val inside = info != null && info.offset.y >= layout.viewportStartOffset && info.offset.y + info.size.height <= layout.viewportEndOffset
        if (!inside) {
            val tile = layout.visibleItemsInfo.firstOrNull { it.index > 0 }?.size?.height ?: 0
            gridState.scrollToItem(item, -(layout.viewportSize.height - tile) / 2)
        }
    }

    // 새로 열 때만 타일이 차례로 나타난다(크게 보기에서 돌아올 때는 아님)
    var introDone by rememberSaveable { mutableStateOf(false) }
    val intro = remember { Animatable(if (introDone) IntroMs.toFloat() else 0f) }
    val currentOnReady by rememberUpdatedState(onReady)
    LaunchedEffect(uiState.loaded) {
        if (!uiState.loaded) return@LaunchedEffect
        currentOnReady()
        if (introDone) return@LaunchedEffect
        intro.animateTo(IntroMs.toFloat(), tween(IntroMs, easing = LinearEasing))
        introDone = true
    }

    Box(Modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize()) {
            WendTopAppBar(
                onBack = onBack,
                title = { Text("장소 사진", style = BarTitleStyle) },
                actions = {
                    Text(
                        uiState.tripName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 112.dp).padding(end = 8.dp),
                    )
                },
            )
            HorizontalDivider(color = colors.outlineVariant)
            if (!uiState.loaded) return@Column
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "head", span = { GridItemSpan(maxLineSpan) }) { Heading(uiState) }
                itemsIndexed(uiState.photos, key = { _, it -> it.media.key }) { index, photo ->
                    val isBest = !photo.media.video && photo.media.mediaId in uiState.best
                    val delay = 90f + (index / 3) * 30f
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .graphicsLayer {
                                if (index >= IntroTiles) return@graphicsLayer
                                val t = PhotoEasing.transform(((intro.value - delay) / 240f).coerceIn(0f, 1f))
                                alpha = t
                                translationY = (1f - t) * 16.dp.toPx()
                                scaleX = .96f + .04f * t
                                scaleY = scaleX
                            }
                            .clip(RoundedCornerShape(5.dp))
                            .background(colors.surfaceContainer)
                            .semantics {
                                contentDescription = "${uiState.name}, 사진 ${index + 1}" + (photo.takenAt?.let { ", ${time(it)}" } ?: "") +
                                    (if (isBest) ", 베스트샷" else "") + ", 크게 보기"
                            }
                            .clickable(role = Role.Button) { onPhotoClick(uiState.place, index) },
                    ) {
                        AsyncImage(
                            model = rememberThumbnail(photo.media),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().sharedPhoto(PhotoKey(photo.media.key, album = true)).clip(RoundedCornerShape(5.dp)),
                        )
                        if (isBest) PlaceStar(Modifier.padding(6.dp))
                    }
                }
                item(key = "end", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (uiState.photos.isEmpty()) "이 장소에 남아 있는 사진이 없어요" else "이곳에서 담은 순간을 모두 펼쳤어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).navigationBarsPadding(),
                    )
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** 시안 `.pg-star`: 사진 왼쪽 위 원 + 별 */
@Composable
private fun PlaceStar(modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFF7A4F05), modifier = Modifier.size(16.dp))
    }
}

/** 시안 `.pg-heading`: 방문 번호 · N일째 · 날짜 / 장소명 / 시간대 / "사진 N장 · 촬영 순서대로" */
@Composable
private fun Heading(state: PlacePhotosUiState) {
    val colors = MaterialTheme.colorScheme
    val dayInk = dayTextColor(state.day)
    // 그리드 좌우 16dp 안에서 시안의 20dp 가 되게 4dp 더, 아래 12dp 는 그리드 간격 4dp 를 뺀 8dp
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(24.dp).background(dayInk, CircleShape), contentAlignment = Alignment.Center) {
                Text("${state.place + 1}", style = StopStyle, color = Color.White)
            }
            val date = state.date
            val label = if (date == null) "${state.dayOrdinal}일째" else {
                val weekday = date.dayOfWeek.getDisplayName(DayTextStyle.FULL, Locale.KOREAN)
                "${state.dayOrdinal}일째 · ${date.monthValue}월 ${date.dayOfMonth}일 $weekday"
            }
            Text(label, style = RouteStyle, color = dayInk)
        }
        Text(state.name, style = NameStyle, color = colors.onSurface, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Text(whenText(state.start, state.end), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("사진 ${formatCount(state.photos.size)}장", style = CountStyle, color = colors.onSurface)
            Text("촬영 순서대로", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}
