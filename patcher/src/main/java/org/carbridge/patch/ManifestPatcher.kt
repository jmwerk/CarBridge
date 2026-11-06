package org.carbridge.patch

import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.value.ValueType

/**
 * Makes an app's launcher activity show up in Android Auto, following what the spike proved:
 * car meta-data, CAR_LAUNCHER + navigation categories, car-surface permissions, and no
 * media/template services (Android Auto would show its template UI instead).
 */
class ManifestPatcher(private val log: (String) -> Unit) {

    fun patch(apk: ApkModule, newPackage: String?) {
        val manifest = apk.androidManifest ?: error("APK has no AndroidManifest.xml")
        val app = manifest.applicationElement ?: error("manifest has no <application>")

        addCarEntries(apk, manifest, app)
        stripTemplateServices(app)
        if (newPackage != null) renamePackage(apk, manifest, newPackage)
        apk.refreshManifest()
        apk.refreshTable()
    }

    /** Split APKs must carry the same package name as the base; car entries live in the base only. */
    fun patchSplit(apk: ApkModule, newPackage: String) {
        val manifest = apk.androidManifest ?: error("split has no AndroidManifest.xml")
        renamePackage(apk, manifest, newPackage)
        apk.refreshManifest()
        if (apk.hasTableBlock()) apk.refreshTable()
    }

    private fun addCarEntries(apk: ApkModule, manifest: AndroidManifestBlock, app: ResXmlElement) {
        app.getOrCreateAndroidAttribute("appCategory", ID_APP_CATEGORY).setValueAsDecimal(APP_CATEGORY_GAME)
        ensureMeta(app, "distractionOptimized") { it.setValueAsBoolean(true) }
        ensureMeta(app, "androidx.car.app.minCarApiLevel") { it.setValueAsDecimal(7) }
        if (findMeta(app, CAR_APP_META) == null) {
            val resId = addAutomotiveDescriptor(apk)
            ensureMeta(app, CAR_APP_META, ID_RESOURCE, "resource") { it.setValueAsResourceId(resId) }
        }

        for (perm in CAR_PERMISSIONS) {
            if (manifest.getUsesPermission(perm) == null) manifest.addUsesPermission(perm)
        }

        val patched = mutableListOf<String>()
        for (activity in app.listElements("activity") + app.listElements("activity-alias")) {
            for (filter in activity.listElements("intent-filter")) {
                if (!isLauncherFilter(filter)) continue
                val existing = filter.listElements("category").mapNotNull { nameOf(it) }.toSet()
                for (cat in CAR_CATEGORIES - existing) {
                    filter.newElement("category").getOrCreateAndroidAttribute("name", ID_NAME).setValueAsString(cat)
                }
                ensureMeta(activity, "distractionOptimized") { it.setValueAsBoolean(true) }
                activity.getOrCreateAndroidAttribute("resizeableActivity", ID_RESIZEABLE).setValueAsBoolean(true)
                nameOf(activity)?.let(patched::add)
            }
        }
        check(patched.isNotEmpty()) { "no launcher activity found" }
        log("Car-enabled: ${patched.joinToString()}")
    }

    /** Adds res/xml/<name>.xml declaring the app as a car app and returns its resource id. */
    private fun addAutomotiveDescriptor(apk: ApkModule): Int {
        val table = apk.getTableBlock(false) ?: error("APK has no resources.arsc")
        val pkg = table.pickOne() ?: error("resources.arsc has no package")
        val path = "res/xml/$DESCRIPTOR_NAME.xml"
        val entry = pkg.getOrCreate("", "xml", DESCRIPTOR_NAME)
        entry.setValueAsString(path)

        val doc = ResXmlDocument()
        val root = doc.newElement("automotiveApp")
        root.newElement("uses").getOrCreateAttribute("name", 0).setValueAsString("media")
        doc.refreshFull()
        apk.add(ByteInputSource(doc.bytes, path))
        return entry.resourceId
    }

    private fun stripTemplateServices(app: ResXmlElement) {
        for (service in app.listElements("service")) {
            for (filter in service.listElements("intent-filter")) {
                val actions = filter.listElements("action").mapNotNull { nameOf(it) }.toSet()
                if (actions.any { it in TEMPLATE_SERVICE_ACTIONS }) {
                    filter.removeSelf()
                    log("Removed car template filter from ${nameOf(service)}")
                }
            }
        }
    }

    /** Lets the patched copy install next to the original; see the spike README for why. */
    private fun renamePackage(apk: ApkModule, manifest: AndroidManifestBlock, newPackage: String) {
        val old = manifest.packageName ?: error("manifest has no package name")
        val root = manifest.manifestElement ?: error("manifest has no <manifest>")

        // Relative class names resolve against the package; pin them to the original classes
        for (el in root.recursiveElements().asSequence().toList()) {
            for (id in CLASS_NAME_ATTRS) {
                val attr = el.searchAttributeByResourceId(id) ?: continue
                val v = attr.stringOrNull() ?: continue
                if (v.startsWith(".")) attr.setValueAsString(old + v)
            }
        }

        // Declared permissions and provider authorities must be unique on the device
        val declared = (root.listElements("permission") + root.listElements("permission-tree"))
            .mapNotNull { nameOf(it) }.toSet()
        for (el in root.recursiveElements().asSequence().toList()) {
            for (id in PERMISSION_ATTRS) {
                val attr = el.searchAttributeByResourceId(id) ?: continue
                val v = attr.stringOrNull() ?: continue
                if (v in declared) attr.setValueAsString(renameValue(v, old, newPackage))
            }
        }
        for (provider in manifest.applicationElement?.listElements("provider").orEmpty()) {
            val attr = provider.searchAttributeByResourceId(ID_AUTHORITIES) ?: continue
            if (attr.valueType == ValueType.REFERENCE) {
                renameStringResource(apk, attr.valueAsResourceId, old, newPackage)
            } else {
                attr.stringOrNull()?.let { attr.setValueAsString(renameValue(it, old, newPackage)) }
            }
        }

        // A shared user must have one signer, so the copy can't join the original's
        root.searchAttributeByResourceId(ID_SHARED_USER_ID)?.let { attr ->
            attr.stringOrNull()?.let { attr.setValueAsString(renameValue(it, old, newPackage)) }
        }

        // getIdentifier(name, type, getPackageName()) resolves against the table's package name
        val table = if (apk.hasTableBlock()) apk.getTableBlock(false) else null
        table?.listPackages()?.filter { it.name == old }?.forEach { it.name = newPackage }

        manifest.packageName = newPackage
        log("Renamed package $old -> $newPackage")
    }

    private fun renameStringResource(apk: ApkModule, resId: Int, old: String, new: String) {
        val resource = apk.getTableBlock(false)?.getResource(resId) ?: error("missing resource 0x%08x".format(resId))
        for (entry in resource) {
            entry.valueAsString?.let { entry.setValueAsString(renameValue(it, old, new)) }
        }
    }

    private fun ensureMeta(
        parent: ResXmlElement,
        name: String,
        valueId: Int = ID_VALUE,
        valueAttr: String = "value",
        set: (ResXmlAttribute) -> Unit,
    ) {
        if (findMeta(parent, name) != null) return
        val meta = parent.newElement("meta-data")
        meta.getOrCreateAndroidAttribute("name", ID_NAME).setValueAsString(name)
        set(meta.getOrCreateAndroidAttribute(valueAttr, valueId))
    }

    private fun findMeta(parent: ResXmlElement, name: String) =
        parent.listElements("meta-data").firstOrNull { nameOf(it) == name }

    private fun isLauncherFilter(filter: ResXmlElement): Boolean {
        val actions = filter.listElements("action").mapNotNull { nameOf(it) }
        val cats = filter.listElements("category").mapNotNull { nameOf(it) }
        return "android.intent.action.MAIN" in actions && "android.intent.category.LAUNCHER" in cats
    }

    private fun nameOf(el: ResXmlElement) = el.searchAttributeByResourceId(ID_NAME)?.stringOrNull()

    private fun ResXmlAttribute.stringOrNull(): String? =
        if (valueType == ValueType.STRING) valueAsString else null

    companion object {
        // android.R.attr ids, verified against android-36 android.jar
        private const val ID_NAME = 0x01010003
        private const val ID_SHARED_USER_ID = 0x0101000b
        private const val ID_PERMISSION = 0x01010006
        private const val ID_READ_PERMISSION = 0x01010007
        private const val ID_WRITE_PERMISSION = 0x01010008
        private const val ID_AUTHORITIES = 0x01010018
        private const val ID_VALUE = 0x01010024
        private const val ID_RESOURCE = 0x01010025
        private const val ID_TARGET_ACTIVITY = 0x01010202
        private const val ID_BACKUP_AGENT = 0x0101027f
        private const val ID_RESIZEABLE = 0x010104f6
        private const val ID_APP_CATEGORY = 0x01010545
        private const val APP_CATEGORY_GAME = 0

        private const val CAR_APP_META = "com.google.android.gms.car.application"
        private const val DESCRIPTOR_NAME = "carbridge_automotive_app_desc"

        private val CLASS_NAME_ATTRS = listOf(ID_NAME, ID_TARGET_ACTIVITY, ID_BACKUP_AGENT)
        private val PERMISSION_ATTRS = listOf(ID_NAME, ID_PERMISSION, ID_READ_PERMISSION, ID_WRITE_PERMISSION)

        private val CAR_PERMISSIONS = listOf("androidx.car.app.ACCESS_SURFACE", "androidx.car.app.MAP_TEMPLATES")
        private val CAR_CATEGORIES = setOf(
            "android.intent.category.CAR_LAUNCHER",
            "androidx.car.app.category.NAVIGATION",
            "android.intent.category.APP_MAPS",
        )
        private val TEMPLATE_SERVICE_ACTIONS = setOf(
            "android.media.browse.MediaBrowserService",
            "androidx.media3.session.MediaLibraryService",
            "androidx.car.app.CarAppService",
        )

        /** Swaps the old package prefix in each `;`-separated part, prefixing parts without it. */
        fun renameValue(value: String, old: String, new: String): String =
            value.split(";").joinToString(";") { part ->
                when {
                    part == old || part.startsWith("$old.") -> new + part.removePrefix(old)
                    else -> "$new.$part"
                }
            }
    }
}
