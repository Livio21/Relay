package dev.relay.music

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard

@OptIn(ExperimentalComposeUiApi::class)
internal actual suspend fun Clipboard.readPlainText(): String? = getClipEntry()?.getPlainText()
