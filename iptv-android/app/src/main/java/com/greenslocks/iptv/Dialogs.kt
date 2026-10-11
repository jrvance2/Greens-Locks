package com.greenslocks.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

data class MenuItem(
    val label: String,
    val enabled: Boolean = true,
    /** false keeps the dialog open so toggles can update their own label. */
    val close: Boolean = true,
    val action: () -> Unit,
)

data class TextPrompt(val title: String, val initial: String, val onDone: (String) -> Unit)

enum class PinResult { CLOSE, KEEP, WRONG }

data class PinRequest(val title: String, val onPin: (String) -> PinResult)

@Composable
fun ActionMenuDialog(title: String, items: List<MenuItem>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = Palette.surface, tonalElevation = 0.dp) {
            Column(Modifier.padding(16.dp).widthIn(min = 320.dp, max = 520.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    items.forEachIndexed { i, item ->
                        ListRow(
                            item.label, "", selected = false,
                            modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).padding(vertical = 2.dp),
                        ) {
                            if (item.enabled) { item.action(); if (item.close) onDismiss() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextPromptDialog(prompt: TextPrompt, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(prompt.initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.title) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it }, singleLine = true,
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { ActionButton("OK", onClick = { prompt.onDone(value); onDismiss() }) },
        dismissButton = { ActionButton("Cancel", primary = false, onClick = onDismiss) },
    )
}

/** On-screen number pad that works with a D-pad. Calls [onDone] after the 4th digit. */
@Composable
fun PinPadDialog(title: String, error: String?, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var digits by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(18.dp), color = Palette.surface, tonalElevation = 0.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "●".repeat(digits.length) + "○".repeat(4 - digits.length),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                val rows = listOf(
                    listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
                    listOf("⌫", "0", "Cancel"),
                )
                rows.forEachIndexed { r, row ->
                    Row {
                        row.forEachIndexed { c, label ->
                            FocusCard(
                                Modifier.padding(4.dp).width(92.dp)
                                    .then(if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier),
                                RoundedCornerShape(10.dp), 1.06f, 2.dp,
                                onClick = {
                                    when (label) {
                                        "\u232B" -> digits = digits.dropLast(1)
                                        "Cancel" -> onCancel()
                                        else -> if (digits.length < 4) {
                                            digits += label
                                            if (digits.length == 4) onDone(digits)
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    label, Modifier.fillMaxWidth().background(Palette.surfaceHi).padding(vertical = 12.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
