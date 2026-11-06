package org.carbridge

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.carbridge.patch.PatchPipeline

private const val TAG = "CarBridge"

data class PatchableApp(val label: String, val info: ApplicationInfo, val patched: Boolean) {
    val apkCount get() = 1 + (info.splitSourceDirs?.size ?: 0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                CarBridgeScreen()
            }
        }
    }

    private fun loadApps(): List<PatchableApp> {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = pm.getInstalledPackages(0).map { it.packageName }.toSet()
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { it.packageName != packageName && !it.packageName.endsWith(".car") }
            .map { PatchableApp(it.loadLabel(pm).toString(), it, PatchPipeline.carPackageName(it.packageName) in installed) }
            .sortedBy { it.label.lowercase() }
    }

    @Composable
    private fun CarBridgeScreen() {
        val scope = rememberCoroutineScope()
        val log = remember { mutableStateListOf<String>() }
        var apps by remember { mutableStateOf(emptyList<PatchableApp>()) }
        var busy by remember { mutableStateOf(false) }

        fun append(line: String) {
            Log.i(TAG, line)
            scope.launch(Dispatchers.Main) { log.add(line) }
        }

        LaunchedEffect(Unit) {
            apps = withContext(Dispatchers.IO) { loadApps() }
            Installer.events.collect {
                append(it)
                apps = withContext(Dispatchers.IO) { loadApps() }
            }
        }

        fun patch(app: PatchableApp) {
            if (!packageManager.canRequestPackageInstalls()) {
                append("Allow CarBridge to install apps, then tap again.")
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                return
            }
            busy = true
            log.clear()
            scope.launch {
                try {
                    val apks = withContext(Dispatchers.IO) { PatchPipeline(this@MainActivity, ::append).patch(app.info) }
                    append("Installing ${PatchPipeline.carPackageName(app.info.packageName)}")
                    withContext(Dispatchers.IO) {
                        Installer.install(this@MainActivity, apks, PatchPipeline.carPackageName(app.info.packageName))
                    }
                } catch (e: Throwable) {
                    // Throwable: apksig/ARSCLib can throw Errors (e.g. missing JDK classes on Android)
                    Log.e(TAG, "patch failed", e)
                    append("Failed: ${e.javaClass.simpleName}: ${e.message}")
                } finally {
                    busy = false
                }
            }
        }

        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("CarBridge", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Patches a copy of an app so Android Auto shows it while parked. " +
                        "Only patch apps whose license allows modification, and never share patched APKs.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (log.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 160.dp).padding(vertical = 8.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(apps, key = { it.info.packageName }) { app ->
                        AppRow(app, enabled = !busy) { patch(app) }
                    }
                }
            }
        }
    }

    @Composable
    private fun AppRow(app: PatchableApp, enabled: Boolean, onPatch: () -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.label)
                val note = when {
                    app.patched -> "Car copy installed"
                    app.apkCount > 1 -> "${app.info.packageName} · ${app.apkCount} APKs"
                    else -> app.info.packageName
                }
                Text(note, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onPatch, enabled = enabled) {
                Text(if (app.patched) "Re-patch" else "Patch")
            }
        }
    }
}
