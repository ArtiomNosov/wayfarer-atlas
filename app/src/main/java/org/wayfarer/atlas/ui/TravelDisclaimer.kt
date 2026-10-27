package org.wayfarer.atlas.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun TravelDisclaimerDialog(language: String, onAcknowledge: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag("travel-disclaimer"),
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(text(language, "Before you travel", "Перед поездкой")) },
        text = {
            Text(
                text(language,
                    "This app provides reference information about entry requirements. Data may be incomplete, inaccurate or out of date.\n\nBefore buying tickets, submitting applications and every trip, check current requirements with official immigration authorities or the destination country's consulates.",
                    "Приложение предоставляет справочную информацию об условиях въезда. Данные могут быть неполными, неточными или устаревшими.\n\nПеред покупкой билетов, подачей документов и каждой поездкой проверяйте действующие требования в официальных иммиграционных службах или консульствах страны назначения."),
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            Button(onClick = onAcknowledge, modifier = Modifier.heightIn(min = 48.dp).testTag("acknowledge-disclaimer")) {
                Text(text(language, "Understood", "Понятно"))
            }
        }
    )
}
