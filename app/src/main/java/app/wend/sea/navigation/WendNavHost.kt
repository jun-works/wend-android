package app.wend.sea.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.navigation.NavDestination
import androidx.navigation.NavBackStackEntry
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberUpdatedState
import app.wend.sea.core.designsystem.component.AlbumUnfold
import app.wend.sea.core.designsystem.component.PhotoEasing
import app.wend.sea.feature.left.LeftScreen
import app.wend.sea.feature.left.LeftViewModel
import app.wend.sea.feature.trip.BestShotScreen
import app.wend.sea.feature.trip.CleanUpScreen
import app.wend.sea.feature.trip.PlacePhotosScreen
import app.wend.sea.feature.trip.PlacePhotosViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import app.wend.sea.core.designsystem.component.TripReveal
import app.wend.sea.core.designsystem.component.tripColor
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.wend.sea.data.MediaAccess
import app.wend.sea.feature.analysis.AnalyzeScreen
import app.wend.sea.feature.analysis.FoundScreen
import app.wend.sea.feature.analysis.NoFoundScreen
import app.wend.sea.feature.globe.GlobeScreen
import app.wend.sea.feature.guide.GuideScreen
import app.wend.sea.feature.nickname.NicknameScreen
import app.wend.sea.feature.permission.DeniedScreen
import app.wend.sea.feature.permission.GrantedScreen
import app.wend.sea.feature.permission.PartialScreen
import app.wend.sea.feature.permission.PermScreen
import app.wend.sea.feature.splash.SplashDestination
import app.wend.sea.feature.splash.SplashScreen
import app.wend.sea.feature.settings.LicensesScreen
import app.wend.sea.feature.settings.SettingsScreen
import app.wend.sea.feature.trip.TripDetailScreen
import app.wend.sea.feature.trip.TripDetailViewModel
import app.wend.sea.feature.viewer.ViewerArgs
import app.wend.sea.feature.viewer.ViewerScreen
import app.wend.sea.feature.viewer.ViewerViewModel
import app.wend.sea.feature.trips.TripsScreen
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable data object SplashRoute
@Serializable data object NicknameRoute
@Serializable data object GlobeRoute
@Serializable data object GuideRoute
@Serializable data object PermRoute
@Serializable data object GrantedRoute
@Serializable data object PartialRoute
@Serializable data object DeniedRoute
@Serializable data object AnalyzeRoute
@Serializable data object FoundRoute
@Serializable data object NoFoundRoute
@Serializable data object TripsRoute
/** [x]·[y] 는 상세가 퍼져 나올 카드 방울 중심(화면 좌표), 없으면 -1 */
@Serializable data class TripRoute(val number: Int, val x: Float = -1f, val y: Float = -1f)
/** [album] 이면 장소 사진 화면에서 열었다 */
/** 시안 ⑰ 빠진 사진 */
@Serializable data object LeftRoute
/** [x]·[y]·[w]·[h] 는 앨범이 펼쳐질 `+N / 전체 보기` 타일 자리(화면 좌표), 없으면 -1 */
@Serializable data class PlacePhotosRoute(val trip: Int, val place: Int, val x: Float = -1f, val y: Float = -1f, val w: Float = -1f, val h: Float = -1f)
@Serializable data object LicensesRoute
@Serializable data object SettingsRoute
@Serializable data class BestShotRoute(val trip: Int)
@Serializable data class CleanUpRoute(val trip: Int)

/** 상세 위에 겹쳐 열리는 화면(상세는 아래에 그대로 남고, 돌아와도 다시 퍼지지 않는다) */
private fun NavDestination.overTrip() = hasRoute<PlacePhotosRoute>() || hasRoute<BestShotRoute>() || hasRoute<CleanUpRoute>()

@Composable
fun WendNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SplashRoute) {
        composable<SplashRoute> {
            SplashScreen(
                onFinished = { destination ->
                    // 이미 권한이 있으면 결과 화면이 첫 화면 — 뒤로 가면 앱이 닫힌다
                    val first: Any = when (destination) {
                        SplashDestination.Nickname -> NicknameRoute
                        SplashDestination.Globe -> GlobeRoute
                        SplashDestination.Permission -> PermRoute
                        SplashDestination.Granted -> GrantedRoute
                        SplashDestination.Partial -> PartialRoute
                        SplashDestination.Analyzing -> AnalyzeRoute
                        SplashDestination.Found -> FoundRoute
                        SplashDestination.NoFound -> NoFoundRoute
                        SplashDestination.Trips -> TripsRoute
                    }
                    navController.navigate(first) {
                        popUpTo(SplashRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<NicknameRoute> {
            NicknameScreen(onSaved = { navController.navigate(GlobeRoute) })
        }
        composable<GlobeRoute> {
            GlobeScreen(
                onBack = {
                    // 닉네임을 이미 정해서 바로 들어온 경우엔 뒤가 없으므로 닉네임 수정으로 보낸다
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(NicknameRoute) {
                            popUpTo(GlobeRoute) { inclusive = true }
                        }
                    }
                },
                onNext = { navController.navigate(GuideRoute) },
            )
        }
        composable<GuideRoute> {
            GuideScreen(
                onBack = { navController.popBackStack() },
                onFinished = { navController.navigate(PermRoute) },
            )
        }
        composable<PermRoute> {
            PermScreen(
                onBack = {
                    // 재실행해서 권한 안내부터 시작했으면 뒤가 없으므로 틱·핀 안내로 보낸다
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(GuideRoute) {
                            popUpTo(PermRoute) { inclusive = true }
                        }
                    }
                },
                onResult = navController::showMediaResult,
            )
        }
        composable<GrantedRoute> {
            GrantedScreen(
                onResult = navController::showMediaResult,
                onAnalysisStarted = { navController.navigate(AnalyzeRoute) },
            )
        }
        composable<PartialRoute> {
            PartialScreen(
                onResult = navController::showMediaResult,
                onAnalysisStarted = { navController.navigate(AnalyzeRoute) },
            )
        }
        composable<AnalyzeRoute> {
            AnalyzeScreen(
                // 결과 화면에서 뒤로 가면 분석 화면은 건너뛴다
                onFinished = { foundAny ->
                    navController.navigate(if (foundAny) FoundRoute else NoFoundRoute) {
                        popUpTo(AnalyzeRoute) { inclusive = true }
                    }
                },
                onFailed = navController::showMediaResult,
            )
        }
        composable<FoundRoute>(
            exitTransition = { if (targetState.destination.hasRoute<TripRoute>()) ExitTransition.KeepUntilTransitionsFinished else null },
            popEnterTransition = { if (initialState.destination.hasRoute<TripRoute>()) EnterTransition.None else null },
        ) {
            FoundScreen(
                onReanalyze = { navController.reanalyze(FoundRoute) },
                onOpenTrips = navController::openTripsAsHome,
                onTripClick = { navController.navigate(TripRoute(it)) },
            )
        }
        composable<NoFoundRoute> {
            NoFoundScreen(onReanalyze = { navController.reanalyze(NoFoundRoute) }, onOpenTrips = navController::openTripsAsHome)
        }
        composable<TripsRoute>(
            // 여행 상세가 카드 방울에서 퍼지는 동안 목록은 그대로 아래에 남는다
            exitTransition = { if (targetState.destination.hasRoute<TripRoute>()) ExitTransition.KeepUntilTransitionsFinished else null },
            popEnterTransition = { if (initialState.destination.hasRoute<TripRoute>()) EnterTransition.None else null },
        ) {
            TripsScreen(
                onReanalyze = { navController.reanalyze(TripsRoute) },
                onTripClick = { number, origin -> navController.navigate(TripRoute(number, origin.x, origin.y)) },
                onSettingsClick = { navController.navigate(SettingsRoute) },
                onLeftClick = { navController.navigate(LeftRoute) },
            )
        }
        composable<LeftRoute> { entry ->
            val viewModel: LeftViewModel = hiltViewModel()
            // 크게 보기는 이 화면 위에 겹쳐 열린다
            val viewer: ViewerViewModel = hiltViewModel(key = "viewer")
            Box(Modifier.fillMaxSize()) {
                LeftScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { index -> viewer.open(ViewerArgs(trip = 0, place = 0, index = index, left = true)) },
                    viewModel = viewModel,
                )
                ViewerScreen(viewer, onTripChanged = viewModel::onResult)
            }
        }
        composable<LicensesRoute> { LicensesScreen(onBack = { navController.popBackStack() }) }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                // 처음부터 다시 찾기: 목록·설정은 기록에서 지우고 분석 화면으로 — 끝나면 결과 화면
                onRescan = {
                    navController.navigate(AnalyzeRoute) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                // 데이터를 지웠으면 처음 실행처럼 스플래시부터
                onWiped = {
                    navController.navigate(SplashRoute) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onLicenses = { navController.navigate(LicensesRoute) },
            )
        }
        composable<TripRoute>(
            // 목록 ↔ 상세: 겹쳐 흐리게 바꾸지 않고, 상세가 카드 방울에서 퍼지고(아래 TripReveal) 방울로 오므라든다.
            // 크게 보기·장소 사진은 상세 위에 열리고 닫힌다 — 상세는 아래에 그대로 둔다
            enterTransition = { EnterTransition.None },
            exitTransition = { if (targetState.destination.overTrip()) ExitTransition.KeepUntilTransitionsFinished else null },
            popExitTransition = { ExitTransition.KeepUntilTransitionsFinished },
        ) { entry ->
            val route = entry.toRoute<TripRoute>()
            val number = route.number
            // 처음 열 때만 퍼지고(사진 크게 보기에서 돌아올 때는 아님), 목록으로 돌아갈 때만 오므라든다
            var revealed by rememberSaveable { mutableStateOf(false) }
            val anim by transition.animateFloat(transitionSpec = { tween(520, easing = FastOutSlowInEasing) }, label = "tripReveal") {
                if (it == EnterExitState.Visible) 1f else 0f
            }
            val leavingToList = transition.targetState == EnterExitState.PostExit &&
                navController.currentBackStackEntry?.destination?.overTrip() != true
            val progress = when {
                leavingToList -> anim
                transition.targetState == EnterExitState.PostExit -> 1f
                !revealed -> anim
                else -> 1f
            }
            LaunchedEffect(anim) { if (anim >= 1f) revealed = true }
            TripReveal(
                progress = progress,
                origin = if (route.x >= 0f) Offset(route.x, route.y) else null,
                color = tripColor(number),
            ) {
                val viewModel: TripDetailViewModel = hiltViewModel()
                entry.OnResult<String>(TripDetailViewModel.VIEWER_RESULT) { viewModel.onResult(it) }
                // 크게 보기는 이 화면 위에 겹쳐 열린다
                val viewer: ViewerViewModel = hiltViewModel(key = "viewer")
                Box(Modifier.fillMaxSize()) {
                    TripDetailScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onPhotoClick = { place, index -> viewer.open(ViewerArgs(number, place, index)) },
                        onOpenPlace = { place, tile ->
                            navController.navigate(PlacePhotosRoute(number, place, tile.left, tile.top, tile.width, tile.height))
                        },
                        onBestShots = { navController.navigate(BestShotRoute(number)) },
                        onCleanUp = { navController.navigate(CleanUpRoute(number)) },
                    )
                    ViewerScreen(viewer, onTripChanged = viewModel::onResult)
                }
            }
        }
        composable<PlacePhotosRoute>(
            // 시안 ㉑: 타일 자리에서 종이 앨범이 펼쳐지고(아래 AlbumUnfold) 그 자리로 접힌다. 크게 보기는 그 위에 열린다
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
            popExitTransition = { ExitTransition.KeepUntilTransitionsFinished },
        ) { entry ->
            val route = entry.toRoute<PlacePhotosRoute>()
            // 처음 열 때만 펼치고(크게 보기에서 돌아올 때는 아님), 상세로 돌아갈 때만 접는다
            var unfolded by rememberSaveable { mutableStateOf(false) }
            // 펼칠 때는 상세를 아래에 붙잡아 두는 시간만(사진을 읽고 그리는 동안 + 펼치기), 접을 때는 접히는 값
            val anim by transition.animateFloat(
                transitionSpec = { if (targetState == EnterExitState.Visible) tween(800, easing = LinearEasing) else tween(420, easing = PhotoEasing) },
                label = "albumUnfold",
            ) { if (it == EnterExitState.Visible) 1f else 0f }
            val closing = transition.targetState == EnterExitState.PostExit
            // 펼치기는 사진을 읽고 그리드를 처음 그린 뒤에 시작한다 — 첫 그리기가 무거워(한 프레임 ~120ms)
            // 펼치는 도중에 그리면 그만큼 건너뛰어 보인다. 그전에는 앨범을 숨겨 빈 종이가 비치지 않게 한다
            var ready by remember { mutableStateOf(unfolded) }
            val unfold = remember { Animatable(if (unfolded) 1f else 0f) }
            LaunchedEffect(ready) {
                if (!ready || unfolded) return@LaunchedEffect
                unfold.animateTo(1f, tween(420, easing = PhotoEasing))
                unfolded = true
            }
            val progress = when {
                closing -> anim
                transition.targetState == EnterExitState.PostExit -> 1f
                else -> unfold.value
            }
            AlbumUnfold(
                progress = progress,
                origin = if (route.w > 0f) Rect(Offset(route.x, route.y), Size(route.w, route.h)) else null,
                closing = closing,
                modifier = Modifier.graphicsLayer { alpha = if (ready) 1f else 0f },
            ) {
                val viewModel: PlacePhotosViewModel = hiltViewModel()
                // 크게 보기는 이 화면 위에 겹쳐 열린다
                val viewer: ViewerViewModel = hiltViewModel(key = "viewer")
                Box(Modifier.fillMaxSize()) {
                    PlacePhotosScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onPhotoClick = { place, index -> viewer.open(ViewerArgs(route.trip, place, index)) },
                        onReady = { ready = true },
                    )
                    ViewerScreen(
                        viewer,
                        onTripChanged = { message ->
                            viewModel.onResult(message)
                            // 아래 상세도 조용히 다시 읽는다(안내는 장소 사진에서)
                            runCatching { navController.getBackStackEntry<TripRoute>() }.getOrNull()
                                ?.savedStateHandle?.set(TripDetailViewModel.VIEWER_RESULT, "")
                        },
                        onPageChange = viewModel::showLastViewed,
                    )
                }
            }
        }
        composable<BestShotRoute> {
            BestShotScreen(
                onBack = { navController.popBackStack() },
                onDone = { count ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        TripDetailViewModel.VIEWER_RESULT,
                        "베스트샷 ${count}장을 골랐어요. 사진에 별이 붙어 있어요",
                    )
                    navController.popBackStack()
                },
            )
        }
        composable<CleanUpRoute> {
            CleanUpScreen(
                onBack = { navController.popBackStack() },
                onDone = { count ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        TripDetailViewModel.VIEWER_RESULT,
                        "${count}장을 휴지통으로 옮겼어요. 30일 안에 갤러리에서 되살릴 수 있어요",
                    )
                    navController.popBackStack()
                },
            )
        }
        composable<DeniedRoute> { DeniedScreen(onResult = navController::showMediaResult) }
    }
}

/**
 * 위에 열렸던 화면이 이 항목의 savedStateHandle 에 남긴 결과를 한 번 받는다.
 * 항목의 savedStateHandle 은 그 화면 ViewModel 의 SavedStateHandle 과 다른 객체라, ViewModel 이 직접 지켜볼 수 없다.
 */
@Composable
private fun <T : Any> NavBackStackEntry.OnResult(key: String, onResult: (T) -> Unit) {
    val value by savedStateHandle.getStateFlow<T?>(key, null).collectAsStateWithLifecycle()
    val current by rememberUpdatedState(onResult)
    LaunchedEffect(value) {
        val v = value ?: return@LaunchedEffect
        savedStateHandle[key] = null
        current(v)
    }
}

/** 목록이 홈: 첫 실행 화면(온보딩·권한·분석)은 뒤로 가기 기록에서 지운다 */
private fun NavController.openTripsAsHome() {
    navigate(TripsRoute) {
        popUpTo(graph.id) { inclusive = true }
    }
}

/** 모든 사진을 허용해서 처음부터 다시 찾는다: 결과 화면을 분석 화면으로 바꿔 끼운다 */
private fun NavController.reanalyze(from: Any) {
    navigate(AnalyzeRoute) {
        popUpTo(from) { inclusive = true }
    }
}

/**
 * 권한 결과 화면으로. 결과 화면끼리는 쌓지 않고 바꿔 끼운다.
 * 권한 안내를 거쳐 왔으면 그 바로 위에 두어 뒤로 가면 권한 안내로, 재실행해서 결과 화면부터 시작했으면 계속 첫 화면으로 둔다.
 */
private fun NavController.showMediaResult(access: MediaAccess) {
    val route: Any = when (access) {
        MediaAccess.All -> GrantedRoute
        MediaAccess.Partial -> PartialRoute
        MediaAccess.None -> DeniedRoute
    }
    val fromPermScreen = runCatching { getBackStackEntry(PermRoute) }.isSuccess
    val current = currentDestination?.id
    navigate(route) {
        if (fromPermScreen) {
            popUpTo(PermRoute)
        } else if (current != null) {
            popUpTo(current) { inclusive = true }
        }
        launchSingleTop = true
    }
}
