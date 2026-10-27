package org.wayfarer.atlas.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.wayfarer.atlas.MainActivity
import org.wayfarer.atlas.R
import org.wayfarer.atlas.data.DatasetParser
import org.wayfarer.atlas.storage.TripStore
import java.time.LocalDate
import java.util.Locale

class TripReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("tripId") ?: return Result.failure()
        val trip = try { TripStore(applicationContext.filesDir).load().find { it.id == id } }
        catch (_: java.io.IOException) { return Result.failure() }
        if (trip == null || !trip.isReminderEnabled || LocalDate.parse(trip.startDate) != LocalDate.now()) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val preferences = applicationContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val language = preferences.getString("language", null) ?: Locale.getDefault().language
        val localized = applicationContext.createConfigurationContext(Configuration(applicationContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(if (language == "ru") "ru" else "en"))
        })
        val countryName = try {
            val countries = applicationContext.assets.open("countries.json").bufferedReader().use { DatasetParser.parseCountries(it.readText()) }
            countries.find { it.code == trip.destination }?.let { if (language == "ru") it.nameRu else it.nameEn } ?: trip.destination
        } catch (_: Exception) { trip.destination }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, localized.getString(R.string.trip_reminders_channel), NotificationManager.IMPORTANCE_DEFAULT))
        if (!manager.areNotificationsEnabled()) return Result.success()
        val intent = Intent(applicationContext, MainActivity::class.java)
            .setData(Uri.parse("wayfarer://trips/" + Uri.encode(id)))
            .putExtra("openTrips", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(localized, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized.getString(R.string.trip_reminder_title))
            .setContentText(localized.getString(R.string.trip_reminder_body, countryName))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        manager.notify(id, 0, notification)
        return Result.success()
    }
    companion object { private const val CHANNEL = "trip-reminders" }
}
