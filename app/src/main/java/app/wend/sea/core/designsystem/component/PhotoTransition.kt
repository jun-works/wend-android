package app.wend.sea.core.designsystem.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.wend.sea.data.MediaThumbnail
import coil3.request.ImageRequest

/** 시안 ㉑의 앨범 펼치기 곡선 cubic-bezier(.2,0,0,1) */
val PhotoEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)

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

