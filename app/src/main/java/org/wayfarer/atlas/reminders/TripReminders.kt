package org.wayfarer.atlas.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Operation
import androidx.work.workDataOf
import org.wayfarer.atlas.domain.Trip
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object TripReminders {
    fun schedule(context: Context, trip: Trip): Operation {
        val date = LocalDate.parse(trip.startDate)
        if (!trip.isReminderEnabled || date.isBefore(LocalDate.now())) {
            return cancel(context, trip.id)
        }
        val now = ZonedDateTime.now()
        val target = date.atTime(LocalTime.of(9, 0)).atZone(now.zone)
        val delay = Duration.between(now, target).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<TripReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("tripId" to trip.id))
            .build()
        return WorkManager.getInstance(context).enqueueUniqueWork("trip-" + trip.id, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, id: String): Operation {
        val operation = WorkManager.getInstance(context).cancelUniqueWork("trip-" + id)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(id, 0)
        return operation
    }
}
