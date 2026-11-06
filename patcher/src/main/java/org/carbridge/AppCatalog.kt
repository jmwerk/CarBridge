package org.carbridge

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import org.carbridge.patch.PatchPipeline

/** An app the user can add to the car. */
data class SourceApp(
    val label: String,
    val info: ApplicationInfo,
    val icon: ImageBitmap,
    val sizeBytes: Long,
)

/** An installed car copy, with the original it came from when that is still installed. */
data class CarCopy(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val original: SourceApp?,
    val version: Version?,
    val originalVersion: Version?,
) {
    /** True when the original app has been updated past the version this copy was made from. */
    val outdated get() = version != null && originalVersion != null && originalVersion.code > version.code
}

data class Version(val code: Long, val name: String?)

data class Catalog(val copies: List<CarCopy>, val available: List<SourceApp>)

object AppCatalog {
    private const val ICON_PX = 144

    fun load(context: Context): Catalog {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != context.packageName }

        val (copyInfos, sourceInfos) = launchable.partition { it.packageName.endsWith(CAR_SUFFIX) }
        val sources = sourceInfos.associate { it.packageName to it.toSource(pm) }

        val copies = copyInfos.map { info ->
            val originalPkg = info.packageName.removeSuffix(CAR_SUFFIX)
            val original = sources[originalPkg]
            CarCopy(
                // The sign already says it is on the car
                label = info.loadLabel(pm).toString().removeSuffix(PatchPipeline.CAR_LABEL_SUFFIX),
                packageName = info.packageName,
                icon = pm.getApplicationIcon(info).toImageBitmap(),
                original = original,
                version = versionOf(pm, info.packageName),
                originalVersion = original?.let { versionOf(pm, originalPkg) },
            )
        }.sortedWith(compareBy({ !it.outdated }, { it.label.lowercase() }))

        val copied = copies.mapNotNull { it.original?.info?.packageName }.toSet()
        val available = sources.values.filter { it.info.packageName !in copied }.sortedBy { it.label.lowercase() }
        return Catalog(copies, available)
    }

    private fun ApplicationInfo.toSource(pm: PackageManager) = SourceApp(
        label = loadLabel(pm).toString(),
        info = this,
        icon = pm.getApplicationIcon(this).toImageBitmap(),
        sizeBytes = (listOf(sourceDir) + splitSourceDirs.orEmpty()).sumOf { File(it).length() },
    )

    private fun versionOf(pm: PackageManager, pkg: String): Version? =
        runCatching { pm.getPackageInfo(pkg, 0).let { Version(it.longVersionCode, it.versionName) } }.getOrNull()

    private fun Drawable.toImageBitmap(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, ICON_PX, ICON_PX)
        draw(Canvas(bitmap))
        return bitmap.asImageBitmap()
    }

    private val CAR_SUFFIX = PatchPipeline.carPackageName("")
}
