package org.wayfarer.atlas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.wayfarer.atlas.AtlasUiState

@Composable
internal fun SettingsDialog(state: AtlasUiState, onLanguage: (String) -> Unit, onRefresh: () -> Unit, onClearTrips: () -> Unit, onDismiss: () -> Unit) {
    val language = state.language
    val uri = LocalUriHandler.current
    var confirming by remember { mutableStateOf(false) }
    var notices by remember { mutableStateOf(false) }
    var travelNotice by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val licenseText = remember(context) { runCatching { context.assets.open("licenses.txt").bufferedReader().use { it.readText() } }.getOrDefault("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text(language, "Settings", "Настройки")) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text(language, "App language", "Язык приложения"), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = language == "en", onClick = { onLanguage("en") }, label = { Text("English") }, modifier = Modifier.heightIn(min = 48.dp))
                    FilterChip(selected = language == "ru", onClick = { onLanguage("ru") }, label = { Text("Русский") }, modifier = Modifier.heightIn(min = 48.dp))
                }
                TextButton(onClick = { travelNotice = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("review-travel-disclaimer")) {
                    Text(text(language, "Travel information notice", "Важная информация о поездках"))
                }
                HorizontalDivider()
                Text(text(language, "Reference data", "Справочник"), style = MaterialTheme.typography.titleMedium)
                if (state.sources.isEmpty()) {
                    Text(state.sourceName.ifBlank { text(language, "Source not available", "Источник недоступен") })
                } else state.sources.forEach { source ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("source-${source.id}")) {
                        Text(if (language == "ru") source.nameRu else source.nameEn, style = MaterialTheme.typography.titleSmall)
                        Text(text(language, if (source.official) "Official source" else "Community dataset", if (source.official) "Официальный источник" else "Общий открытый набор"), style = MaterialTheme.typography.labelMedium)
                        Text(text(language, "Rules: ${source.ruleCount}", "Пар условий: ${source.ruleCount}"), style = MaterialTheme.typography.bodySmall)
                        Text(text(language, if (source.official) "Official source state date: " else "Source updated: ", if (source.official) "Состояние данных в источнике: " else "Обновление источника: ") + (source.sourceDate ?: text(language, "not provided", "не указано")), style = MaterialTheme.typography.bodySmall)
                        source.reviewedAt?.let { Text(text(language, "Snapshot checked/imported: ", "Сверка/получение среза: ") + it, style = MaterialTheme.typography.bodySmall) }
                        Text(text(language, "Last downloaded: ", "Последняя загрузка: ") + (source.downloadedAt ?: text(language, "bundled snapshot", "встроенный срез")), style = MaterialTheme.typography.bodySmall)
                        Text(if (source.liveUpdates) text(language, "Updates with the button below.", "Обновляется кнопкой ниже.") else text(language, "Reviewed snapshot; updates with app releases.", "Проверенный срез; обновляется с версией приложения."), style = MaterialTheme.typography.bodySmall)
                        if (source.failedUpdate) Text(text(language, "The last update failed. The last valid snapshot is retained.", "Последнее обновление не удалось. Сохранён последний корректный срез."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { uri.openUri(source.url) }) { Text(text(language, "Open source", "Открыть источник")) }
                    }
                    HorizontalDivider()
                }
                Text(text(language, "A download or import date is not a new publication date and does not guarantee current rules. Official and community sources can be incomplete or out of date. Confirm requirements before making travel decisions.", "Дата загрузки или получения среза не является новой датой публикации и не гарантирует актуальность правил. Официальные и открытые источники могут быть неполными или устаревшими. Перепроверьте требования перед принятием решений о поездке."), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRefresh, enabled = !state.refreshing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(if (state.refreshing) text(language, "  Updating…", "  Обновляем…") else text(language, "  Update reference data", "  Обновить справочник"))
                }
                HorizontalDivider()
                Text(text(language, "Privacy", "Приватность"), style = MaterialTheme.typography.titleMedium)
                Text(text(language, "No account, ads, purchases or cloud sync. The app does not collect documents or use the camera. Your citizenship preference and travel journal stay in private storage on this device, outside Android backup. No analytics SDK is included.", "Без аккаунтов, рекламы, покупок и облачной синхронизации. Приложение не собирает документы и не использует камеру. Выбранное гражданство и журнал поездок хранятся в закрытом хранилище устройства и исключены из резервного копирования Android. SDK аналитики нет."), style = MaterialTheme.typography.bodySmall)
                Text(text(language, "Reference updates connect to the public data source. Its hosting provider receives standard network information such as your IP address. Opening a source link uses your browser. Uninstalling the app removes local trips.", "Обновление справочника обращается к открытому источнику данных. Его хостинг получает обычные сетевые сведения, например IP-адрес. Ссылки на источники открываются в браузере. При удалении приложения локальные поездки исчезают."), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { notices = true }) { Text(text(language, "Data licenses", "Лицензии данных")) }
                TextButton(onClick = { confirming = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("clear-journal")) {
                    Text(text(language, "Clear travel journal", "Очистить журнал поездок"), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text(language, "Done", "Готово")) } }
    )
    if (travelNotice) TravelDisclaimerDialog(language) { travelNotice = false }
    if (notices) AlertDialog(onDismissRequest = { notices = false },
        title = { Text(text(language, "Data licenses", "Лицензии данных")) },
        text = { Text(licenseText, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = { notices = false }) { Text(text(language, "Done", "Готово")) } })
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(text(language, "Clear all trips?", "Удалить все поездки?")) },
        text = { Text(text(language, "All local journal entries and their reminders will be removed. This cannot be undone.", "Все локальные записи журнала и их напоминания будут удалены. Это действие нельзя отменить.")) },
        confirmButton = { TextButton(onClick = { onClearTrips(); confirming = false }, modifier = Modifier.testTag("confirm-clear-journal")) { Text(text(language, "Clear journal", "Очистить журнал"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(text(language, "Cancel", "Отмена")) } }
    )
}
