package hu.safora.documentstudio

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

internal fun compactTextScale(widthDp:Int):Float=when{
    widthDp<360->.86f
    widthDp<400->.94f
    else->1f
}
internal fun scaledTypography(widthDp:Int):Typography{
    val scale=compactTextScale(widthDp)
    val base=Typography()
    fun TextStyle.scaled()=copy(fontSize=fontSize*scale,lineHeight=lineHeight*scale,letterSpacing=letterSpacing*scale)
    return base.copy(
        displayLarge=base.displayLarge.scaled(),displayMedium=base.displayMedium.scaled(),displaySmall=base.displaySmall.scaled(),
        headlineLarge=base.headlineLarge.scaled(),headlineMedium=base.headlineMedium.scaled(),headlineSmall=base.headlineSmall.scaled(),
        titleLarge=base.titleLarge.scaled(),titleMedium=base.titleMedium.scaled(),titleSmall=base.titleSmall.scaled(),
        bodyLarge=base.bodyLarge.scaled(),bodyMedium=base.bodyMedium.scaled(),bodySmall=base.bodySmall.scaled(),
        labelLarge=base.labelLarge.scaled(),labelMedium=base.labelMedium.scaled(),labelSmall=base.labelSmall.scaled()
    )
}
@Composable internal fun windowWidthDp():Int=(LocalWindowInfo.current.containerSize.width/LocalDensity.current.density).roundToInt().coerceAtLeast(1)
@Composable internal fun responsiveTypography():Typography{
    val width=windowWidthDp()
    return remember(width){scaledTypography(width)}
}
@Composable internal fun screenGutter()=when{
    windowWidthDp()<360->12.dp
    windowWidthDp()<400->16.dp
    else->18.dp
}
