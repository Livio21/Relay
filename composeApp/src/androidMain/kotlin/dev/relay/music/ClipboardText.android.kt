package dev.relay.music

import androidx.compose.ui.platform.Clipboard

internal actual suspend fun Clipboard.readPlainText(): String? =
    getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
