package org.carbridge.core

/** What the user can change about a car patch. */
data class CarOptions(
    /** Keep media browser services, so audio apps keep Android Auto's player. */
    val keepMediaServices: Boolean = false,
    /** Drop portrait-only locks, so the app fills the landscape car display. */
    val fillScreen: Boolean = true,
)

/**
 * The Android Auto changes shared by CarBridge and CarBridge Patches: car meta-data, launcher
 * categories, car-surface permissions, and no services that make Android Auto show its
 * template UI instead of the app.
 */
object CarPatch {
    const val CAR_APP_META = "com.google.android.gms.car.application"
    const val DESCRIPTOR_NAME = "carbridge_automotive_app_desc"

    // android.R.attr ids, verified against android-36 android.jar
    val NAME = AndroidAttr("name", 0x01010003)
    val VALUE = AndroidAttr("value", 0x01010024)
    val RESOURCE = AndroidAttr("resource", 0x01010025)
    val RESIZEABLE = AndroidAttr("resizeableActivity", 0x010104f6)
    val APP_CATEGORY = AndroidAttr("appCategory", 0x01010545, mapOf("game" to 0))
    val SCREEN_ORIENTATION = AndroidAttr(
        "screenOrientation",
        0x0101001e,
        mapOf(
            "unspecified" to -1, "landscape" to 0, "portrait" to 1, "user" to 2, "behind" to 3,
            "sensor" to 4, "nosensor" to 5, "sensorLandscape" to 6, "sensorPortrait" to 7,
            "reverseLandscape" to 8, "reversePortrait" to 9, "fullSensor" to 10,
            "userLandscape" to 11, "userPortrait" to 12, "fullUser" to 13, "locked" to 14,
        ),
    )

    // ActivityInfo.SCREEN_ORIENTATION_{PORTRAIT, SENSOR_PORTRAIT, REVERSE_PORTRAIT, USER_PORTRAIT}
    val PORTRAIT_ORIENTATIONS = setOf(1, 7, 9, 12)

    val CAR_PERMISSIONS = listOf("androidx.car.app.ACCESS_SURFACE", "androidx.car.app.MAP_TEMPLATES")
    val CAR_CATEGORIES = listOf(
        "android.intent.category.CAR_LAUNCHER",
        "androidx.car.app.category.NAVIGATION",
        "android.intent.category.APP_MAPS",
    )
    val MEDIA_SERVICE_ACTIONS = setOf(
        "android.media.browse.MediaBrowserService",
        "androidx.media3.session.MediaLibraryService",
    )
    const val CAR_APP_SERVICE_ACTION = "androidx.car.app.CarAppService"

    fun apply(host: ManifestHost, options: CarOptions = CarOptions()) {
        val app = host.application
        addCarEntries(host, app)
        val stripped = if (options.keepMediaServices) setOf(CAR_APP_SERVICE_ACTION) else MEDIA_SERVICE_ACTIONS + CAR_APP_SERVICE_ACTION
        stripTemplateServices(host, app, stripped)
        if (options.fillScreen) unlockPortrait(host, app)
    }

    fun launcherActivities(app: ManifestElement): List<ManifestElement> =
        (app.children("activity") + app.children("activity-alias"))
            .filter { a -> a.children("intent-filter").any(::isLauncherFilter) }

    private fun addCarEntries(host: ManifestHost, app: ManifestElement) {
        app.setEnum(APP_CATEGORY, "game")
        ensureMeta(app, "distractionOptimized") { it.setBoolean(VALUE, true) }
        ensureMeta(app, "androidx.car.app.minCarApiLevel") { it.setInt(VALUE, 7) }
        if (findMeta(app, CAR_APP_META) == null) {
            host.addAutomotiveDescriptor(DESCRIPTOR_NAME, "media")
            ensureMeta(app, CAR_APP_META) { it.setResource(RESOURCE, "xml", DESCRIPTOR_NAME) }
        }

        for (perm in CAR_PERMISSIONS) {
            if (!host.hasPermission(perm)) host.addPermission(perm)
        }

        val patched = mutableListOf<String>()
        for (activity in app.children("activity") + app.children("activity-alias")) {
            for (filter in activity.children("intent-filter")) {
                if (!isLauncherFilter(filter)) continue
                val existing = filter.children("category").mapNotNull { it.getString(NAME) }.toSet()
                for (cat in CAR_CATEGORIES - existing) filter.append("category").setString(NAME, cat)
                ensureMeta(activity, "distractionOptimized") { it.setBoolean(VALUE, true) }
                activity.setBoolean(RESIZEABLE, true)
                activity.getString(NAME)?.let(patched::add)
            }
        }
        check(patched.isNotEmpty()) { "no launcher activity found" }
        host.log("Car-enabled: ${patched.joinToString()}")
    }

    private fun stripTemplateServices(host: ManifestHost, app: ManifestElement, actions: Set<String>) {
        for (service in app.children("service")) {
            for (filter in service.children("intent-filter")) {
                val declared = filter.children("action").mapNotNull { it.getString(NAME) }
                if (declared.any { it in actions }) {
                    service.removeChild(filter)
                    host.log("Removed car template filter from ${service.getString(NAME)}")
                }
            }
        }
    }

    private fun unlockPortrait(host: ManifestHost, app: ManifestElement) {
        var unlocked = 0
        for (activity in app.children("activity")) {
            if (activity.getInt(SCREEN_ORIENTATION) in PORTRAIT_ORIENTATIONS) {
                activity.remove(SCREEN_ORIENTATION)
                unlocked++
            }
        }
        if (unlocked > 0) host.log("Unlocked portrait on $unlocked activities")
    }

    private inline fun ensureMeta(parent: ManifestElement, name: String, setValue: (ManifestElement) -> Unit) {
        if (findMeta(parent, name) != null) return
        val meta = parent.append("meta-data")
        meta.setString(NAME, name)
        setValue(meta)
    }

    private fun findMeta(parent: ManifestElement, name: String) =
        parent.children("meta-data").firstOrNull { it.getString(NAME) == name }

    private fun isLauncherFilter(filter: ManifestElement): Boolean {
        val actions = filter.children("action").mapNotNull { it.getString(NAME) }
        val cats = filter.children("category").mapNotNull { it.getString(NAME) }
        return "android.intent.action.MAIN" in actions && "android.intent.category.LAUNCHER" in cats
    }
}
