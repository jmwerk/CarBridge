package org.carbridge

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider
import org.carbridge.ui.CarBridgeTheme
import org.carbridge.ui.HomeActions
import org.carbridge.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[HomeModel::class.java] }
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // The header is always guide-sign green, so status bar icons stay light in both themes
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val actions = HomeActions(
            allowInstalls = {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            },
            openAndroidAuto = ::openAndroidAuto,
            markDeveloperMode = model::markDeveloperMode,
            markUnknownSources = model::markUnknownSources,
            add = model::requestAdd,
            update = { copy -> copy.original?.let { model.add(it, update = true) } },
            updateAll = model::updateAll,
            openMorphe = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MORPHE_SOURCE))) },
            setFillScreen = model::setFillScreen,
            setKeepMedia = model::setKeepMedia,
            remove = { copy -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${copy.packageName}"))) },
        )
        UpdateCheck.schedule(this)
        if (savedInstanceState == null) handleUpdateIntent(intent)
        lifecycleScope.launch {
            model.notificationPrompt.collect { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        setContent {
            CarBridgeTheme {
                HomeScreen(model, actions)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent) {
        if (intent.action != UpdateCheck.ACTION_UPDATE) return
        model.updateFromNotification(intent.getStringExtra(UpdateCheck.EXTRA_PACKAGE))
        UpdateCheck.dismiss(this)
    }

    override fun onResume() {
        super.onResume()
        // Settings, uninstall and install prompts all return here
        model.refresh()
        UpdateCheck.dismissIfResolved(this)
    }

    private fun openAndroidAuto() {
        val settings = Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(ANDROID_AUTO)
        try {
            startActivity(settings)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$ANDROID_AUTO")))
        }
    }

    private companion object {
        const val ANDROID_AUTO = "com.google.android.projection.gearhead"
        const val MORPHE_SOURCE = "https://morphe.software/add-source?github=jmwerk/carbridge-patches"
    }
}
