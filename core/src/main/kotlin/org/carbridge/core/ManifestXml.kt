package org.carbridge.core

/** An `android:` attribute, by name (decoded XML) and resource id (binary XML). */
class AndroidAttr(
    val name: String,
    val id: Int,
    /** Symbolic values as decoded XML writes them, mapped to their binary integers. */
    val enumValues: Map<String, Int> = emptyMap(),
)

/**
 * One manifest element. CarBridge implements it over ARSCLib's binary XML, CarBridge Patches
 * over Morphe's decoded DOM, so both apply exactly the same [CarPatch] rules.
 */
interface ManifestElement {
    val tag: String

    fun children(tag: String): List<ManifestElement>
    fun getString(attr: AndroidAttr): String?

    /** Integer or enum value; decoded symbolic values are mapped through [AndroidAttr.enumValues]. */
    fun getInt(attr: AndroidAttr): Int?

    fun setString(attr: AndroidAttr, value: String)
    fun setBoolean(attr: AndroidAttr, value: Boolean)
    fun setInt(attr: AndroidAttr, value: Int)

    /** Sets an enum attribute by its symbolic name, which must be in [AndroidAttr.enumValues]. */
    fun setEnum(attr: AndroidAttr, value: String)

    /** Points the attribute at a resource the [ManifestHost] added, e.g. `@xml/name`. */
    fun setResource(attr: AndroidAttr, type: String, name: String)
    fun remove(attr: AndroidAttr)
    fun append(tag: String): ManifestElement
    fun removeChild(child: ManifestElement)
}

/** The parts of an APK the rules touch beyond single elements. */
interface ManifestHost {
    val manifest: ManifestElement
    val application: ManifestElement

    fun hasPermission(name: String): Boolean

    /** Adds `<uses-permission>`, which must come before `<application>`. */
    fun addPermission(name: String)

    /** Writes `res/xml/<name>.xml` as `<automotiveApp><uses name="[uses]"/></automotiveApp>`. */
    fun addAutomotiveDescriptor(name: String, uses: String)

    fun log(message: String)
}
