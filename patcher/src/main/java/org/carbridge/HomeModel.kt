package org.carbridge

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.carbridge.patch.PatchPipeline

private const val TAG = "CarBridge"

/** A car copy being made; [step] indexes [JobStep]. */
data class Job(val label: String, val icon: ImageBitmap, val packageName: String, val update: Boolean, val step: Int)

enum class JobStep(val text: String) {
    Read("Reading the app"),
    Patch("Making the car copy"),
    Sign("Signing it on this phone"),
    Confirm("Confirm the install in Android's prompt"),
}

data class Setup(val canInstall: Boolean, val developerMode: Boolean, val unknownSources: Boolean) {
    val complete get() = canInstall && developerMode && unknownSources
}

data class UserMessage(val text: String, val details: String? = null)

class HomeModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("setup", 0)

    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var job by mutableStateOf<Job?>(null)
        private set
    var setup by mutableStateOf(readSetup())
        private set
    var showAutoApps by mutableStateOf(prefs.getBoolean("show_auto_apps", false))
        private set

    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private val log = mutableListOf<String>()

    init {
        viewModelScope.launch {
            Installer.events.collect { result ->
                val current = job
                if (current != null && (result.packageName ?: current.packageName) == current.packageName) {
                    job = null
                    _messages.send(
                        when {
                            result.success -> UserMessage(
                                "${current.label} is on your car. Reconnect your phone to Android Auto to see it.",
                            )
                            result.cancelled -> UserMessage("Install cancelled.")
                            else -> UserMessage("Couldn't add ${current.label}.", details(result.message))
                        },
                    )
                }
                refresh()
            }
        }
    }

    fun refresh() {
        setup = readSetup()
        viewModelScope.launch {
            catalog = withContext(Dispatchers.IO) { AppCatalog.load(getApplication()) }
        }
    }

    fun toggleAutoApps() {
        showAutoApps = !showAutoApps
        prefs.edit().putBoolean("show_auto_apps", showAutoApps).apply()
    }

    fun markDeveloperMode() = mark("developer_mode")
    fun markUnknownSources() = mark("unknown_sources")

    private fun mark(key: String) {
        prefs.edit().putBoolean(key, true).apply()
        setup = readSetup()
    }

    private fun readSetup() = Setup(
        canInstall = getApplication<Application>().packageManager.canRequestPackageInstalls(),
        developerMode = prefs.getBoolean("developer_mode", false),
        unknownSources = prefs.getBoolean("unknown_sources", false),
    )

    fun add(source: SourceApp, update: Boolean) {
        if (job != null) return
        val context = getApplication<Application>()
        val carPackage = PatchPipeline.carPackageName(source.info.packageName)
        log.clear()
        job = Job(source.label, source.icon, carPackage, update, JobStep.Read.ordinal)
        viewModelScope.launch {
            try {
                val apks = withContext(Dispatchers.IO) {
                    PatchPipeline(context) { line -> onLog(line) }.patch(source.info)
                }
                job = job?.copy(step = JobStep.Confirm.ordinal)
                withContext(Dispatchers.IO) { Installer.install(context, apks, carPackage) }
            } catch (e: Throwable) {
                // Throwable: apksig/ARSCLib can throw Errors (e.g. missing JDK classes on Android)
                Log.e(TAG, "patch failed", e)
                job = null
                _messages.send(UserMessage("Couldn't add ${source.label}.", details("${e.javaClass.simpleName}: ${e.message}")))
            }
        }
    }

    private fun onLog(line: String) {
        Log.i(TAG, line)
        synchronized(log) { log.add(line) }
        val step = when {
            line.startsWith("Signing") -> JobStep.Sign
            line.startsWith("Car-enabled") || line.startsWith("Renamed") -> JobStep.Patch
            else -> null
        } ?: return
        viewModelScope.launch { job = job?.copy(step = maxOf(job?.step ?: 0, step.ordinal)) }
    }

    private fun details(error: String?) = synchronized(log) { (log + listOfNotNull(error)).joinToString("\n") }
}
