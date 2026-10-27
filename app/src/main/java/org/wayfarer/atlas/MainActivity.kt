package org.wayfarer.atlas

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.wayfarer.atlas.domain.Trip
import org.wayfarer.atlas.ui.AtlasApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboarding = getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        setContent {
            val model: AtlasViewModel = viewModel()
            val state by model.ui.collectAsStateWithLifecycle()
            var disclaimerAcknowledged by remember {
                mutableStateOf(onboarding.getBoolean("travel_disclaimer_acknowledged", false))
            }
            val tripSaver = remember {
                Saver<Trip?, ArrayList<String>>(
                    save = { trip -> trip?.let { arrayListOf(it.id, it.destination, it.startDate, it.endDate, it.note, it.isReminderEnabled.toString()) } ?: arrayListOf() },
                    restore = { values -> if (values.isEmpty()) null else Trip(values[0], values[1], values[2], values[3], values[4], values[5].toBoolean()) }
                )
            }
            var pendingTrip by rememberSaveable(stateSaver = tripSaver) { mutableStateOf<Trip?>(null) }
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                pendingTrip?.let { trip ->
                    model.saveTrip(Trip(trip.id, trip.destination, trip.startDate, trip.endDate, trip.note, granted))
                    if (!granted) {
                        Toast.makeText(
                            this,
                            if (state.language == "ru") "Доступ к уведомлениям не разрешён. Напоминание для этой поездки отключено."
                            else "Notifications were not allowed. The reminder for this trip is off.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                pendingTrip = null
            }
            AtlasApp(
                state = state,
                initialTab = if (intent?.getBooleanExtra("openTrips", false) == true) 3 else 0,
                onCitizenship = model::setCitizenship,
                onLanguage = model::setLanguage,
                onRefresh = model::refresh,
                onSaveTrip = { trip ->
                    if (trip.isReminderEnabled && Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingTrip = trip
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else model.saveTrip(trip)
                },
                onDeleteTrip = model::deleteTrip,
                onClearTrips = model::clearTrips,
                onDismissError = model::dismissError,
                disclaimerRequired = !disclaimerAcknowledged,
                onAcknowledgeDisclaimer = {
                    disclaimerAcknowledged = onboarding.edit().putBoolean("travel_disclaimer_acknowledged", true).commit()
                }
            )
        }
    }
}
