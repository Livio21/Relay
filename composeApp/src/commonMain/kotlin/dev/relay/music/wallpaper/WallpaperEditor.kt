package dev.relay.music.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.relay.music.TransportAction
import dev.relay.music.formatDuration
import dev.relay.music.haptics.HapticAction
import dev.relay.music.haptics.LocalRelayHaptics
import dev.relay.music.model.Track
import dev.relay.music.model.resolveEffectiveMetadata
import dev.relay.music.ui.RelayColors
import dev.relay.music.ui.RelayType
import dev.relay.music.ui.asThemedTitle

/** Anchor-aware translation; x/y is the anchor point, so a move shifts both directly. */
fun moveElementLayoutBy(
    layout: WallpaperElementLayout,
    dxNorm: Float,
    dyNorm: Float,
): WallpaperElementLayout = layout.copy(
    x = (layout.x + dxNorm).coerceIn(0f, 1f),
    y = (layout.y + dyNorm).coerceIn(0f, 1f),
)

/** Corner-handle resize; the anchor point stays fixed, matching renderer behavior. */
fun resizeElementLayoutBy(
    layout: WallpaperElementLayout,
    dWidth: Float,
    dHeight: Float,
): WallpaperElementLayout = layout.copy(
    width = (layout.width + dWidth).coerceIn(0.01f, 1f),
    height = (layout.height + dHeight).coerceIn(0.005f, 1f),
)

/**
 * Snaps toward the canvas center line when close enough. Pure so the editor's feel is testable.
 */
fun snapNormalized(value: Float, threshold: Float = 0.02f): Float =
    if ((value - 0.5f).let { it >= -threshold && it <= threshold }) 0.5f else value

private const val UNDO_LIMIT = 25

/**
 * Direct-manipulation wallpaper composition editor: drag to move, corner handle to resize,
 * with an always-available fine-tune panel for exact values. Edits stay in a local draft
 * until SAVE, so the live wallpaper is not rewritten on every gesture.
 */
@Composable
internal fun WallpaperEditorScreen(
    initial: WallpaperPreset,
    track: Track?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    nowEpochMs: Long,
    onSave: (WallpaperPreset) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(initial) }
    var undoStack by remember { mutableStateOf(listOf<WallpaperPreset>()) }
    var redoStack by remember { mutableStateOf(listOf<WallpaperPreset>()) }
    var selected by remember { mutableIntStateOf(draft.elements.indexOfFirst { it !is WallpaperElement.Artwork }.coerceAtLeast(0)) }
    val haptics = LocalRelayHaptics.current

    fun update(transform: (WallpaperPreset) -> WallpaperPreset) {
        val next = transform(draft)
        if (next == draft) return
        undoStack = (undoStack + draft).takeLast(UNDO_LIMIT)
        redoStack = emptyList()
        draft = next
        selected = selected.coerceIn(0, (draft.elements.size - 1).coerceAtLeast(0))
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction("CANCEL", "Discard wallpaper edits", true, onCancel, Modifier.weight(1f))
            BasicText("EDIT WALLPAPER", style = RelayType.Track, modifier = Modifier.weight(2f).padding(top = 15.dp))
            TransportAction("SAVE", "Save the wallpaper composition", true, { onSave(draft) }, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportAction("UNDO", "Undo the last edit", undoStack.isNotEmpty(), {
                redoStack = (redoStack + draft).takeLast(UNDO_LIMIT)
                draft = undoStack.last()
                undoStack = undoStack.dropLast(1)
            }, Modifier.weight(1f))
            TransportAction("REDO", "Redo the undone edit", redoStack.isNotEmpty(), {
                undoStack = (undoStack + draft).takeLast(UNDO_LIMIT)
                draft = redoStack.last()
                redoStack = redoStack.dropLast(1)
            }, Modifier.weight(1f))
        }

        EditorCanvas(
            preset = draft,
            selectedIndex = selected,
            track = track,
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            nowEpochMs = nowEpochMs,
            onSelect = { index ->
                if (index != selected) {
                    haptics.perform(HapticAction.SELECT_TRACK)
                    selected = index
                }
            },
            onMove = { dx, dy -> update { preset -> preset.moveElement(selected) { moveElementLayoutBy(it, dx, dy) } } },
            onResize = { dw, dh -> update { preset -> preset.resizeElement(selected) { resizeElementLayoutBy(it, dw, dh) } } },
        )

        ElementRail(
            elements = draft.elements,
            selected = selected,
            onSelect = { index -> selected = index },
            onAdd = { label ->
                update { preset -> preset.copy(elements = preset.elements + defaultWallpaperElement(label)) }
                selected = draft.elements.lastIndex
            },
            onFront = { update { preset -> preset.moveElementOrder(selected, +1) }; selected = (selected + 1).coerceAtMost(draft.elements.lastIndex) },
            onBack = { update { preset -> preset.moveElementOrder(selected, -1) }; selected = (selected - 1).coerceAtLeast(0) },
            onRemove = {
                update { preset -> preset.copy(elements = preset.elements.filterIndexed { index, _ -> index != selected }) }
                selected = 0
            },
        )

        FineTunePanel(
            element = draft.elements.getOrNull(selected),
            onChange = { layout -> update { preset -> preset.withElementLayout(selected, layout) } },
        )

        BasicText(
            "Drag to move · drag the corner square to resize · fine-tune below for exact values.",
            style = RelayType.Metadata,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun WallpaperPreset.moveElement(index: Int, transform: (WallpaperElementLayout) -> WallpaperElementLayout): WallpaperPreset =
    copy(elements = elements.mapIndexed { i, element -> if (i == index) element.withLayout(transform(element.layout)) else element })

private fun WallpaperPreset.resizeElement(index: Int, transform: (WallpaperElementLayout) -> WallpaperElementLayout): WallpaperPreset =
    moveElement(index, transform)

private fun WallpaperPreset.withElementLayout(index: Int, layout: WallpaperElementLayout): WallpaperPreset =
    copy(elements = elements.mapIndexed { i, element -> if (i == index) element.withLayout(layout) else element })

private fun WallpaperPreset.moveElementOrder(index: Int, delta: Int): WallpaperPreset {
    val target = index + delta
    if (index < 0 || target < 0 || target > elements.lastIndex) return this
    return copy(elements = elements.toMutableList().apply {
        val item = removeAt(index)
        add(target, item)
    })
}

@Composable
private fun EditorCanvas(
    preset: WallpaperPreset,
    selectedIndex: Int,
    track: Track?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    nowEpochMs: Long,
    onSelect: (Int) -> Unit,
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .padding(top = 12.dp)
            .border(1.dp, RelayColors.Line)
            .background(canvasBackground(preset)),
    ) {
        val canvasW = maxWidth
        val canvasH = maxHeight

        // Real artwork behind the wireframe, cropped per the preset.
        track?.artworkUri?.takeIf { preset.canvas.artworkFit == WallpaperArtworkFit.FILL }?.let { uri ->
            AsyncImage(uri, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        track?.artworkUri?.takeIf { preset.canvas.artworkFit == WallpaperArtworkFit.FIT }?.let { uri ->
            AsyncImage(uri, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }

        preset.elements.forEachIndexed { index, element ->
            val bounds = wallpaperElementBounds(element.layout, canvasW.value, canvasH.value)
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .offset(x = canvasW * bounds.left, y = canvasH * bounds.top)
                    .width(canvasW * bounds.width)
                    .height(canvasH * bounds.height)
                    .semantics { contentDescription = "Select ${element.label} element" }
                    .clickable(role = Role.Button) { onSelect(index) }
                    .border(1.dp, if (isSelected) RelayColors.Signal else RelayColors.Line)
                    .background(if (element is WallpaperElement.Artwork) Color.Transparent else RelayColors.Ink.copy(alpha = 0.01f)),
            ) {
                EditorElementContent(element, track, positionMs, durationMs, nowEpochMs, canvasH)
                if (isSelected) {
                    // Bottom-right resize handle: drag outside the parent's clip via offset box.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .background(RelayColors.Signal)
                            .pointerInput(index, element.layout.width, element.layout.height, canvasW, canvasH) {
                                detectDragGestures { change, amount ->
                                    change.consume()
                                    onResize(
                                        pxToNorm(amount.x, size.width),
                                        pxToNorm(amount.y, size.height),
                                    )
                                }
                            }
                            .semantics { contentDescription = "Resize ${element.label}" },
                    )
                }
            }
        }
        // Canvas-level drag moves the selected element; taps pass through to hit-testing above.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedIndex, preset.elements.getOrNull(selectedIndex)?.layout?.x, preset.elements.getOrNull(selectedIndex)?.layout?.y, canvasW, canvasH) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val hit = hitTestElement(preset, selectedIndex, offset.x / size.width.toFloat(), offset.y / size.height.toFloat())
                            onSelect(hit)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val currentLayout = preset.elements.getOrNull(selectedIndex)?.layout
                            val dx = pxToNorm(amount.x, size.width)
                            val dy = pxToNorm(amount.y, size.height)
                            val snappedDx = currentLayout?.let { snapMove(dx, it.x) } ?: dx
                            val snappedDy = currentLayout?.let { snapMove(dy, it.y) } ?: dy
                            onMove(snappedDx, snappedDy)
                        },
                    )
                },
        )
    }
}

private fun pxToNorm(deltaPx: Float, sizePx: Int): Float =
    if (sizePx <= 0) 0f else deltaPx / sizePx

private fun snapMove(delta: Float, current: Float): Float {
    val next = current + delta
    val snapped = snapNormalized(next)
    return if (snapped != next) snapped - current else delta
}

private fun hitTestElement(preset: WallpaperPreset, fallback: Int, nx: Float, ny: Float): Int {
    for (index in preset.elements.indices.reversed()) {
        val bounds = wallpaperElementBounds(preset.elements[index].layout, 1f, 1f)
        if (nx >= bounds.left && nx <= bounds.left + bounds.width && ny >= bounds.top && ny <= bounds.top + bounds.height) {
            return index
        }
    }
    return fallback
}

private fun canvasBackground(preset: WallpaperPreset): Color {
    if (preset.canvas.background != WallpaperCanvasBackground.SOLID) return RelayColors.Ink
    val argb = preset.canvas.solidColorArgb
    return Color(
        red = ((argb shr 16) and 0xFFL) / 255f,
        green = ((argb shr 8) and 0xFFL) / 255f,
        blue = (argb and 0xFFL) / 255f,
        alpha = ((argb shr 24) and 0xFFL) / 255f,
    )
}

@Composable
private fun EditorElementContent(
    element: WallpaperElement,
    track: Track?,
    positionMs: Long,
    durationMs: Long,
    nowEpochMs: Long,
    canvasHeight: androidx.compose.ui.unit.Dp,
) {
    val effective = track?.resolveEffectiveMetadata()
    when (element) {
        is WallpaperElement.Title -> PreviewText(effective?.display?.title.orEmpty(), element.layout.font, canvasHeight)
        is WallpaperElement.Artist -> PreviewText(effective?.display?.artist.orEmpty(), element.layout.font, canvasHeight)
        is WallpaperElement.Album -> PreviewText(effective?.display?.album.orEmpty(), element.layout.font, canvasHeight)
        is WallpaperElement.Clock -> PreviewText(formatClock(nowEpochMs), element.layout.font, canvasHeight)
        is WallpaperElement.Progress -> Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .background(RelayColors.Signal),
            )
        }
        is WallpaperElement.Artwork -> Unit // the shared artwork layer already renders behind
    }
}

@Composable
private fun PreviewText(text: String, font: WallpaperFont, canvasHeight: androidx.compose.ui.unit.Dp) {
    if (text.isBlank()) return
    val factor = when (font) {
        WallpaperFont.METADATA -> 0.035f
        WallpaperFont.BODY -> 0.05f
        WallpaperFont.TITLE -> 0.075f
        WallpaperFont.DISPLAY -> 0.12f
    }
    val fontSize = with(LocalDensity.current) { (canvasHeight.value * factor).sp }
    BasicText(
        text = text.asThemedTitle(),
        style = RelayType.Track.copy(color = RelayColors.Paper, fontSize = fontSize),
        maxLines = 1,
        modifier = Modifier.padding(4.dp),
    )
}

/** Wallpapers show wall-clock time, not playback time; formatted from the host-supplied instant. */
internal fun formatClock(epochMs: Long): String {
    val totalMinutes = (epochMs / 60_000L).toInt()
    val minutesOfDay = ((totalMinutes % (24 * 60)) + 24 * 60) % (24 * 60)
    val hours = minutesOfDay / 60
    val minutes = minutesOfDay % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}

/** Horizontal chip strip for selecting, adding, ordering, and removing elements. */
@Composable
private fun ElementRail(
    elements: List<WallpaperElement>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onAdd: (String) -> Unit,
    onFront: () -> Unit,
    onBack: () -> Unit,
    onRemove: () -> Unit,
) {
    BasicText("ELEMENTS", style = RelayType.Utility.copy(color = RelayColors.Muted), modifier = Modifier.padding(top = 12.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        elements.forEachIndexed { index, element ->
            val active = index == selected
            BasicText(
                text = element.label,
                style = RelayType.Utility.copy(color = if (active) RelayColors.Signal else RelayColors.Muted),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .border(1.dp, if (active) RelayColors.Signal else RelayColors.Line)
                    .clickable(role = Role.Button) { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 15.dp),
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransportAction("BACK LAYER", "Move the element behind others", selected > 0, onBack, Modifier.weight(1f))
        TransportAction("FRONT LAYER", "Move the element in front of others", selected < elements.lastIndex, onFront, Modifier.weight(1f))
        TransportAction("REMOVE", "Remove the element", elements.isNotEmpty(), onRemove, Modifier.weight(1f))
    }
    val present = elements.map { it.label }.toSet()
    val missing = listOf("ARTWORK", "TITLE", "ARTIST", "ALBUM", "CLOCK", "PROGRESS").filterNot { it in present }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        missing.forEach { label ->
            TransportAction("+ $label", "Add a $label element", true, { onAdd(label) }, Modifier.weight(1f))
        }
    }
}

/** Exact-value controls; always synced with gestures so either input style works alone. */
@Composable
private fun FineTunePanel(element: WallpaperElement?, onChange: (WallpaperElementLayout) -> Unit) {
    element ?: return
    val l = element.layout
    fun set(transform: (WallpaperElementLayout) -> WallpaperElementLayout) = onChange(transform(l))

    @Composable
    fun Stepper(
        label: String,
        value: Float,
        text: String,
        step: Float,
        range: ClosedFloatingPointRange<Float>,
        update: (WallpaperElementLayout, Float) -> WallpaperElementLayout,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, style = RelayType.Utility.copy(color = RelayColors.Muted), modifier = Modifier.width(84.dp))
            TransportAction("-", "Decrease $label", value > range.start, { set { layout -> update(layout, (value - step).coerceIn(range)) } }, Modifier.weight(1f))
            BasicText(text, style = RelayType.Utility, modifier = Modifier.weight(2f), maxLines = 1)
            TransportAction("+", "Increase $label", value < range.endInclusive, { set { layout -> update(layout, (value + step).coerceIn(range)) } }, Modifier.weight(1f))
        }
    }

    fun percent(value: Float) = "${(value * 100).toInt()}%"

    BasicText("FINE TUNE · ${element.label}", style = RelayType.Utility.copy(color = RelayColors.Muted), modifier = Modifier.padding(top = 12.dp))
    Stepper("X", l.x, percent(l.x), 0.01f, 0f..1f) { layout, v -> layout.copy(x = v) }
    Stepper("Y", l.y, percent(l.y), 0.01f, 0f..1f) { layout, v -> layout.copy(y = v) }
    Stepper("WIDTH", l.width, percent(l.width), 0.01f, 0.01f..1f) { layout, v -> layout.copy(width = v) }
    Stepper("HEIGHT", l.height, percent(l.height), 0.005f, 0.005f..1f) { layout, v -> layout.copy(height = v) }
    Stepper("OPACITY", l.opacity, percent(l.opacity), 0.05f, 0f..1f) { layout, v -> layout.copy(opacity = v) }

    if (element !is WallpaperElement.Artwork && element !is WallpaperElement.Progress) {
        CycleRow("FONT", l.font.name.replaceFirstChar { it.uppercase() }) {
            val values = WallpaperFont.entries
            val next = values[(values.indexOf(l.font) + 1) % values.size]
            set { it.copy(font = next) }
        }
        CycleRow("ALIGN", l.alignment.name) {
            val values = WallpaperTextAlignment.entries
            val next = values[(values.indexOf(l.alignment) + 1) % values.size]
            set { it.copy(alignment = next) }
        }
    }
    CycleRow("VISIBLE ON", l.visibility.name.replace('_', ' ')) {
        val values = WallpaperVisibility.entries
        val next = values[(values.indexOf(l.visibility) + 1) % values.size]
        set { it.copy(visibility = next) }
    }

    BasicText("ANCHOR", style = RelayType.Utility.copy(color = RelayColors.Muted), modifier = Modifier.padding(top = 8.dp))
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WallpaperAnchor.entries.chunked(3).forEach { rowAnchors ->
            rowAnchors.forEach { anchor ->
                val active = anchor == l.anchor
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp)
                        .border(1.dp, if (active) RelayColors.Signal else RelayColors.Line)
                        .clickable(role = Role.Button) { set { it.copy(anchor = anchor) } }
                        .semantics { contentDescription = "Anchor ${anchor.name.replace('_', ' ').lowercase()}" },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        anchor.name.take(3),
                        style = RelayType.Utility.copy(color = if (active) RelayColors.Signal else RelayColors.Muted),
                    )
                }
            }
        }
    }
}

@Composable
private fun CycleRow(label: String, value: String, onCycle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = RelayType.Utility.copy(color = RelayColors.Muted), modifier = Modifier.width(84.dp))
        BasicText(value, style = RelayType.Utility, modifier = Modifier.weight(2f), maxLines = 1)
        TransportAction("CHANGE", "Change $label", true, onCycle, Modifier.weight(1f))
    }
}

