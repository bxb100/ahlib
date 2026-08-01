package cn.ahlib.reservation.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.border
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.fillWidth
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.scale
import androidx.compose.foundation.style.transformOrigin
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp

internal val StyleScope.reservationColors: ColorScheme
    get() = LocalReservationColorScheme.currentValue

internal val StyleScope.reservationTypography: Typography
    get() = LocalReservationTypography.currentValue

internal val StyleScope.reservationShapes: Shapes
    get() = LocalReservationShapes.currentValue

internal val StyleScope.reservationSpacing: ReservationSpacing
    get() = LocalReservationSpacing.currentValue

internal object ComponentStyles {
    val roomCard: Style = Style {
        fillWidth()
        contentPadding(reservationSpacing.medium)
        shape(reservationShapes.extraLarge)
        clip()
        background(reservationColors.surfaceContainerLow)
        border(
            width = 1.dp,
            color = reservationColors.outlineVariant.copy(alpha = 0.32f),
        )
        transformOrigin(TransformOrigin.Center)

        hovered {
            animate(tween(durationMillis = 120)) {
                background(reservationColors.surfaceContainer)
                borderColor(reservationColors.outline.copy(alpha = 0.42f))
            }
        }
        focused {
            border(
                width = 2.dp,
                color = reservationColors.primary.copy(alpha = 0.72f),
            )
        }
        pressed {
            animate(tween(durationMillis = 110)) {
                scale(0.985f)
                background(reservationColors.surfaceContainer)
                borderColor(reservationColors.primary.copy(alpha = 0.48f))
            }
        }
    }
}

internal object ReservationDesignSystem {
    val styles: ComponentStyles = ComponentStyles
}
