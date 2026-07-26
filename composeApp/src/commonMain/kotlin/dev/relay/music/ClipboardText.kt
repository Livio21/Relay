package dev.relay.music

import androidx.compose.ui.platform.Clipboard

internal expect suspend fun Clipboard.readPlainText(): String?
