package org.carbridge.patch

import android.content.Context
import android.content.pm.ApplicationInfo
import com.reandroid.apk.ApkModule
import com.reandroid.archive.writer.ZipAligner
import java.io.File

/** Original APK in, signed car-enabled APK out. Runs off the main thread. */
class PatchPipeline(private val context: Context, private val log: (String) -> Unit) {

    fun patch(app: ApplicationInfo): File {
        require(app.splitSourceDirs.isNullOrEmpty()) { "split APKs aren't supported yet" }

        val work = File(context.cacheDir, "patch").apply { deleteRecursively(); mkdirs() }
        val unsigned = File(work, "unsigned.apk")
        val signed = File(work, "${app.packageName}.car.apk")

        log("Reading ${File(app.sourceDir).length() / 1024 / 1024} MB APK")
        val minSdk: Int
        ApkModule.loadApkFile(File(app.sourceDir)).use { apk ->
            apk.setApkSignatureBlock(null)
            apk.zipEntryMap.removeIf(V1_SIGNATURE_FILES)
            ManifestPatcher(log).patch(apk, carPackageName(app.packageName))
            minSdk = apk.androidManifest.minSdkVersion ?: 1

            log("Writing patched APK")
            val writer = apk.createApkFileWriter(unsigned)
            writer.zipAligner = ZipAligner().apply {
                setDefaultAlignment(4)
                // Uncompressed native libs must be page-aligned; 16 KB covers 4 KB devices too
                setFileAlignment(ZipAligner.PREDICATE_NATIVE_LIBS, 16 * 1024)
            }
            writer.write()
        }

        log("Signing")
        KeystoreSigner().sign(unsigned, signed, minSdk)
        unsigned.delete()
        return signed
    }

    companion object {
        private val V1_SIGNATURE_FILES =
            Regex("META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC))", RegexOption.IGNORE_CASE).toPattern()

        fun carPackageName(original: String) = "$original.car"
    }
}
