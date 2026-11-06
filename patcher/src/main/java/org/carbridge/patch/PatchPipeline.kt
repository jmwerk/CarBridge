package org.carbridge.patch

import android.content.Context
import android.content.pm.ApplicationInfo
import com.reandroid.apk.ApkModule
import com.reandroid.archive.writer.ZipAligner
import java.io.File

/** Original APK(s) in, signed car-enabled APK(s) out, base first. Runs off the main thread. */
class PatchPipeline(private val context: Context, private val log: (String) -> Unit) {

    fun patch(app: ApplicationInfo): List<File> {
        val work = File(context.cacheDir, "patch").apply { deleteRecursively(); mkdirs() }
        val newPackage = carPackageName(app.packageName)
        val splits = app.splitSourceDirs.orEmpty().map(::File)
        val base = File(app.sourceDir)

        val totalMb = (splits + base).sumOf { it.length() } / 1024 / 1024
        log("Reading $totalMb MB in ${splits.size + 1} APK(s)")

        val patcher = ManifestPatcher(log)
        val unsignedBase = File(work, "base.unsigned.apk")
        val minSdk = rewrite(base, unsignedBase) { apk -> patcher.patch(apk, newPackage) }
        val unsignedSplits = splits.mapIndexed { i, split ->
            File(work, "split$i.unsigned.apk").also { out ->
                rewrite(split, out) { apk -> patcher.patchSplit(apk, newPackage) }
            }
        }

        log("Signing")
        val signer = KeystoreSigner()
        return (listOf(unsignedBase) + unsignedSplits).map { unsigned ->
            File(work, unsigned.name.replace(".unsigned", "")).also { signed ->
                signer.sign(unsigned, signed, minSdk)
                unsigned.delete()
            }
        }
    }

    /** Applies [edit], writes an unsigned APK and returns its minSdk. */
    private fun rewrite(input: File, output: File, edit: (ApkModule) -> Unit): Int =
        ApkModule.loadApkFile(input).use { apk ->
            apk.setApkSignatureBlock(null)
            apk.zipEntryMap.removeIf(V1_SIGNATURE_FILES)
            edit(apk)
            val writer = apk.createApkFileWriter(output)
            writer.zipAligner = ZipAligner().apply {
                setDefaultAlignment(4)
                // Uncompressed native libs must be page-aligned; 16 KB covers 4 KB devices too
                setFileAlignment(ZipAligner.PREDICATE_NATIVE_LIBS, 16 * 1024)
            }
            writer.write()
            apk.androidManifest.minSdkVersion ?: 1
        }

    companion object {
        private val V1_SIGNATURE_FILES =
            Regex("META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC))", RegexOption.IGNORE_CASE).toPattern()

        fun carPackageName(original: String) = "$original.car"
    }
}
