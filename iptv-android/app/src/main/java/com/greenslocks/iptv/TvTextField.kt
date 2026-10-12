@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.greenslocks.iptv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A text box that behaves on a TV: moving the D-pad onto it only highlights it. Press OK to start
 * typing (this is when the keyboard opens), and Back or the keyboard's Done goes back to the
 * highlighted box so you can keep moving around.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    startEditing: Boolean = false,
) {
    var editing by remember { mutableStateOf(startEditing) }
    var wasEditing by remember { mutableStateOf(false) }
    var gotFocus by remember { mutableStateOf(false) }
    var sawKeyboard by remember { mutableStateOf(false) }
    val outer = focusRequester ?: remember { FocusRequester() }
    val editReq = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeHeight = WindowInsets.ime.getBottom(LocalDensity.current)

    fun finish() {
        editing = false
        keyboard?.hide()
    }

    BackHandler(enabled = editing) { finish() }

    LaunchedEffect(editing) {
        if (editing) {
            wasEditing = true
            gotFocus = false
            sawKeyboard = false
            delay(60)
            runCatching { editReq.requestFocus() }
            keyboard?.show()
        } else if (wasEditing) {
            wasEditing = false
            repeat(10) { delay(50); if (runCatching { outer.requestFocus() }.isSuccess) return@LaunchedEffect }
        }
    }
    // If the on-screen keyboard is dismissed (its own Back/Done), leave typing mode too.
    LaunchedEffect(imeHeight, editing) {
        if (editing && imeHeight > 0) sawKeyboard = true
        else if (editing && sawKeyboard && imeHeight == 0) finish()
    }

    if (editing) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            label = { Text(label) },
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { finish() }),
            modifier = modifier.focusRequester(editReq).onFocusChanged {
                if (it.isFocused) gotFocus = true else if (gotFocus) finish()
            },
        )
    } else {
        FocusCard(
            modifier.focusRequester(outer).heightIn(min = 56.dp),
            RoundedCornerShape(10.dp), 1f, 2.dp,
            onClick = { editing = true },
        ) { focused ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label, color = Palette.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                val shown = when {
                    value.isEmpty() -> if (focused) "Press OK to type" else ""
                    visualTransformation is PasswordVisualTransformation -> "•".repeat(value.length.coerceAtMost(24))
                    else -> value
                }
                Text(
                    shown, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (value.isEmpty()) Palette.muted else Palette.text,
                )
            }
        }
    }
}
