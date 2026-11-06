package org.carbridge.patch

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import java.io.ByteArrayOutputStream

/** Builds the car copy's label and adaptive icon: the original art plus a guide-sign badge. */
object IconBadger {
    // One adaptive-icon layer (108dp) at xxxhdpi
    private const val SIZE = 432
    private const val UNIT = SIZE / 108f
    private const val GUIDE_GREEN = 0xFF0B6B4C.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun branding(context: Context, app: ApplicationInfo): ManifestPatcher.Branding {
        val pm = context.packageManager
        val label = pm.getApplicationLabel(app).toString() + PatchPipeline.CAR_LABEL_SUFFIX
        return ManifestPatcher.Branding(label, background(pm.getApplicationIcon(app)).png(), badge().png())
    }

    private fun background(icon: android.graphics.drawable.Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (icon is AdaptiveIconDrawable) {
            for (layer in listOfNotNull(icon.background, icon.foreground)) {
                layer.setBounds(0, 0, SIZE, SIZE)
                layer.draw(canvas)
            }
        } else {
            // Legacy icons sit inside the 72dp visible area on a plain plate
            canvas.drawColor(WHITE)
            val inset = (18 * UNIT).toInt()
            icon.setBounds(inset, inset, SIZE - inset, SIZE - inset)
            icon.draw(canvas)
        }
        return bitmap
    }

    /** White-ringed green disc with the bridge mark, inside the 66dp safe zone's lower right. */
    internal fun badge(): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = 70 * UNIT
        val cy = 70 * UNIT
        paint.color = WHITE
        canvas.drawCircle(cx, cy, 11 * UNIT, paint)
        paint.color = GUIDE_GREEN
        canvas.drawCircle(cx, cy, 9.3f * UNIT, paint)

        // Bridge drawn on the same 24-unit grid as the launcher icon, scaled into the disc
        canvas.translate(62.5f * UNIT, 62.5f * UNIT)
        canvas.scale(0.625f * UNIT, 0.625f * UNIT)
        paint.color = WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 2.8f
        canvas.drawPath(Path().apply {
            moveTo(2f, 15f); lineTo(22f, 15f)
            moveTo(7f, 5f); lineTo(7f, 18f)
            moveTo(17f, 5f); lineTo(17f, 18f)
        }, paint)
        paint.strokeWidth = 2f
        canvas.drawPath(Path().apply {
            moveTo(7f, 5f); quadTo(12f, 15f, 17f, 5f)
            moveTo(2f, 11.5f); quadTo(5f, 9.5f, 7f, 5f)
            moveTo(17f, 5f); quadTo(19f, 9.5f, 22f, 11.5f)
        }, paint)
        return bitmap
    }

    private fun Bitmap.png(): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
