package app.wend.sea.navigation

import androidx.compose.runtime.Composable
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
import app.wend.sea.feature.trip.TripDetailScreen
import app.wend.sea.feature.trips.TripsScreen
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
@Serializable data class TripRoute(val number: Int)
@Serializable data object LicensesRoute

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
        composable<FoundRoute> {
            FoundScreen(
                onReanalyze = { navController.reanalyze(FoundRoute) },
                onOpenTrips = navController::openTripsAsHome,
                onTripClick = { navController.navigate(TripRoute(it)) },
            )
        }
        composable<NoFoundRoute> {
            NoFoundScreen(onReanalyze = { navController.reanalyze(NoFoundRoute) }, onOpenTrips = navController::openTripsAsHome)
        }
        composable<TripsRoute> {
            TripsScreen(
                onReanalyze = { navController.reanalyze(TripsRoute) },
                onTripClick = { navController.navigate(TripRoute(it)) },
                // 설정 화면이 생기기 전까지는 라이선스로 바로 간다
                onSettingsClick = { navController.navigate(LicensesRoute) },
            )
        }
        composable<LicensesRoute> { LicensesScreen(onBack = { navController.popBackStack() }) }
        composable<TripRoute> { TripDetailScreen(onBack = { navController.popBackStack() }) }
        composable<DeniedRoute> { DeniedScreen(onResult = navController::showMediaResult) }
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
