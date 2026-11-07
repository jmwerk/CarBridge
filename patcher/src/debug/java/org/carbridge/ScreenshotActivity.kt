package org.carbridge

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModelProvider
import org.carbridge.core.CarOptions
import org.carbridge.patch.IconBadger
import org.carbridge.ui.CarBridgeTheme
import org.carbridge.ui.HomeActions
import org.carbridge.ui.HomeScreen

/** Shows the home screen with made-up apps, so store screenshots don't expose anyone's phone. */
class ScreenshotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[HomeModel::class.java]
        model.showSample(sampleCatalog())
        val none = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
        setContent { CarBridgeTheme { HomeScreen(model, none) } }
    }

    private fun sampleCatalog(): Catalog {
        val trail = source("Trail Maps", "T", 0xFF2E7D32, 48)
        val arcade = source("Pocket Arcade", "P", 0xFF6A1B9A, 61)
        val radio = source("Night Radio", "N", 0xFF1A237E, 23)
        val copies = listOf(
            copy(arcade, "P", 0xFF6A1B9A, Version(40, "4.0.2"), Version(41, "4.1.0"), portrait = true),
            copy(radio, "N", 0xFF1A237E, Version(12, "1.2"), Version(12, "1.2")),
            copy(trail, "T", 0xFF2E7D32, Version(305, "3.0.5"), Version(305, "3.0.5")),
        )
        val available = listOf(
            source("Chess Club", "C", 0xFF5D4037, 35),
            source("Recipe Box", "R", 0xFFE65100, 19),
            source("Sky Watch", "S", 0xFF0277BD, 27),
            source("Word Garden", "W", 0xFF00796B, 14),
            source("Zen Puzzles", "Z", 0xFFAD1457, 52),
        )
        return Catalog(copies, available)
    }

    private fun source(label: String, letter: String, color: Long, mb: Long) = SourceApp(
        label = label,
        info = ApplicationInfo().apply { packageName = "sample." + label.lowercase().replace(' ', '.') },
        icon = icon(letter, color, badged = false),
        sizeBytes = mb * 1_000_000,
        hasAndroidAuto = false,
        caution = null,
    )

    private fun copy(original: SourceApp, letter: String, color: Long, version: Version, originalVersion: Version, portrait: Boolean = false) =
        CarCopy(
            label = original.label,
            packageName = original.info.packageName + ".car",
            icon = icon(letter, color, badged = true),
            original = original,
            version = version,
            originalVersion = originalVersion,
            portraitLocked = portrait,
            hasMediaService = false,
            options = CarOptions(),
        )

    private fun icon(letter: String, color: Long, badged: Boolean): ImageBitmap {
        val layer = Bitmap.createBitmap(LAYER, LAYER, Bitmap.Config.ARGB_8888)
        Canvas(layer).apply {
            drawColor(color.toInt())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textSize = LAYER * 0.36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            // Badged icons nudge the letter up-left so the badge doesn't cover it
            val center = if (badged) LAYER * 0.45f else LAYER / 2f
            drawText(letter, center, center - (paint.ascent() + paint.descent()) / 2, paint)
        }
        val foreground: Drawable = if (badged) BitmapDrawable(resources, IconBadger.badge()) else ColorDrawable(Color.TRANSPARENT)
        val drawable = AdaptiveIconDrawable(BitmapDrawable(resources, layer), foreground)
        val out = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, ICON_PX, ICON_PX)
        drawable.draw(Canvas(out))
        return out.asImageBitmap()
    }

    private companion object {
        const val LAYER = 432
        const val ICON_PX = 144
    }
}
