package dev.relay.music.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One-line text that never changes height. Short strings render statically; overflowing strings
 * get a soft edge fade and a slow loop. The full string remains in the semantics tree.
 *
 * This is the host-wide replacement for ad-hoc `maxLines + Ellipsis` titles so dense Metro rows
 * stay aligned while a long title can still be read.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelayMarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    fadeEdges: Boolean = true,
    fadeColor: Color = RelayColors.Ink,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val measurer = rememberTextMeasurer()
        val widthPx = constraints.maxWidth
        val overflows = if (constraints.hasBoundedWidth) {
            remember(text, style, widthPx) {
                val measured = measurer.measure(
                    text = AnnotatedString(text),
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    constraints = Constraints(maxWidth = widthPx),
                )
                measured.hasVisualOverflow || measured.size.width > widthPx
            }
        } else {
            false
        }
        BasicText(
            text = text,
            style = if (textAlign == null) style else style.copy(textAlign = textAlign),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 900,
                    repeatDelayMillis = 1_100,
                    velocity = 26.dp,
                ),
        )
        if (fadeEdges && overflows) {
            Box(
                modifier = Modifier.matchParentSize().drawBehind {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.86f to Color.Transparent,
                            1f to fadeColor,
                        ),
                    )
                },
            )
        }
    }
}

/** A compact row label variant; avoids repeating the marquee call at every dense list site. */
@Composable
fun RelayMarqueeLabel(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        RelayMarqueeText(text = text, style = style)
    }
}
