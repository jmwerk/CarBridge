package org.carbridge

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
import androidx.lifecycle.ViewModelProvider
import org.carbridge.ui.CarBridgeTheme
import org.carbridge.ui.HomeActions
import org.carbridge.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[HomeModel::class.java] }

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
            add = { model.add(it, update = false) },
            update = { copy -> copy.original?.let { model.add(it, update = true) } },
            setFillScreen = model::setFillScreen,
            remove = { copy -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${copy.packageName}"))) },
        )
        setContent {
            CarBridgeTheme {
                HomeScreen(model, actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Settings, uninstall and install prompts all return here
        model.refresh()
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
    }
}
