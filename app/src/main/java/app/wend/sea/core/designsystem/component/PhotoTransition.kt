package app.wend.sea.core.designsystem.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.wend.sea.data.MediaThumbnail
import coil3.request.ImageRequest

/** 시안 ㉑의 전환 곡선 cubic-bezier(.2,0,0,1) */
val PhotoEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)

/** 사진이 날아가는 시간(썸네일 ↔ 크게 보기) */
const val PhotoFlightMs = 400

/** 앱 전체를 감싸는 공유 요소 범위(WendNavHost) */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** 지금 화면의 네비게이션 전환 범위 */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** 사진 한 장을 가리키는 번호(크게 보기 넘김 키와 같다) */
val MediaThumbnail.key: Long get() = mediaId * 2 + if (video) 1 else 0

/** 썸네일 메모리 캐시 키 — 크게 보기가 큰 사진을 읽는 동안 같은 썸네일을 먼저 보여 준다 */
val MediaThumbnail.cacheKey: String get() = "thumb:$key"

/** 목록·그리드 썸네일 요청. 캐시 키를 고정해 크게 보기에서 자리표시로 다시 쓴다 */
@Composable
fun rememberThumbnail(photo: MediaThumbnail): ImageRequest {
    val context = LocalContext.current
    return remember(photo) { ImageRequest.Builder(context).data(photo).memoryCacheKey(photo.cacheKey).build() }
}

/**
 * 썸네일 ↔ 크게 보기에서 같이 움직이는 사진. [album] 은 장소 사진 화면에서 열었는지 —
 * 상세와 장소 사진 사이를 오갈 때 같은 사진끼리 날아가지 않게 키를 나눈다.
 */
data class PhotoKey(val media: Long, val album: Boolean)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPhoto(key: PhotoKey?): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedScope.current ?: return this
    if (key == null) return this
    return with(shared) {
        this@sharedPhoto.sharedElement(
            rememberSharedContentState(key),
            animatedVisibilityScope = scope,
            boundsTransform = { _, _ -> tween(PhotoFlightMs, easing = PhotoEasing) },
        )
    }
}
