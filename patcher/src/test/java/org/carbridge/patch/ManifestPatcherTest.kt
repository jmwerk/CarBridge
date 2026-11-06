package org.carbridge.patch

import com.reandroid.apk.ApkModule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class ManifestPatcherTest {

    @Test
    fun renameValueSwapsPrefixAndPrefixesForeignParts() {
        assertEquals("a.b.car.provider", ManifestPatcher.renameValue("a.b.provider", "a.b", "a.b.car"))
        assertEquals("a.b.car.x;a.b.car.y", ManifestPatcher.renameValue("a.b.x;y", "a.b", "a.b.car"))
        assertEquals("a.b.car", ManifestPatcher.renameValue("a.b", "a.b", "a.b.car"))
    }

    /** Patches a real APK so the output can be checked with aapt2; skipped without a fixture. */
    @Test
    fun patchesFixtureApk() {
        val fixture = System.getProperty("patchFixture")
        assumeTrue("no -PpatchFixture given", fixture != null)
        val out = File(System.getProperty("patchOut")).apply { parentFile.mkdirs() }
        ApkModule.loadApkFile(File(fixture!!)).use { apk ->
            ManifestPatcher(::println).patch(apk, apk.androidManifest.packageName + ".car")
            apk.writeApk(out)
        }
    }
}
