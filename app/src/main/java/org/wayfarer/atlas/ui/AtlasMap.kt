package org.wayfarer.atlas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.wayfarer.atlas.data.CountryShape
import org.wayfarer.atlas.domain.EntryCategory

internal fun categoryColor(category: EntryCategory): Color = when (category) {
    EntryCategory.VISA_FREE -> Color(0xFF34795D)
    EntryCategory.VISA_ON_ARRIVAL -> Color(0xFF8BA96C)
    EntryCategory.ETA -> Color(0xFFC5D39C)
    EntryCategory.E_VISA -> Color(0xFFDBC886)
    EntryCategory.VISA_REQUIRED -> Color(0xFFC99579)
    EntryCategory.RESTRICTED -> Color(0xFFA9584C)
    EntryCategory.UNKNOWN -> Color(0xFFC7CEC6)
}

@Composable
internal fun AtlasMap(shapes: List<CountryShape>, categories: Map<String, EntryCategory>, citizenship: String, language: String, onSelect: (String) -> Unit) {
    val water = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f)
    val border = MaterialTheme.colorScheme.surface
    val caption = text(language, "Tap a country, or use the accessible list below", "Нажмите на страну или выберите её в списке ниже")
    val polygonShapes = remember(shapes) { shapes.flatMap { shape -> shape.polygons.map { shape.code to it } } }
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(water)) {
        if (shapes.isEmpty()) Text(
            text(language, "The map is unavailable. Choose a destination in the list below.", "Карта недоступна. Выберите направление в списке ниже."),
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium
        )
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.8f).semantics { contentDescription = caption }
            .pointerInput(polygonShapes, citizenship) {
                detectTapGestures { offset ->
                    val lon = offset.x / size.width * 360.0 - 180.0
                    val lat = 90.0 - offset.y / size.height * 180.0
                    polygonShapes.asReversed().firstOrNull { (code, points) ->
                        code != citizenship && containsPoint(points, lon, lat)
                    }?.let { onSelect(it.first) }
                }
            }) {
            for ((code, polygon) in polygonShapes) {
                if (polygon.size < 3) continue
                val path = Path()
                var previous: Pair<Double, Double>? = null
                for ((index, point) in polygon.withIndex()) {
                    val x = ((point.first + 180.0) / 360.0 * size.width).toFloat()
                    val y = ((90.0 - point.second) / 180.0 * size.height).toFloat()
                    if (index == 0 || (previous != null && kotlin.math.abs(previous.first - point.first) > 180)) path.moveTo(x, y)
                    else path.lineTo(x, y)
                    previous = point
                }
                path.close()
                drawPath(path, if (code == citizenship) Color(0xFF203E32) else categoryColor(categories[code] ?: EntryCategory.UNKNOWN))
                drawPath(path, border, style = Stroke(width = .8.dp.toPx()))
            }
            // Quiet graticules keep the map readable while all geometry stays on device.
            for (longitude in -120..120 step 60) {
                val x = (longitude + 180f) / 360f * size.width
                drawLine(border.copy(alpha = .25f), Offset(x, 0f), Offset(x, size.height), .5.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EntryCategory.values().forEach { category ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(categoryColor(category), CircleShape))
                    Text(categoryLabel(category, language), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color(0xFF203E32), CircleShape))
                Text(text(language, "Selected citizenship", "Выбранное гражданство"), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(caption, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun containsPoint(polygon: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var previous = polygon.last()
    for (current in polygon) {
        if (kotlin.math.abs(previous.first - current.first) < 180 &&
            ((current.second > y) != (previous.second > y)) &&
            x < (previous.first - current.first) * (y - current.second) / (previous.second - current.second) + current.first
        ) inside = !inside
        previous = current
    }
    return inside
}
