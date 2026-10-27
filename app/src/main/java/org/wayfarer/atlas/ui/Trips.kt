@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.wayfarer.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.wayfarer.atlas.domain.Country
import org.wayfarer.atlas.domain.Trip
import org.wayfarer.atlas.domain.TripStatistics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID

@Composable
internal fun TripsScreen(countries: List<Country>, trips: List<Trip>, language: String, onSave: (Trip) -> Unit, onDelete: (String) -> Unit) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf(false) }
    val editing = trips.find { it.id == editingId }
    var deleting by remember { mutableStateOf<Trip?>(null) }
    val summary = remember(trips) { TripStatistics.summarize(trips) }
    val sorted = remember(trips) { trips.sortedByDescending { it.startDate } }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading(text(language, "Your travel journal", "Ваш журнал поездок"), text(language, "Your plans and memories stay on this device.", "Планы и воспоминания хранятся на этом устройстве.")) }
        item {
            Button(onClick = { editingId = null; editor = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("journal-add")) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(text(language, "Add a trip", "Добавить поездку"))
            }
        }
        if (trips.isEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.testTag("journal-empty")) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text(language, "Every journey starts with a plan", "Каждое путешествие начинается с плана"), style = MaterialTheme.typography.titleLarge)
                    Text(text(language, "Save a destination, dates and a note. Enable a local reminder if you want one.", "Сохраните направление, даты и заметку. При желании включите локальное напоминание."))
                }
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text(language, "${summary.tripCount} trips · ${summary.countriesVisited} destinations", "${summary.tripCount} поездок · ${summary.countriesVisited} направлений"), style = MaterialTheme.typography.titleMedium)
                        Text(text(language, "${summary.distinctTravelDays} unique travel days", "${summary.distinctTravelDays} уникальных дней в поездках"))
                        Text(text(language, "Includes planned trips. Both boundary dates count; overlapping days count once.", "Включая планы. Даты начала и конца учитываются; совпадающие дни считаются один раз."), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(sorted, key = { it.id }) { trip ->
                Card(onClick = { editingId = trip.id; editor = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(countryName(trip.destination, countries, language), style = MaterialTheme.typography.titleLarge)
                            Text("${trip.startDate} — ${trip.endDate}", style = MaterialTheme.typography.bodyMedium)
                            Text(text(language, "${trip.inclusiveDays} days", "${trip.inclusiveDays} дней"), style = MaterialTheme.typography.bodySmall)
                            if (trip.note.isNotBlank()) Text(trip.note, style = MaterialTheme.typography.bodyMedium)
                            if (trip.isReminderEnabled) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsNone, contentDescription = null)
                                Text(text(language, "Local reminder on", "Локальное напоминание включено"), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(text(language, "Tap to edit", "Нажмите, чтобы изменить"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { deleting = trip }) { Icon(Icons.Default.DeleteOutline, contentDescription = text(language, "Delete trip", "Удалить поездку")) }
                    }
                }
            }
        }
    }
    if (editor) TripEditorDialog(language, countries, editing, { onSave(it); editor = false }, { editor = false })
    deleting?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(text(language, "Delete this trip?", "Удалить поездку?")) },
            text = { Text(countryName(trip.destination, countries, language) + " · " + trip.startDate + text(language, "\nThis cannot be undone.", "\nЭто действие нельзя отменить.")) },
            confirmButton = { TextButton(onClick = { onDelete(trip.id); deleting = null }) { Text(text(language, "Delete", "Удалить"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(text(language, "Cancel", "Отмена")) } }
        )
    }
}

@Composable
fun TripEditorDialog(language: String, countries: List<Country>, initial: Trip?, onSave: (Trip) -> Unit, onDismiss: () -> Unit) {
    var destination by rememberSaveable(initial?.id) { mutableStateOf(initial?.destination ?: "") }
    var start by rememberSaveable(initial?.id) { mutableStateOf(initial?.startDate ?: LocalDate.now().toString()) }
    var end by rememberSaveable(initial?.id) { mutableStateOf(initial?.endDate ?: LocalDate.now().toString()) }
    var note by rememberSaveable(initial?.id) { mutableStateOf(initial?.note ?: "") }
    var reminder by rememberSaveable(initial?.id) { mutableStateOf(initial?.isReminderEnabled ?: false) }
    var submitted by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf<String?>(null) }
    val startDate = parseDate(start)
    val endDate = parseDate(end)
    val invalidDates = startDate == null || endDate == null || endDate < startDate
    val invalidCountry = destination.isBlank() || countries.none { it.code == destination }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 760.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (initial == null) text(language, "A new journey", "Новая поездка") else text(language, "Edit your trip", "Изменить поездку"), style = MaterialTheme.typography.headlineMedium)
                CountryPicker(text(language, "Destination", "Направление"), destination, countries, language, { destination = it }, Modifier.testTag("trip-destination"))
                OutlinedTextField(start, { start = it }, label = { Text(text(language, "Start date (YYYY-MM-DD)", "Начало (ГГГГ-ММ-ДД)")) }, singleLine = true,
                    isError = submitted && invalidDates, modifier = Modifier.fillMaxWidth().testTag("trip-start"), trailingIcon = {
                        IconButton(onClick = { pickingDate = "start" }) { Icon(Icons.Default.CalendarMonth, contentDescription = text(language, "Choose start date", "Выбрать дату начала")) }
                    })
                OutlinedTextField(end, { end = it }, label = { Text(text(language, "End date (YYYY-MM-DD)", "Конец (ГГГГ-ММ-ДД)")) }, singleLine = true,
                    isError = submitted && invalidDates, modifier = Modifier.fillMaxWidth().testTag("trip-end"), trailingIcon = {
                        IconButton(onClick = { pickingDate = "end" }) { Icon(Icons.Default.CalendarMonth, contentDescription = text(language, "Choose end date", "Выбрать дату окончания")) }
                    })
                OutlinedTextField(note, { if (it.length <= 2000) note = it }, label = { Text(text(language, "Note (optional)", "Заметка (необязательно)")) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().toggleable(value = reminder, role = Role.Switch, onValueChange = { reminder = it }), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text(language, "Trip reminder", "Напоминание о поездке"), style = MaterialTheme.typography.titleMedium)
                        Text(text(language, "On the start day, at an approximate time. Requires notifications.", "В день начала, в приблизительное время. Нужен доступ к уведомлениям."), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = reminder, onCheckedChange = null)
                }
                Text(text(language, "Saved only on this device. Removing the app removes the journal.", "Хранится только на этом устройстве. При удалении приложения журнал исчезнет."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (submitted && invalidCountry) Text(text(language, "Choose a destination from the list.", "Выберите направление из списка."), color = MaterialTheme.colorScheme.error)
                if (submitted && invalidDates) Text(text(language, "Use real ISO dates. End date must be on or after the start date.", "Укажите существующие даты в формате ГГГГ-ММ-ДД. Конец не может быть раньше начала."), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("trip-date-error"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(text(language, "Cancel", "Отмена")) }
                    Button(onClick = {
                        submitted = true
                        if (!invalidDates && !invalidCountry) {
                            onSave(Trip(initial?.id ?: UUID.randomUUID().toString(), destination, start, end, note.trim(), reminder))
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip-save")) { Text(text(language, "Save trip", "Сохранить")) }
                }
            }
        }
    }
    pickingDate?.let { field ->
        val current = parseDate(if (field == "start") start else end)
        val picker = rememberDatePickerState(initialSelectedDateMillis = current?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickingDate = null }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { millis ->
                    val value = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    if (field == "start") start = value else end = value
                }
                pickingDate = null
            }) { Text(text(language, "Choose", "Выбрать")) }
        }, dismissButton = { TextButton(onClick = { pickingDate = null }) { Text(text(language, "Cancel", "Отмена")) } }) {
            DatePicker(state = picker, title = { Text(text(language, "Travel date", "Дата поездки"), Modifier.padding(24.dp)) }, headline = {
                Text(picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() } ?: text(language, "Choose date", "Выберите дату"), Modifier.padding(horizontal = 24.dp))
            })
        }
    }
}

private fun parseDate(value: String): LocalDate? {
    if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return null
    return try { LocalDate.parse(value) } catch (_: DateTimeParseException) { null }
}
