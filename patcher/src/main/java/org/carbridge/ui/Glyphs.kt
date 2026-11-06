package org.carbridge.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The suspension-bridge mark, matching the launcher icon and the car-copy badge. */
val BridgeMark: ImageVector by lazy {
    val ink = SolidColor(Color.Black)
    ImageVector.Builder("BridgeMark", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = ink, strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round) {
            moveTo(2f, 15f); lineTo(22f, 15f)
            moveTo(7f, 5f); lineTo(7f, 18.5f)
            moveTo(17f, 5f); lineTo(17f, 18.5f)
        }
        path(stroke = ink, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(7f, 5f); quadTo(12f, 15f, 17f, 5f)
            moveTo(2f, 11.5f); quadTo(5f, 9.5f, 7f, 5f)
            moveTo(17f, 5f); quadTo(19f, 9.5f, 22f, 11.5f)
        }
        path(stroke = ink, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Round) {
            moveTo(9.5f, 10f); lineTo(9.5f, 15f)
            moveTo(12f, 10.6f); lineTo(12f, 15f)
            moveTo(14.5f, 10f); lineTo(14.5f, 15f)
        }
    }.build()
}

/** A landscape screen outline for the "fill the car screen" layout option. */
val LandscapeGlyph: ImageVector by lazy {
    ImageVector.Builder("Landscape", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 6f); lineTo(19f, 6f)
            quadTo(21f, 6f, 21f, 8f); lineTo(21f, 16f)
            quadTo(21f, 18f, 19f, 18f); lineTo(5f, 18f)
            quadTo(3f, 18f, 3f, 16f); lineTo(3f, 8f)
            quadTo(3f, 6f, 5f, 6f); close()
        }
    }.build()
}
