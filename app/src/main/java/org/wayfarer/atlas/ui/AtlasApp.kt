@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.wayfarer.atlas.ui

import android.content.res.Configuration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wayfarer.atlas.AtlasUiState
import org.wayfarer.atlas.domain.Country
import org.wayfarer.atlas.domain.EntryCategory
import org.wayfarer.atlas.domain.EntryRule
import org.wayfarer.atlas.domain.EntryRules
import org.wayfarer.atlas.domain.Trip
import java.util.Locale

@Composable
fun AtlasApp(
    state: AtlasUiState,
    initialTab: Int = 0,
    onCitizenship: (String) -> Unit = {},
    onLanguage: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    onSaveTrip: (Trip) -> Unit = {},
    onDeleteTrip: (String) -> Unit = {},
    onClearTrips: () -> Unit = {},
    onDismissError: () -> Unit = {},
    disclaimerRequired: Boolean = false,
    onAcknowledgeDisclaimer: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentConfiguration = LocalConfiguration.current
    val localizedConfiguration = remember(currentConfiguration, state.language) {
        Configuration(currentConfiguration).apply { setLocale(Locale.forLanguageTag(state.language)) }
    }
    val localizedContext = remember(context, localizedConfiguration) { context.createConfigurationContext(localizedConfiguration) }
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides localizedConfiguration) {
    AtlasTheme {
        var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
        var settings by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        val language = state.language
        LaunchedEffect(state.error, language) {
            if (state.error != null) {
                snackbar.showSnackbar(if (state.error == "update_partial") text(language, "Some sources could not be updated. Their last valid data is retained. See Settings for details.", "Не все источники удалось обновить. Для них сохранены последние корректные данные. Подробности в настройках.") else text(language, "Could not complete the action. Saved data remains available.", "Не удалось выполнить действие. Сохранённые данные доступны."), text(language, "Dismiss", "Закрыть"))
                onDismissError()
            }
        }
        val tabs = listOf(
            AtlasTab("explore", text(language, "Explore", "Карта"), Icons.Default.Map),
            AtlasTab("compare", text(language, "Compare", "Сравнить"), Icons.AutoMirrored.Filled.CompareArrows),
            AtlasTab("ranking", text(language, "Ranking", "Рейтинг"), Icons.AutoMirrored.Filled.TrendingUp),
            AtlasTab("trips", text(language, "Trips", "Поездки"), Icons.Default.BookmarkBorder)
        )
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(text(language, "Wayfarer Atlas", "Атлас маршрутов"), style = MaterialTheme.typography.titleLarge) },
                    actions = { IconButton(onClick = { settings = true }, modifier = Modifier.testTag("settings")) { Icon(Icons.Default.Settings, contentDescription = text(language, "Settings", "Настройки")) } }
                )
            },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(item.icon, contentDescription = null) }, label = { Text(item.title) }, modifier = Modifier.testTag("tab-${item.id}"))
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
                if (state.loading) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text(text(language, "Opening your atlas…", "Открываем атлас…"))
                    }
                } else when (tab) {
                    0 -> ExploreScreen(state, onCitizenship)
                    1 -> CompareScreen(state)
                    2 -> RankingScreen(state)
                    3 -> TripsScreen(state.countries, state.trips, language, onSaveTrip, onDeleteTrip)
                }
                if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
        if (settings) SettingsDialog(state, onLanguage, onRefresh, onClearTrips, { settings = false })
        if (disclaimerRequired) TravelDisclaimerDialog(language, onAcknowledgeDisclaimer)
    }
    }
}

private data class AtlasTab(val id: String, val title: String, val icon: ImageVector)

@Composable
private fun ExploreScreen(state: AtlasUiState, onCitizenship: (String) -> Unit) {
    val language = state.language
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var detail by remember { mutableStateOf<String?>(null) }
    val destinations = remember(state.countries, state.citizenship, language) {
        state.countries.filter { it.code != state.citizenship }.sortedBy { it.localized(language) }
    }
    val rules = remember(state.rules, state.citizenship) { rulesByDestination(state.rules, state.citizenship) }
    val summary = remember(state.rules, state.citizenship, state.countries) { EntryRules.summarize(state.citizenship, state.countries.map { it.code }, state.rules) }
    val filtered = remember(destinations, rules, query, filter) {
        destinations.filter { country ->
            val category = rules[country.code]?.category ?: EntryCategory.UNKNOWN
            (country.localized(language).contains(query, ignoreCase = true) || country.code.contains(query, ignoreCase = true)) &&
                (filter == "all" || (filter == "accessible" && category.isAccessible) || filter == category.name)
        }
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading(text(language, "Where will you go?", "Куда отправимся?"), text(language, "Explore entry conditions, one destination at a time.", "Изучайте условия въезда и планируйте новые маршруты.")) }
        item { CountryPicker(text(language, "Your citizenship", "Ваше гражданство"), state.citizenship, state.citizenships.ifEmpty { state.countries }, language, onCitizenship) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summary.total > 0 && summary.unknown < summary.total) {
                        Text("${summary.accessible}", style = MaterialTheme.typography.headlineLarge)
                        Text(text(language, "destinations with visa-free, arrival visa or ETA access", "направлений без визы, с визой по прибытии или ETA"), style = MaterialTheme.typography.bodyMedium)
                        Text(text(language, "Known rules: ${summary.total - summary.unknown} of ${summary.total}. E-visas are counted separately.", "Известны условия: ${summary.total - summary.unknown} из ${summary.total}. Электронные визы считаются отдельно."), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(text(language, "Entry conditions are not loaded", "Условия въезда не загружены"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("coverage-unknown"))
                        Text(text(language, "Unknown means check the source. It never means visa-free entry.", "Неизвестные условия нужно уточнить в источнике. Они не означают безвизовый въезд."), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { AtlasMap(state.shapes, rules.mapValues { it.value.category }, state.citizenship, language, { detail = it }) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to text(language, "All", "Все"), "accessible" to text(language, "Accessible", "Доступны"), EntryCategory.E_VISA.name to text(language, "E-visa", "Электронная виза"), EntryCategory.VISA_REQUIRED.name to text(language, "Visa required", "Нужна виза"), EntryCategory.UNKNOWN.name to text(language, "Unknown", "Неизвестно")).forEach { (key, label) ->
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(text(language, "Search destinations", "Поиск направлений")) }, singleLine = true) }
        if (filtered.isEmpty()) item { Text(text(language, "No matching destinations", "Подходящих направлений нет"), Modifier.padding(vertical = 16.dp)) }
        items(filtered, key = { it.code }) { country ->
            DestinationRow(country, rules[country.code]?.category ?: EntryCategory.UNKNOWN, language) { detail = country.code }
        }
        item { SourceNote(state) }
    }
    detail?.let { DestinationDetail(it, state.citizenship, state.countries, rules[it], language) { detail = null } }
}

internal fun rulesByDestination(rules: List<EntryRule>, citizenship: String): Map<String, EntryRule> =
    rules.filter { it.citizenship == citizenship }.groupBy { it.destination }.mapNotNull { (code, entries) ->
        // Conflicting source rows remain unknown, matching the domain summary.
        if (entries.map { it.category }.distinct().size == 1) code to entries.first() else null
    }.toMap()

@Composable
internal fun DestinationRow(country: Country, category: EntryCategory, language: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(country.localized(language), fontWeight = FontWeight.Medium) },
        supportingContent = { Text(categoryLabel(category, language)) },
        leadingContent = { Box(Modifier.size(12.dp).background(categoryColor(category), CircleShape)) },
        trailingContent = { Text(country.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.fillMaxWidth().testTag("destination-${country.code}").clickable(onClick = onClick).heightIn(min = 72.dp)
    )
}

@Composable
internal fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SourceNote(state: AtlasUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))
        Text(text(state.language, "Reference data: ", "Справочные данные: ") + if (state.sources.isNotEmpty()) state.sources.joinToString(" · ") { if (state.language == "ru") it.nameRu else it.nameEn } else state.sourceName.ifBlank { text(state.language, "not available", "недоступны") }, style = MaterialTheme.typography.bodySmall)
        if (state.sources.isNotEmpty()) Text(text(state.language, "Source dates and update status are listed in Settings.", "Даты источников и состояние обновления указаны в настройках."), style = MaterialTheme.typography.bodySmall)
        else Text(text(state.language, "Source updated: ", "Обновление источника: ") + (state.updatedAt?.takeIf { it.isNotBlank() } ?: text(state.language, "not provided", "не указано")), style = MaterialTheme.typography.bodySmall)
        Text(text(state.language, "Verify current requirements with official authorities before travel.", "Перед поездкой уточните действующие требования в официальных источниках."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
