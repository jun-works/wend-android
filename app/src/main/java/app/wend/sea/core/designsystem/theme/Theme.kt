package app.wend.sea.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PaperColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    error = Error,
    errorContainer = ErrorContainer,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    inverseSurface = OnSurface,
    inverseOnSurface = Surface,
)

private val WendShapes = Shapes(
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

// 시안은 종이 테마 한 가지로 확정되어 다크 테마를 두지 않는다.
@Composable
fun WendTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperColorScheme,
        typography = Typography,
        shapes = WendShapes,
        content = content,
    )
}
