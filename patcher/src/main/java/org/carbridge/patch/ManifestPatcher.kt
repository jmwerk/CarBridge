package org.carbridge.patch

import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.value.ValueType
import org.carbridge.core.AndroidAttr
import org.carbridge.core.CarOptions
import org.carbridge.core.CarPatch
import org.carbridge.core.ManifestElement
import org.carbridge.core.ManifestHost

/**
 * Applies the shared [CarPatch] rules to binary XML with ARSCLib, then the copy-only steps:
 * branding and the package rename that lets the copy install next to the original.
 */
class ManifestPatcher(private val log: (String) -> Unit) {

    /** How the car copy presents itself; icon layers are PNGs for an adaptive icon. */
    class Branding(val label: String, val iconBackground: ByteArray?, val iconForeground: ByteArray?)

    fun patch(apk: ApkModule, newPackage: String?, branding: Branding? = null, options: CarOptions = CarOptions()) {
        val manifest = apk.androidManifest ?: error("APK has no AndroidManifest.xml")
        val app = manifest.applicationElement ?: error("manifest has no <application>")

        CarPatch.apply(ArscHost(apk, manifest, app), options)
        if (branding != null) applyBranding(apk, app, branding)
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

    private inner class ArscHost(
        private val apk: ApkModule,
        private val block: AndroidManifestBlock,
        app: ResXmlElement,
    ) : ManifestHost {
        private val pkg: PackageBlock by lazy {
            apk.getTableBlock(false)?.pickOne() ?: error("APK has no resources.arsc")
        }
        override val manifest = ArscElement(block.manifestElement ?: error("manifest has no <manifest>")) { pkg }
        override val application = ArscElement(app) { pkg }

        override fun hasPermission(name: String) = block.getUsesPermission(name) != null
        override fun addPermission(name: String) {
            block.addUsesPermission(name)
        }

        override fun addAutomotiveDescriptor(name: String, uses: String) {
            val path = "res/xml/$name.xml"
            pkg.getOrCreate("", "xml", name).setValueAsString(path)
            val doc = ResXmlDocument()
            doc.newElement("automotiveApp").newElement("uses").getOrCreateAttribute("name", 0).setValueAsString(uses)
            doc.refreshFull()
            apk.add(ByteInputSource(doc.bytes, path))
        }

        override fun log(message: String) = this@ManifestPatcher.log(message)
    }

    /** [ManifestElement] over ARSCLib; resource references resolve in the APK's own table. */
    private class ArscElement(val el: ResXmlElement, private val pkg: () -> PackageBlock) : ManifestElement {
        override val tag: String get() = el.name

        override fun children(tag: String) = el.listElements(tag).map { ArscElement(it, pkg) }
        override fun getString(attr: AndroidAttr) = el.searchAttributeByResourceId(attr.id)?.stringOrNull()
        override fun getInt(attr: AndroidAttr): Int? {
            val a = el.searchAttributeByResourceId(attr.id) ?: return null
            return if (a.valueType == ValueType.DEC || a.valueType == ValueType.HEX) a.data else null
        }

        override fun setString(attr: AndroidAttr, value: String) = set(attr).setValueAsString(value)
        override fun setBoolean(attr: AndroidAttr, value: Boolean) = set(attr).setValueAsBoolean(value)
        override fun setInt(attr: AndroidAttr, value: Int) = set(attr).setValueAsDecimal(value)
        override fun setEnum(attr: AndroidAttr, value: String) =
            set(attr).setValueAsDecimal(attr.enumValues[value] ?: error("unknown ${attr.name} value $value"))

        override fun setResource(attr: AndroidAttr, type: String, name: String) =
            set(attr).setValueAsResourceId(pkg().getOrCreate("", type, name).resourceId)

        override fun remove(attr: AndroidAttr) {
            el.searchAttributeByResourceId(attr.id)?.let(el::removeAttribute)
        }

        override fun append(tag: String): ManifestElement = ArscElement(el.newElement(tag), pkg)
        override fun removeChild(child: ManifestElement) {
            (child as ArscElement).el.removeSelf()
        }

        private fun set(attr: AndroidAttr) = el.getOrCreateAndroidAttribute(attr.name, attr.id)
    }

    /** Gives the copy its own label and icon so it can't be mistaken for the original. */
    private fun applyBranding(apk: ApkModule, app: ResXmlElement, branding: Branding) {
        val launchers = launcherActivities(app)
        for (el in listOf(app) + launchers) {
            // Activities without a label inherit the application's
            if (el === app || el.searchAttributeByResourceId(ID_LABEL) != null) {
                el.getOrCreateAndroidAttribute("label", ID_LABEL).setValueAsString(branding.label)
            }
        }
        log("Label: ${branding.label}")

        val bg = branding.iconBackground ?: return
        val fg = branding.iconForeground ?: return
        val pkg = apk.getTableBlock(false)?.pickOne() ?: error("APK has no resources.arsc")
        val bgId = addFile(apk, pkg, "nodpi", "drawable", "carbridge_icon_bg", "png", bg)
        val fgId = addFile(apk, pkg, "nodpi", "drawable", "carbridge_icon_fg", "png", fg)

        val doc = ResXmlDocument()
        val root = doc.newElement("adaptive-icon")
        root.newNamespace(ANDROID_NS, "android")
        root.newElement("background").getOrCreateAndroidAttribute("drawable", ID_DRAWABLE).setValueAsResourceId(bgId)
        root.newElement("foreground").getOrCreateAndroidAttribute("drawable", ID_DRAWABLE).setValueAsResourceId(fgId)
        doc.refreshFull()
        val iconId = addFile(apk, pkg, "anydpi-v26", "mipmap", "carbridge_icon", "xml", doc.bytes)

        for (el in listOf(app) + launchers) {
            for ((name, id) in listOf("icon" to ID_ICON, "roundIcon" to ID_ROUND_ICON)) {
                if (el === app || el.searchAttributeByResourceId(id) != null) {
                    el.getOrCreateAndroidAttribute(name, id).setValueAsResourceId(iconId)
                }
            }
        }
        log("Icon: badged")
    }

    private fun addFile(
        apk: ApkModule,
        pkg: com.reandroid.arsc.chunk.PackageBlock,
        qualifiers: String,
        type: String,
        name: String,
        ext: String,
        bytes: ByteArray,
    ): Int {
        val path = "res/$type-$qualifiers/$name.$ext"
        val entry = pkg.getOrCreate(qualifiers, type, name)
        entry.setValueAsString(path)
        apk.add(ByteInputSource(bytes, path))
        return entry.resourceId
    }

    private fun launcherActivities(app: ResXmlElement) =
        CarPatch.launcherActivities(ArscElement(app) { error("no table needed") }).map { (it as ArscElement).el }

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

    private fun nameOf(el: ResXmlElement) = el.searchAttributeByResourceId(ID_NAME)?.stringOrNull()

    companion object {
        // android.R.attr ids, verified against android-36 android.jar
        private const val ID_LABEL = 0x01010001
        private const val ID_ICON = 0x01010002
        private const val ID_NAME = 0x01010003
        private const val ID_SHARED_USER_ID = 0x0101000b
        private const val ID_PERMISSION = 0x01010006
        private const val ID_READ_PERMISSION = 0x01010007
        private const val ID_WRITE_PERMISSION = 0x01010008
        private const val ID_AUTHORITIES = 0x01010018
        private const val ID_TARGET_ACTIVITY = 0x01010202
        private const val ID_BACKUP_AGENT = 0x0101027f
        private const val ID_DRAWABLE = 0x01010199
        private const val ID_ROUND_ICON = 0x0101052c

        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        private val CLASS_NAME_ATTRS = listOf(ID_NAME, ID_TARGET_ACTIVITY, ID_BACKUP_AGENT)
        private val PERMISSION_ATTRS = listOf(ID_NAME, ID_PERMISSION, ID_READ_PERMISSION, ID_WRITE_PERMISSION)

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

private fun ResXmlAttribute.stringOrNull(): String? =
    if (valueType == ValueType.STRING) valueAsString else null
