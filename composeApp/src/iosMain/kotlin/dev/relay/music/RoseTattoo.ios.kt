package dev.relay.music

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import relay.composeapp.generated.resources.Res
import relay.composeapp.generated.resources.rose_tattoo

@Composable
internal actual fun RoseTattoo(modifier: Modifier) {
    Image(
        painter = painterResource(Res.drawable.rose_tattoo),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
