package app.wend.sea.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.wend.sea.feature.globe.GlobeScreen
import app.wend.sea.feature.guide.GuideScreen
import app.wend.sea.feature.nickname.NicknameScreen
import app.wend.sea.feature.splash.SplashDestination
import app.wend.sea.feature.splash.SplashScreen
import kotlinx.serialization.Serializable

@Serializable data object SplashRoute
@Serializable data object NicknameRoute
@Serializable data object GlobeRoute
@Serializable data object GuideRoute

@Composable
fun WendNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SplashRoute) {
        composable<SplashRoute> {
            SplashScreen(
                onFinished = { destination ->
                    val route: Any = when (destination) {
                        SplashDestination.Nickname -> NicknameRoute
                        SplashDestination.Globe -> GlobeRoute
                    }
                    navController.navigate(route) {
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
            GuideScreen(onBack = { navController.popBackStack() })
        }
    }
}
