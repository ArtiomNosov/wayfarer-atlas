package org.wayfarer.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wayfarer.atlas.AtlasUiState
import org.wayfarer.atlas.domain.EntryCategory
import org.wayfarer.atlas.domain.EntryRules

@Composable
internal fun CompareScreen(state: AtlasUiState) {
    val language = state.language
    val citizenships = state.citizenships.ifEmpty { state.countries }
    var first by rememberSaveable { mutableStateOf(state.citizenship) }
    var second by rememberSaveable { mutableStateOf(citizenships.firstOrNull { it.code != first }?.code ?: "") }
    var group by rememberSaveable { mutableIntStateOf(1) }
    var detail by remember { mutableStateOf<String?>(null) }
    val comparison = remember(first, second, state.countries, state.rules) {
        if (second.isNotBlank() && first != second) EntryRules.compare(first, second, state.countries.map { it.code }, state.rules) else null
    }
    val firstRules = remember(first, state.rules) { rulesByDestination(state.rules, first) }
    val secondRules = remember(second, state.rules) { rulesByDestination(state.rules, second) }
    val groups = listOf(comparison?.firstOnly ?: emptySet(), comparison?.both ?: emptySet(), comparison?.secondOnly ?: emptySet(), comparison?.unknown ?: emptySet())
    val labels = listOf(text(language, "First only", "Только первое"), text(language, "Both", "Оба"), text(language, "Second only", "Только второе"), text(language, "Unknown", "Неизвестно"))
    val countries = state.countries.filter { it.code in groups[group] }.sortedBy { it.localized(language) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading(text(language, "Two ways to travel", "Два варианта пути"), text(language, "Compare entry access for two citizenships.", "Сравните доступность направлений для двух гражданств.")) }
        item { CountryPicker(text(language, "First citizenship", "Первое гражданство"), first, citizenships, language, { first = it }) }
        item { CountryPicker(text(language, "Second citizenship", "Второе гражданство"), second, citizenships, language, { second = it }) }
        item {
            Text(text(language, "Access includes visa-free entry, visa on arrival and ETA. An e-visa is a separate application. Both origin countries are excluded. Missing rules stay unknown.", "Доступ включает безвизовый въезд, визу по прибытии и ETA. Электронная виза требует отдельной заявки. Страны обоих гражданств исключены. Пропуски остаются неизвестными."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (first == second || second.isBlank()) {
            item { Text(text(language, "Choose two different citizenships to compare.", "Выберите два разных гражданства для сравнения.")) }
        } else {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEachIndexed { index, label ->
                        FilterChip(selected = group == index, onClick = { group = index }, label = { Text("$label · ${groups[index].size}") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
            if (countries.isEmpty()) item { Text(text(language, "No destinations in this group", "В этой группе нет направлений"), Modifier.padding(vertical = 16.dp)) }
            items(countries, key = { it.code }) { country ->
                ListItem(
                    headlineContent = { Text(country.localized(language), fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${countryName(first, state.countries, language)}: ${categoryLabel(firstRules[country.code]?.category ?: EntryCategory.UNKNOWN, language)}")
                            Text("${countryName(second, state.countries, language)}: ${categoryLabel(secondRules[country.code]?.category ?: EntryCategory.UNKNOWN, language)}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { detail = country.code }.heightIn(min = 88.dp)
                )
            }
        }
        item { SourceNote(state) }
    }
    detail?.let { DestinationDetail(it, first, state.countries, firstRules[it], language) { detail = null } }
}

@Composable
internal fun RankingScreen(state: AtlasUiState) {
    val language = state.language
    val rows = remember(state.countries, state.citizenships, state.rules, language) {
        val destinations = state.rules.map { it.destination }.toSet()
        state.citizenships.ifEmpty { state.countries }.map { country -> country to EntryRules.summarize(country.code, destinations, state.rules) }
            .filter { (_, summary) -> summary.total > 0 && summary.unknown < summary.total }
            .sortedWith(compareByDescending<Pair<org.wayfarer.atlas.domain.Country, org.wayfarer.atlas.domain.AccessSummary>> { it.second.accessible }.thenBy { it.first.localized(language) })
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading(text(language, "Travel access", "Доступность поездок"), text(language, "An open method. Visible coverage.", "Открытая методика. Видимое покрытие.")) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text(language, "How this overview works", "Как устроен обзор"), style = MaterialTheme.typography.titleMedium)
                    Text(text(language, "Sorted by known destinations with visa-free entry, visa on arrival or ETA. E-visas are separate. Every citizenship uses the same source destinations; its own country is excluded. Destinations outside the source are not ranked. This is a community reference, not an authoritative worldwide ranking.", "Сортировка по известным направлениям без визы, с визой по прибытии или ETA. Электронные визы отдельно. Для всех гражданств используется одна база направлений источника; своя страна исключена. Направления вне источника в рейтинг не входят. Это справочник сообщества, а не официальный мировой рейтинг."), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (rows.isEmpty()) item { Text(text(language, "There is not enough known data to build an overview. Update the reference data in Settings.", "Недостаточно известных данных для обзора. Обновите справочник в настройках."), Modifier.padding(vertical = 16.dp)) }
        items(rows, key = { it.first.code }) { (country, summary) ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(country.localized(language), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(summary.accessible.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(text(language, "${summary.visaFree} visa free · ${summary.visaOnArrival} arrival · ${summary.eta} ETA · ${summary.getEVisa()} e-visa", "${summary.visaFree} без визы · ${summary.visaOnArrival} по прибытии · ${summary.eta} ETA · ${summary.getEVisa()} эл. виза"), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { (summary.total - summary.unknown).toFloat() / summary.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(text(language, "Coverage: ${summary.total - summary.unknown}/${summary.total} source destinations" + if (summary.isComplete) " · complete" else " · incomplete", "Покрытие: ${summary.total - summary.unknown}/${summary.total} направлений источника" + if (summary.isComplete) " · полное" else " · неполное"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SourceNote(state) }
    }
}
