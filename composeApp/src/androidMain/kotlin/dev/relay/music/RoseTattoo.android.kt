package dev.relay.music

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import dev.relay.music.shared.R

@Composable
internal actual fun RoseTattoo(modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.rose_tattoo),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
