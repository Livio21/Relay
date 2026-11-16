package dev.relay.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import dev.relay.music.haptics.HapticAction
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayIcon
import dev.relay.music.ui.RelayIconButton
import dev.relay.music.ui.RelayIcons
import dev.relay.music.ui.RelayType

internal data class MetroSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

/**
 * Metro hub: the current section title is the menu trigger; only a 64dp slice of the next
 * section's title peeks in at the right edge. The page itself stays full width.
 */
@Composable
internal fun MetroPageHeading(
    title: String,
    nextTitle: String,
    onOpenSections: () -> Unit,
    onNext: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backdropArtworkUri: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .zIndex(1f),
    ) {
        if (backdropArtworkUri != null) {
            AsyncImage(
                model = backdropArtworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(26.dp).alpha(0.85f),
            )
        }
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to RelayColors.Ink.copy(alpha = 0.84f),
                    0.7f to RelayColors.Ink.copy(alpha = 0.74f),
                    1f to RelayColors.Ink.copy(alpha = 0.64f),
                ),
            ),
        )
        if (onBack != null) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RelayIconButton(
                    icon = RelayIcons.Back,
                    description = "Back",
                    onClick = onBack,
                    modifier = Modifier.padding(start = 4.dp),
                )
                BasicText(
                    text = title.lowercase(),
                    style = RelayType.SectionTitle.copy(color = RelayColors.Paper),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 64.dp)
                        .padding(start = 12.dp)
                        .clickable(role = Role.Button) { onOpenSections() }
                        .semantics { contentDescription = "Open sections" },
                    verticalAlignment = Alignment.Bottom,
                ) {
                    BasicText(
                        text = title.lowercase(),
                        style = RelayType.SectionTitle.copy(color = RelayColors.Paper),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f),
                    )
                    RelayIcon(
                        icon = RelayIcons.ChevronDown,
                        description = null,
                        tint = RelayColors.Signal,
                        size = 20.dp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(64.dp)
                        .height(64.dp)
                        .clipToBounds()
                        .clickable(role = Role.Button) { onNext() }
                        .semantics { contentDescription = "Go to ${nextTitle.lowercase()}" }
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    BasicText(
                        text = nextTitle.lowercase(),
                        style = RelayType.SectionTitle.copy(color = RelayColors.Muted),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/** Full-screen, dark section chooser. The host owns the overlay; no pack-provided UI. */
@Composable
internal fun MetroSectionMenu(
    sections: List<MetroSection>,
    currentIndex: Int,
    artworkUri: String?,
    signature: String,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val dismissInteraction = remember { MutableInteractionSource() }
    Box(modifier = Modifier.fillMaxSize().background(RelayColors.Ink)) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(18.dp).alpha(0.78f),
            )
        }
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to RelayColors.Ink.copy(alpha = 0.04f),
                    0.55f to RelayColors.Ink.copy(alpha = 0.22f),
                    1f to RelayColors.Ink.copy(alpha = 0.58f),
                ),
            ),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val topHeight = maxHeight * 0.30f
            val listHeight = maxHeight * 0.60f
            val bottomHeight = maxHeight * 0.10f
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topHeight)
                        .clickable(
                            interactionSource = dismissInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = onClose,
                        )
                        .semantics { contentDescription = "Close sections menu" },
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (signature.isNotBlank()) {
                            BasicText(
                                text = signature,
                                style = RelayType.SectionTitle.copy(
                                    fontSize = 32.sp,
                                    lineHeight = 34.sp,
                                    fontWeight = FontWeight.Light,
                                    fontStyle = FontStyle.Italic,
                                    color = RelayColors.Paper.copy(alpha = 0.62f),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(listHeight)
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.08f to RelayColors.Ink.copy(alpha = 0.80f),
                                    0.92f to RelayColors.Ink.copy(alpha = 0.80f),
                                    1f to Color.Transparent,
                                ),
                            )
                        },
                ) {
                    sections.forEachIndexed { index, section ->
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable(role = Role.Button) { onSelect(index) }
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RelayIcon(
                                icon = section.icon,
                                description = null,
                                tint = if (isCurrent) RelayColors.Signal else RelayColors.Paper,
                                size = 24.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                BasicText(
                                    text = section.title.lowercase(),
                                    style = RelayType.SectionTitle.copy(
                                        fontSize = 28.sp,
                                        lineHeight = 30.sp,
                                        color = if (isCurrent) RelayColors.Signal else RelayColors.Paper,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                                BasicText(
                                    text = section.subtitle,
                                    style = RelayType.Kicker.copy(fontSize = 8.sp),
                                )
                            }
                            RelayIcon(
                                icon = RelayIcons.ChevronRight,
                                description = null,
                                tint = if (isCurrent) RelayColors.Signal else RelayColors.Muted,
                                size = 20.dp,
                            )
                        }
                        if (index != sections.lastIndex) Rule()
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomHeight)
                        .clickable(
                            interactionSource = dismissInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = onClose,
                        )
                        .semantics { contentDescription = "Close sections menu" },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    RoseTattoo(
                        Modifier
                            .padding(end = 24.dp, top = 8.dp, bottom = 4.dp)
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .alpha(0.55f),
                    )
                }
            }
        }
    }
}



@Composable
internal expect fun RoseTattoo(modifier: Modifier)
