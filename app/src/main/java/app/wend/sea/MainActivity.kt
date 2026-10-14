package app.wend.sea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import app.wend.sea.core.designsystem.theme.Surface
import app.wend.sea.core.designsystem.theme.WendTheme
import app.wend.sea.navigation.WendNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val paper = Surface.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(paper, paper),
            navigationBarStyle = SystemBarStyle.light(paper, paper),
        )
        setContent {
            WendTheme {
                WendNavHost()
            }
        }
    }
}
