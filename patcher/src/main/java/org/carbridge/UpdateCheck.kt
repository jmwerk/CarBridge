package org.carbridge

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.carbridge.patch.PatchPipeline

/**
 * Periodically compares car copies with their originals and notifies once per new original
 * version. A job, not a PACKAGE_REPLACED receiver: Android no longer delivers that to manifests.
 */
object UpdateCheck {
    private const val JOB_ID = 1
    private const val CHANNEL = "copy_updates"
    private const val NOTIFICATION_ID = 1
    const val ACTION_UPDATE = "org.carbridge.action.UPDATE_COPIES"

    /** Original package to update; absent means every outdated copy. */
    const val EXTRA_PACKAGE = "original_package"
    private const val INTERVAL_HOURS = 6L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckService::class.java))
            .setPeriodic(TimeUnit.HOURS.toMillis(INTERVAL_HOURS))
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    data class Outdated(val originalPackage: String, val label: String, val originalCode: Long)

    /** Lightweight version of [AppCatalog.load]: no icons, only copies whose original is newer. */
    fun findOutdated(context: Context): List<Outdated> {
        val pm = context.packageManager
        val suffix = PatchPipeline.carPackageName("")
        return pm.getInstalledPackages(0)
            .filter { it.packageName.endsWith(suffix) }
            .mapNotNull { copy ->
                val originalPkg = copy.packageName.removeSuffix(suffix)
                val original = runCatching { pm.getPackageInfo(originalPkg, 0) }.getOrNull() ?: return@mapNotNull null
                if (original.longVersionCode <= copy.longVersionCode) return@mapNotNull null
                val label = original.applicationInfo?.loadLabel(pm)?.toString() ?: originalPkg
                Outdated(originalPkg, label, original.longVersionCode)
            }
    }

    fun run(context: Context) {
        val prefs = context.getSharedPreferences("setup", 0)
        // Only versions not yet announced, so each original update notifies once
        val outdated = findOutdated(context)
        val fresh = outdated.filter { prefs.getLong("notified:${it.originalPackage}", 0) < it.originalCode }
        if (fresh.isEmpty()) return
        if (notify(context, outdated)) {
            prefs.edit().apply { fresh.forEach { putLong("notified:${it.originalPackage}", it.originalCode) } }.apply()
        }
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun dismissIfResolved(context: Context) {
        if (findOutdated(context).isEmpty()) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
    }

    private fun notify(context: Context, outdated: List<Outdated>): Boolean {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Car copy updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When an app is updated and its car copy should be updated to match"
            },
        )
        val title = if (outdated.size == 1) "${outdated[0].label} was updated" else "${outdated.size} car copies are out of date"
        val text = if (outdated.size == 1) {
            "Update its car copy in CarBridge so they match."
        } else {
            outdated.joinToString { it.label } + ". Update them in CarBridge."
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val updateIntent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_UPDATE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (outdated.size == 1) updateIntent.putExtra(EXTRA_PACKAGE, outdated[0].originalPackage)
        val update = PendingIntent.getActivity(
            context, 1, updateIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val updateLabel = if (outdated.size == 1) "Update" else "Update all"
        val notification = Notification.Builder(context, CHANNEL)
            .addAction(Notification.Action.Builder(null, updateLabel, update).build())
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }
}

class UpdateCheckService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread {
            runCatching { UpdateCheck.run(this) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
