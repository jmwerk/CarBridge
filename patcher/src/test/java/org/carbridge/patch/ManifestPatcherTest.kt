package org.carbridge.patch

import org.carbridge.core.CarOptions

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

    /**
     * Patches a real APK, or a directory holding base.apk plus split_*.apk, so the output can be
     * checked with aapt2. Skipped without -PpatchFixture.
     */
    @Test
    fun patchesFixture() {
        val fixture = System.getProperty("patchFixture")?.let(::File)
        assumeTrue("no -PpatchFixture given", fixture != null)
        val outDir = File(System.getProperty("patchOut")).parentFile.apply { deleteRecursively(); mkdirs() }

        val base = if (fixture!!.isDirectory) File(fixture, "base.apk") else fixture
        val splits = if (fixture.isDirectory) fixture.listFiles { f -> f.name.startsWith("split_") }!!.toList() else emptyList()
        val patcher = ManifestPatcher(::println)

        val newPackage = ApkModule.loadApkFile(base).use { it.androidManifest.packageName + ".car" }
        ApkModule.loadApkFile(base).use { apk ->
            // Placeholder layers: aapt2 checks the resource wiring, not the PNG pixels
            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
            patcher.patch(apk, newPackage, ManifestPatcher.Branding("Fixture (Car)", png, png), CarOptions(fillScreen = true))
            apk.writeApk(File(outDir, "base.apk"))
        }
        for (split in splits) {
            ApkModule.loadApkFile(split).use { apk ->
                patcher.patchSplit(apk, newPackage)
                apk.writeApk(File(outDir, split.name))
            }
        }
    }
}
