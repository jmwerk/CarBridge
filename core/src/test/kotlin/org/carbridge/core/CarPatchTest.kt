package org.carbridge.core

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Runs the shared rules on decoded-XML manifests, the form CarBridge Patches sees in Morphe. */
class CarPatchTest {

    @Test
    fun addsCarEntriesToLauncher() {
        val host = host(manifest())
        CarPatch.apply(host)

        val app = host.application
        assertEquals("game", app.getString(CarPatch.APP_CATEGORY))
        assertEquals(listOf(CarPatch.DESCRIPTOR_NAME), host.descriptors)
        assertEquals("@xml/${CarPatch.DESCRIPTOR_NAME}", meta(app, CarPatch.CAR_APP_META)?.getString(CarPatch.RESOURCE))
        assertEquals("7", meta(app, "androidx.car.app.minCarApiLevel")?.getString(CarPatch.VALUE))

        val main = app.children("activity").single { it.getString(CarPatch.NAME) == ".Main" }
        val categories = main.children("intent-filter").single().children("category").map { it.getString(CarPatch.NAME) }
        assertTrue(categories.containsAll(CarPatch.CAR_CATEGORIES))
        assertEquals("true", main.getString(CarPatch.RESIZEABLE))
        assertEquals("true", meta(main, "distractionOptimized")?.getString(CarPatch.VALUE))

        // Permissions must precede <application>
        val order = elements(host.doc.documentElement).map { it.tagName + ":" + it.getAttribute("android:name") }
        for (perm in CarPatch.CAR_PERMISSIONS) assertTrue(order.indexOf("uses-permission:$perm") < order.indexOf("application:"))
    }

    @Test
    fun leavesOtherActivitiesAlone() {
        val host = host(manifest())
        CarPatch.apply(host)
        val other = host.application.children("activity").single { it.getString(CarPatch.NAME) == ".Settings" }
        assertNull(other.getString(CarPatch.RESIZEABLE))
        assertTrue(other.children("meta-data").isEmpty())
    }

    @Test
    fun isIdempotent() {
        val host = host(manifest())
        CarPatch.apply(host)
        CarPatch.apply(host)
        assertEquals(1, host.application.children("meta-data").count { it.getString(CarPatch.NAME) == CarPatch.CAR_APP_META })
        val perms = host.manifest.children("uses-permission").map { it.getString(CarPatch.NAME) }
        assertEquals(perms.size, perms.toSet().size)
        val main = host.application.children("activity").single { it.getString(CarPatch.NAME) == ".Main" }
        val categories = main.children("intent-filter").single().children("category").map { it.getString(CarPatch.NAME) }
        assertEquals(categories.size, categories.toSet().size)
    }

    @Test
    fun stripsMediaAndCarAppServicesByDefault() {
        val host = host(manifest())
        CarPatch.apply(host)
        assertTrue(serviceFilters(host, ".Media").isEmpty())
        assertTrue(serviceFilters(host, ".CarApp").isEmpty())
        assertEquals(1, serviceFilters(host, ".Sync").size)
    }

    @Test
    fun keepMediaServicesStillStripsCarAppService() {
        val host = host(manifest())
        CarPatch.apply(host, CarOptions(keepMediaServices = true))
        assertEquals(1, serviceFilters(host, ".Media").size)
        assertTrue(serviceFilters(host, ".CarApp").isEmpty())
    }

    @Test
    fun fillScreenRemovesOnlyPortraitLocks() {
        val host = host(manifest())
        CarPatch.apply(host, CarOptions(fillScreen = true))
        assertNull(orientation(host, ".Main"))
        assertNull(orientation(host, ".Numeric"))
        assertEquals("landscape", orientation(host, ".Settings"))
    }

    @Test
    fun keepPortraitLeavesLocks() {
        val host = host(manifest())
        CarPatch.apply(host, CarOptions(fillScreen = false))
        assertEquals("portrait", orientation(host, ".Main"))
        assertEquals("7", orientation(host, ".Numeric"))
    }

    @Test(expected = IllegalStateException::class)
    fun failsWithoutLauncher() {
        CarPatch.apply(host(manifest().replace("android.intent.category.LAUNCHER", "android.intent.category.DEFAULT")))
    }

    @Test
    fun existingCarAppMetaIsKept() {
        val xml = manifest().replace(
            "<application>",
            "<application><meta-data android:name=\"${CarPatch.CAR_APP_META}\" android:resource=\"@xml/own\"/>",
        )
        val host = host(xml)
        CarPatch.apply(host)
        assertTrue(host.descriptors.isEmpty())
        assertEquals("@xml/own", meta(host.application, CarPatch.CAR_APP_META)?.getString(CarPatch.RESOURCE))
        assertFalse(host.application.children("meta-data").count { it.getString(CarPatch.NAME) == CarPatch.CAR_APP_META } > 1)
    }

    private fun manifest() = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="a.b">
          <uses-permission android:name="android.permission.INTERNET"/>
          <application>
            <activity android:name=".Main" android:screenOrientation="portrait">
              <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
              </intent-filter>
            </activity>
            <activity android:name=".Settings" android:screenOrientation="landscape"/>
            <activity android:name=".Numeric" android:screenOrientation="7"/>
            <service android:name=".Media">
              <intent-filter><action android:name="android.media.browse.MediaBrowserService"/></intent-filter>
            </service>
            <service android:name=".CarApp">
              <intent-filter><action android:name="androidx.car.app.CarAppService"/></intent-filter>
            </service>
            <service android:name=".Sync">
              <intent-filter><action android:name="android.content.SyncAdapter"/></intent-filter>
            </service>
          </application>
        </manifest>
    """.trimIndent()

    private fun host(xml: String): DomHost {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        return DomHost(doc)
    }

    private fun meta(parent: ManifestElement, name: String) =
        parent.children("meta-data").firstOrNull { it.getString(CarPatch.NAME) == name }

    private fun serviceFilters(host: DomHost, name: String) =
        host.application.children("service").single { it.getString(CarPatch.NAME) == name }.children("intent-filter")

    private fun orientation(host: DomHost, name: String) =
        host.application.children("activity").single { it.getString(CarPatch.NAME) == name }
            .getString(CarPatch.SCREEN_ORIENTATION)

    private fun elements(parent: Element): List<Element> {
        val nodes = parent.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    /** Same shape as the DOM adapter in CarBridge Patches; descriptors are only recorded. */
    private class DomHost(val doc: Document) : ManifestHost {
        val descriptors = mutableListOf<String>()
        override val manifest = DomElement(doc.documentElement)
        override val application = manifest.children("application").single()

        override fun hasPermission(name: String) =
            manifest.children("uses-permission").any { it.getString(CarPatch.NAME) == name }

        override fun addPermission(name: String) {
            val el = doc.createElement("uses-permission")
            el.setAttribute("android:name", name)
            doc.documentElement.insertBefore(el, (application as DomElement).el)
        }

        override fun addAutomotiveDescriptor(name: String, uses: String) {
            descriptors += name
        }

        override fun log(message: String) = Unit
    }

    private class DomElement(val el: Element) : ManifestElement {
        override val tag: String get() = el.tagName
        override fun children(tag: String): List<ManifestElement> {
            val nodes = el.childNodes
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { it.tagName == tag }.map(::DomElement)
        }

        override fun getString(attr: AndroidAttr) = el.getAttribute("android:${attr.name}").ifEmpty { null }
        override fun getInt(attr: AndroidAttr): Int? = getString(attr)?.let { it.toIntOrNull() ?: attr.enumValues[it] }
        override fun setString(attr: AndroidAttr, value: String) = el.setAttribute("android:${attr.name}", value)
        override fun setBoolean(attr: AndroidAttr, value: Boolean) = setString(attr, value.toString())
        override fun setInt(attr: AndroidAttr, value: Int) = setString(attr, value.toString())
        override fun setEnum(attr: AndroidAttr, value: String) = setString(attr, value)
        override fun setResource(attr: AndroidAttr, type: String, name: String) = setString(attr, "@$type/$name")
        override fun remove(attr: AndroidAttr) = el.removeAttribute("android:${attr.name}")
        override fun append(tag: String): ManifestElement = DomElement(el.ownerDocument.createElement(tag).also(el::appendChild))
        override fun removeChild(child: ManifestElement) {
            el.removeChild((child as DomElement).el)
        }
    }
}
