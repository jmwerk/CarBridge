package org.carbridge.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The guide-sign exit arrow, matching the launcher icon. */
val ExitArrow: ImageVector by lazy {
    ImageVector.Builder("ExitArrow", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 3.2f, strokeLineCap = StrokeCap.Round) {
            moveTo(5.5f, 18.5f)
            lineTo(14.5f, 9.5f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 4.5f)
            lineTo(19.5f, 4.5f)
            lineTo(19.5f, 15f)
            close()
        }
    }.build()
}
