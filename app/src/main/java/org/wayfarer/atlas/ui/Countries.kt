@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.wayfarer.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.wayfarer.atlas.domain.Country
import org.wayfarer.atlas.domain.EntryCategory
import org.wayfarer.atlas.domain.EntryRule

internal fun Country.localized(language: String) = if (language == "ru") nameRu else nameEn
internal fun countryName(code: String, countries: List<Country>, language: String): String =
    countries.find { it.code == code }?.localized(language) ?: code

private fun localizedRuleNotes(notes: String, language: String): String {
    if (language != "ru") return notes
    val standard = "Community reference; ordinary passport tourism; check official conditions."
    return when {
        notes.startsWith(standard) -> notes.replace(standard, "Данные сообщества; туризм по обычному заграничному паспорту. Уточните официальные условия.")
            .replace(" Not individually verified.", " Отдельная проверка не указана.")
            .replace(" Upstream reference: ", " Первоисточник: ")
        notes == "Community dataset; check official entry conditions before travel." -> "Данные сообщества. Перед поездкой уточните официальные условия въезда."
        else -> notes
    }
}

internal fun categoryLabel(category: EntryCategory, language: String): String = when (category) {
    EntryCategory.VISA_FREE -> text(language, "Visa free", "Без визы")
    EntryCategory.VISA_ON_ARRIVAL -> text(language, "Visa on arrival", "Виза по прибытии")
    EntryCategory.ETA -> text(language, "Travel authorization (ETA)", "Разрешение ETA")
    EntryCategory.E_VISA -> text(language, "E-visa", "Электронная виза")
    EntryCategory.VISA_REQUIRED -> text(language, "Visa required", "Нужна виза")
    EntryCategory.RESTRICTED -> text(language, "Restricted entry", "Въезд ограничен")
    EntryCategory.UNKNOWN -> text(language, "No reliable data", "Нет надёжных данных")
}

@Composable
internal fun CountryPicker(
    label: String,
    code: String,
    countries: List<Country>,
    language: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    OutlinedCard(onClick = { open = true }, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (code.isBlank()) text(language, "Choose a country", "Выберите страну") else countryName(code, countries, language), style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
    }
    if (open) CountrySearchDialog(countries, language, label, { onSelect(it); open = false }, { open = false })
}

@Composable
private fun CountrySearchDialog(countries: List<Country>, language: String, label: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(countries, language, query) {
        countries.filter { it.localized(language).contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true) }
            .sortedBy { it.localized(language) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text(text(language, "Find a country", "Найти страну")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.code }) { country ->
                        ListItem(headlineContent = { Text(country.localized(language)) }, trailingContent = { Text(country.code) },
                            modifier = Modifier.clickable { onSelect(country.code) }.heightIn(min = 56.dp))
                    }
                    if (filtered.isEmpty()) item { Text(text(language, "No countries found", "Страны не найдены"), Modifier.padding(16.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text(language, "Close", "Закрыть")) } }
    )
}

@Composable
internal fun DestinationDetail(code: String, citizenship: String, countries: List<Country>, rule: EntryRule?, language: String, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val category = rule?.category ?: EntryCategory.UNKNOWN
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(countryName(code, countries, language)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text(language, "Citizenship: ", "Гражданство: ") + countryName(citizenship, countries, language), style = MaterialTheme.typography.bodyMedium)
                Text(categoryLabel(category, language), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("destination-category"))
                rule?.maxStayDays?.let { days -> Text(text(language, "Maximum stay in source: $days days", "Срок в источнике: $days дней")) }
                val notes = if (language == "ru" && !rule?.notesRu.isNullOrBlank()) rule!!.notesRu else rule?.notes?.let { localizedRuleNotes(it, language) }.orEmpty()
                if (notes.isNotBlank()) Text(notes, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text(text(language, "Reference scope: short-stay tourism with an ordinary passport, single entry and direct arrival. Transit, residence-permit alternatives and rolling-window limits are not covered. Confirm with an embassy or official immigration authority before booking.", "Справочник охватывает краткосрочный туризм по обычному заграничному паспорту, однократный прямой въезд. Транзит, альтернативы по ВНЖ и ограничения по скользящим периодам не учтены. Перед бронированием уточните условия в консульстве или официальной миграционной службе."), style = MaterialTheme.typography.bodySmall)
                val official = rule?.citations?.any { it.isOfficial } == true
                Text(rule?.checkedAt?.takeIf { it.isNotBlank() }?.let {
                    if (official) text(language, "Official data checked/imported: $it", "Сверка/получение официальных данных: $it")
                    else text(language, "Verification date supplied by the source: $it", "Дата проверки, указанная источником: $it")
                } ?: text(language, "The source provides no individual verification date for this rule.", "Источник не указал дату отдельной проверки этого правила."), style = MaterialTheme.typography.bodySmall)
                rule?.citations?.distinctBy { it.url }?.forEach { citation ->
                    if (citation.url.startsWith("https://")) {
                        TextButton(onClick = { uriHandler.openUri(citation.url) }) {
                            val name = if (language == "ru") citation.nameRu else citation.nameEn
                            Text(name.ifBlank { text(language, "Open data source", "Открыть источник") })
                        }
                    }
                }

            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text(language, "Done", "Готово")) } }
    )
}
