package dev.relay.music

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.asAwtTransferable
import java.awt.datatransfer.DataFlavor

@OptIn(ExperimentalComposeUiApi::class)
internal actual suspend fun Clipboard.readPlainText(): String? = runCatching {
    getClipEntry()?.asAwtTransferable
        ?.takeIf { it.isDataFlavorSupported(DataFlavor.stringFlavor) }
        ?.getTransferData(DataFlavor.stringFlavor) as? String
}.getOrNull()
