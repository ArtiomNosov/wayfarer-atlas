package org.wayfarer.atlas

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.wayfarer.atlas.data.AtlasReference
import org.wayfarer.atlas.data.AtlasRepository
import org.wayfarer.atlas.data.CountryShape
import org.wayfarer.atlas.data.ReferenceSourceState
import org.wayfarer.atlas.domain.Country
import org.wayfarer.atlas.domain.EntryRule
import org.wayfarer.atlas.domain.Trip
import org.wayfarer.atlas.domain.ReminderChanges
import org.wayfarer.atlas.reminders.TripReminders
import org.wayfarer.atlas.storage.TripStore
import java.util.Locale

data class AtlasUiState(
    val loading: Boolean = true, val refreshing: Boolean = false,
    val countries: List<Country> = emptyList(), val rules: List<EntryRule> = emptyList(),
    val citizenships: List<Country> = emptyList(),
    val shapes: List<CountryShape> = emptyList(), val trips: List<Trip> = emptyList(),
    val language: String = "en", val citizenship: String = "US", val error: String? = null,
    val sourceName: String = "", val sourceUrl: String = "", val updatedAt: String? = null,
    val downloadedAt: String? = null, val sources: List<ReferenceSourceState> = emptyList()
)

class AtlasViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val repository = AtlasRepository(application)
    private val tripStore = TripStore(application.filesDir)
    private val tripMutex = Mutex()
    private val state = MutableStateFlow(AtlasUiState(
        language = preferences.getString("language", null) ?: if (Locale.getDefault().language == "ru") "ru" else "en",
        citizenship = preferences.getString("citizenship", "US") ?: "US"
    ))
    val ui: StateFlow<AtlasUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val reference = withContext(Dispatchers.IO) { repository.load() }
                applyReference(reference)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                state.update { it.copy(error = "reference_load_failed") }
            }
            try {
                tripMutex.withLock {
                    val trips = withContext(Dispatchers.IO) { tripStore.load() }
                    state.update { it.copy(trips = trips) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                state.update { it.copy(error = "journal_load_failed") }
            } finally { state.update { it.copy(loading = false) } }
        }
    }

    fun setCitizenship(code: String) {
        if (state.value.citizenships.none { it.code == code }) return
        preferences.edit().putString("citizenship", code).apply()
        state.update { it.copy(citizenship = code) }
    }

    fun setLanguage(language: String) {
        if (language !in listOf("ru", "en")) return
        preferences.edit().putString("language", language).apply()
        state.update { it.copy(language = language) }
    }

    fun refresh() {
        if (state.value.refreshing || state.value.loading) return
        viewModelScope.launch {
            state.update { it.copy(refreshing = true) }
            try { applyReference(withContext(Dispatchers.IO) { repository.refresh() }) }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                state.update { it.copy(error = "update_failed") }
            }
            finally { state.update { it.copy(refreshing = false) } }
        }
    }

    fun saveTrip(trip: Trip) = mutateTrips { trips ->
        require(state.value.countries.any { it.code == trip.destination })
        trips.filterNot { it.id == trip.id } + trip
    }

    fun deleteTrip(id: String) = mutateTrips { trips -> trips.filterNot { it.id == id } }

    fun clearTrips() {
        viewModelScope.launch {
            tripMutex.withLock {
                try {
                    withContext(NonCancellable + Dispatchers.IO) {
                        tripStore.deleteAll()
                        state.value.trips.forEach { TripReminders.cancel(getApplication(), it.id).result.get() }
                    }
                    state.update { it.copy(trips = emptyList()) }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    state.update { it.copy(error = "delete_failed") }
                }
            }
        }
    }

    fun dismissError() { state.update { it.copy(error = null) } }

    private fun mutateTrips(change: (List<Trip>) -> List<Trip>) {
        viewModelScope.launch {
            tripMutex.withLock {
                try {
                    val old = state.value.trips
                    val updated = change(old).sortedBy { it.startDate }
                    withContext(NonCancellable + Dispatchers.IO) {
                        tripStore.save(updated)
                        ReminderChanges.toCancel(old, updated).forEach { TripReminders.cancel(getApplication(), it).result.get() }
                        ReminderChanges.toSchedule(old, updated).forEach { TripReminders.schedule(getApplication(), it).result.get() }
                    }
                    state.update { it.copy(trips = updated) }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    state.update { it.copy(error = "save_failed") }
                }
            }
        }
    }

    private fun applyReference(reference: AtlasReference) {
        val origins = reference.rules.map { it.citizenship }.toSet()
        val citizenships = reference.countries.filter { it.code in origins }
        val selected = state.value.citizenship.takeIf { current -> citizenships.any { it.code == current } }
            ?: citizenships.firstOrNull()?.code ?: state.value.citizenship
        preferences.edit().putString("citizenship", selected).apply()
        state.update { it.copy(countries = reference.countries, citizenships = citizenships,
            citizenship = selected, rules = reference.rules, shapes = reference.shapes,
            sourceName = reference.sourceName, sourceUrl = reference.sourceUrl, updatedAt = reference.updatedAt,
            downloadedAt = reference.downloadedAt, sources = reference.sources,
            error = if (reference.failedSources.isNotEmpty()) "update_partial" else if (it.error == "update_partial") null else it.error) }
    }
}
