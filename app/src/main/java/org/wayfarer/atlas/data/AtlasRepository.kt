package org.wayfarer.atlas.data

import android.content.Context
import android.util.AtomicFile
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.wayfarer.atlas.domain.Country
import org.wayfarer.atlas.domain.EntryRule
import org.wayfarer.atlas.domain.RuleCitation
import java.io.File
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import javax.net.ssl.HttpsURLConnection

data class CountryShape(val code: String, val polygons: List<List<Pair<Double, Double>>>)
data class ReferenceSourceState(
    val id: String, val nameEn: String, val nameRu: String, val url: String,
    val official: Boolean, val sourceDate: String?, val reviewedAt: String?, val downloadedAt: String?,
    val ruleCount: Int, val liveUpdates: Boolean, val failedUpdate: Boolean,
    val notesEn: String = "", val notesRu: String = ""
)
data class AtlasReference(
    val countries: List<Country>, val rules: List<EntryRule>, val shapes: List<CountryShape>,
    val sourceName: String, val sourceUrl: String, val updatedAt: String?, val downloadedAt: String?,
    val sources: List<ReferenceSourceState> = emptyList(), val failedSources: List<String> = emptyList()
)

/** Each public source has its own validated cache; government supplements survive community refreshes. */
class AtlasRepository(private val context: Context) {
    private val metadata = context.getSharedPreferences("reference-metadata", Context.MODE_PRIVATE)
    private var loaded: Map<String, LoadedSource> = emptyMap()
    private data class SourceConfig(
        val id: String, val nameEn: String, val nameRu: String, val referenceUrl: String,
        val official: Boolean, val asset: String, val adapter: String, val url: String?,
        val allowedHosts: Set<String>, val minimumRules: Int, val minimumKnown: Int, val minimumOrigins: Int,
        val mappingAsset: String? = null, val notesEn: String = "", val notesRu: String = ""
    )
    private data class LoadedSource(val config: SourceConfig, val snapshot: RuleSnapshot, val downloadedAt: String?, val failed: Boolean)

    fun load(): AtlasReference {
        val countries = DatasetParser.parseCountries(asset("countries.json"))
        val codes = countries.map { it.code }.toSet()
        loaded = configs().associate { config -> config.id to readSource(config, codes) }
        return combined(countries)
    }

    /** Only explicit user action initiates downloads. One failed source never erases another source. */
    fun refresh(): AtlasReference {
        if (loaded.isEmpty()) load()
        val countries = DatasetParser.parseCountries(asset("countries.json"))
        val codes = countries.map { it.code }.toSet()
        val updated = loaded.toMutableMap()
        for ((id, previous) in loaded) {
            val config = previous.config
            if (config.adapter == "bundled-only") continue
            try {
                val text = download(config)
                val canonical: String
                val snapshot: RuleSnapshot
                when (config.adapter) {
                    "visachecker-community-v1" -> {
                        val normalized = CommunityMatrixParser.normalizeAndParse(text, codes)
                        canonical = normalized.canonical; snapshot = normalized.snapshot
                    }
                    "hong-kong-csv-v1" -> {
                        canonical = HongKongCsvParser.normalize(text, asset(config.mappingAsset ?: throw IOException("Missing source mapping")), codes, LocalDate.now().toString())
                        snapshot = DatasetParser.parseRules(canonical, codes)
                    }
                    else -> throw IOException("Unsupported reference adapter")
                }
                ReferenceUpdatePolicy.validateReplacement(previous.snapshot, snapshot, config.minimumRules, config.minimumOrigins, config.minimumKnown)
                val cache = cache(config)
                val stream = cache.startWrite()
                try {
                    stream.write(canonical.toByteArray(Charsets.UTF_8)); cache.finishWrite(stream)
                } catch (failure: Exception) { cache.failWrite(stream); throw failure }
                val downloaded = Instant.now().toString()
                metadata.edit().putString(downloadKey(config), downloaded).putBoolean("failed-$id", false).commit()
                updated[id] = LoadedSource(config, snapshot, downloaded, false)
            } catch (_: Exception) {
                metadata.edit().putBoolean("failed-$id", true).commit()
                updated[id] = previous.copy(failed = true)
            }
        }
        loaded = updated
        return combined(countries)
    }

    private fun readSource(config: SourceConfig, codes: Set<String>): LoadedSource {
        val cached = runCatching { cache(config).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
        val bundled = optionalAsset(config.asset)
        val preferCache = SnapshotSelection.preferCache(cached != null, SnapshotSelection.publicationDate(cached), SnapshotSelection.publicationDate(bundled))
        val choices = if (preferCache) listOf(cached to true, bundled to false) else listOf(bundled to false, cached to true)
        val selected = choices.firstNotNullOfOrNull { (text, fromCache) ->
            text?.let {
                runCatching {
                    val snapshot = DatasetParser.parseRules(it, codes)
                    validate(config, snapshot, null)
                    snapshot to fromCache
                }.getOrNull()
            }
        }
        return LoadedSource(config, selected?.first ?: RuleSnapshot(emptyList(), config.referenceUrl, null),
            if (selected?.second == true) metadata.getString(downloadKey(config), null) else null,
            selected == null || metadata.getBoolean("failed-${config.id}", false))
    }

    private fun combined(countries: List<Country>): AtlasReference {
        val community = loaded.values.firstOrNull { !it.config.official }
        val government = loaded.values.filter { it.config.official }.map { source ->
            source.snapshot.rules.map { rule ->
                val url = rule.sourceUrl ?: source.config.referenceUrl
                val asAt = source.snapshot.sourceAsAt
                val notes = rule.notes + (asAt?.let { "\nOfficial source state date: $it. This is separate from the import date; check the current source before travel." } ?: "")
                val notesRu = rule.notesRu + (asAt?.let { "\nСостояние данных в официальном источнике: $it. Это отдельная дата от загрузки; перед поездкой проверьте текущий источник." } ?: "")
                EntryRule(rule.citizenship, rule.destination, rule.category, rule.maxStayDays, notes, url, rule.checkedAt, notesRu,
                    listOf(RuleCitation(source.config.nameEn, source.config.nameRu, url, rule.checkedAt, true)))
            }
        }
        val sources = loaded.values.map { source ->
            val config = source.config
            ReferenceSourceState(config.id, config.nameEn, config.nameRu, config.referenceUrl, config.official,
                if (config.official) source.snapshot.sourceAsAt else source.snapshot.updatedAt,
                if (config.official) source.snapshot.updatedAt else null, source.downloadedAt, source.snapshot.rules.size,
                config.adapter != "bundled-only", source.failed, config.notesEn, config.notesRu)
        }
        return AtlasReference(countries, RuleMerger.combine(community?.snapshot?.rules.orEmpty(), government),
            parseShapes(optionalAsset("world.geojson"), countries.map { it.code }.toSet()),
            "VisaChecker + official immigration sources", community?.snapshot?.sourceUrl.orEmpty(),
            community?.snapshot?.updatedAt, community?.downloadedAt, sources, sources.filter { it.failedUpdate }.map { it.id })
    }

    private fun configs(): List<SourceConfig> {
        val community = JsonParser.parseString(asset("update-source.json")).asJsonObject
        val result = mutableListOf(SourceConfig("community", "VisaChecker community dataset", "Общий справочник VisaChecker",
            community.get("attributionUrl").asString, false, "rules.json", community.get("format").asString,
            community.get("url").asString, community.getAsJsonArray("allowedHosts").map { it.asString }.toSet(),
            community.get("minimumRules").asInt, community.get("minimumKnownRules").asInt, community.get("minimumCitizenships").asInt))
        val catalog = JsonParser.parseString(asset("official-sources.json")).asJsonObject
        require(catalog.get("version").asInt == 1)
        val ids = mutableSetOf("community")
        for (row in catalog.getAsJsonArray("sources")) {
            val source = row.asJsonObject
            val id = source.get("id").asString
            require(id.matches(Regex("[a-z0-9-]{1,80}")) && ids.add(id) && ids.size <= 10)
            val adapter = source.get("adapter").asString
            require(adapter in setOf("hong-kong-csv-v1", "bundled-only") && source.get("kind").asString == "government")
            val sourceAsset = source.get("asset").asString
            require(sourceAsset.matches(Regex("[a-z0-9-]+\\.json")))
            val mapping = source.optional("mappingAsset")
            require(mapping == null || mapping.matches(Regex("[a-z0-9-]+\\.json")))
            val reference = source.get("referenceUrl").asString
            requireHttps(reference)
            result += SourceConfig(id, source.get("nameEn").asString, source.get("nameRu").asString, reference,
                true, sourceAsset, adapter, source.optional("url"), setOf("www.immd.gov.hk"),
                source.get("minimumRules").asInt, source.get("minimumKnownRules").asInt, source.get("minimumCitizenships").asInt,
                mapping, source.optional("notesEn").orEmpty(), source.optional("notesRu").orEmpty())
        }
        return result
    }

    private fun validate(config: SourceConfig, snapshot: RuleSnapshot, previous: String?) =
        ReferenceUpdatePolicy.validate(previous, snapshot, config.minimumRules, config.minimumOrigins, config.minimumKnown)
    private fun cache(config: SourceConfig) = AtomicFile(File(context.filesDir, if (config.id == "community") "reference.json" else "reference-${config.id}.json"))
    private fun downloadKey(config: SourceConfig) = if (config.id == "community") "downloadedAt" else "downloadedAt-${config.id}"
    private fun requireHttps(value: String): URL = URL(value).also { require(it.protocol == "https" && it.userInfo == null) }
    private fun JsonObject.optional(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString

    private fun download(config: SourceConfig): String {
        val parsed = requireHttps(config.url ?: throw IOException("No update URL"))
        if (parsed.host !in config.allowedHosts || config.allowedHosts.isEmpty()) throw IOException("Unapproved update host")
        val connection = parsed.openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false; connection.connectTimeout = 15_000; connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", if (config.adapter == "hong-kong-csv-v1") "text/csv" else "application/json")
        connection.setRequestProperty("User-Agent", "WayfarerAtlas/0.1.1")
        return try {
            if (connection.responseCode != 200) throw IOException("Reference server unavailable")
            if (connection.contentLengthLong > MAX_REFERENCE_BYTES) throw IOException("Reference too large")
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    val size = input.read(buffer); if (size < 0) break
                    if (output.size() + size > MAX_REFERENCE_BYTES) throw IOException("Reference too large")
                    output.write(buffer, 0, size)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally { connection.disconnect() }
    }

    private fun asset(name: String): String = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    private fun optionalAsset(name: String): String? = try { asset(name) } catch (_: java.io.FileNotFoundException) { null }
    private fun parseShapes(text: String?, codes: Set<String>): List<CountryShape> {
        if (text == null) return emptyList()
        return JsonParser.parseString(text).asJsonObject.getAsJsonArray("features").mapNotNull { item ->
            val feature = item.asJsonObject; val code = feature.getAsJsonObject("properties").get("code").asString
            if (code !in codes) return@mapNotNull null
            val geometry = feature.getAsJsonObject("geometry"); val coordinates = geometry.getAsJsonArray("coordinates")
            val polygons = when (geometry.get("type").asString) {
                "Polygon" -> listOf(coordinates)
                "MultiPolygon" -> coordinates.map { it.asJsonArray }
                else -> throw IOException("Unsupported map geometry")
            }
            CountryShape(code, polygons.map { polygon -> ring(polygon[0].asJsonArray) })
        }
    }
    private fun ring(array: JsonArray): List<Pair<Double, Double>> = array.map {
        val pair = it.asJsonArray; val lon = pair[0].asDouble; val lat = pair[1].asDouble
        if (!lon.isFinite() || !lat.isFinite() || lon !in -180.0..180.0 || lat !in -90.0..90.0) throw IOException("Invalid map geometry")
        lon to lat
    }
    companion object { private const val MAX_REFERENCE_BYTES = 16 * 1024 * 1024 }
}
