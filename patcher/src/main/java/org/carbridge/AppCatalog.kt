package org.carbridge

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
    /** The app already ships its own Android Auto experience (media, messaging or templates). */
    val hasAndroidAuto: Boolean,
)

/** An installed car copy, with the original it came from when that is still installed. */
data class CarCopy(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val original: SourceApp?,
    val version: Version?,
    val originalVersion: Version?,
    /** The original locks some screens to portrait, so the layout choice matters. */
    val portraitLocked: Boolean,
    val fillsScreen: Boolean,
) {
    /** True when the original app has been updated past the version this copy was made from. */
    val outdated get() = version != null && originalVersion != null && originalVersion.code > version.code
}

data class Version(val code: Long, val name: String?)

data class Catalog(val copies: List<CarCopy>, val available: List<SourceApp>)

object AppCatalog {
    private const val ICON_PX = 144

    fun load(context: Context, fillsScreen: (String) -> Boolean): Catalog {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != context.packageName }

        val (copyInfos, sourceInfos) = launchable.partition { it.packageName.endsWith(CAR_SUFFIX) }
        // Launcher query results don't carry meta-data, so read it once for all apps
        val autoApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.metaData?.containsKey(CAR_APP_META) == true }
            .map { it.packageName }
            .toSet()
        val sources = sourceInfos.associate { it.packageName to it.toSource(pm, it.packageName in autoApps) }

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
                portraitLocked = original != null && locksPortrait(pm, originalPkg),
                fillsScreen = fillsScreen(originalPkg),
            )
        }.sortedWith(compareBy({ !it.outdated }, { it.label.lowercase() }))

        val copied = copies.mapNotNull { it.original?.info?.packageName }.toSet()
        val available = sources.values.filter { it.info.packageName !in copied }.sortedBy { it.label.lowercase() }
        return Catalog(copies, available)
    }

    private fun ApplicationInfo.toSource(pm: PackageManager, hasAndroidAuto: Boolean) = SourceApp(
        label = loadLabel(pm).toString(),
        info = this,
        icon = pm.getApplicationIcon(this).toImageBitmap(),
        sizeBytes = (listOf(sourceDir) + splitSourceDirs.orEmpty()).sumOf { File(it).length() },
        hasAndroidAuto = hasAndroidAuto,
    )

    private fun locksPortrait(pm: PackageManager, pkg: String): Boolean =
        runCatching {
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities.orEmpty()
                .any { it.screenOrientation in PORTRAIT_ORIENTATIONS }
        }.getOrDefault(false)

    private fun versionOf(pm: PackageManager, pkg: String): Version? =
        runCatching { pm.getPackageInfo(pkg, 0).let { Version(it.longVersionCode, it.versionName) } }.getOrNull()

    private fun Drawable.toImageBitmap(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, ICON_PX, ICON_PX)
        draw(Canvas(bitmap))
        return bitmap.asImageBitmap()
    }

    private val CAR_SUFFIX = PatchPipeline.carPackageName("")
    private val PORTRAIT_ORIENTATIONS = setOf(
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
    )
    private const val CAR_APP_META = "com.google.android.gms.car.application"
}
