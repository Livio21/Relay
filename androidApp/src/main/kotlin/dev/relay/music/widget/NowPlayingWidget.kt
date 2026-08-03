package dev.relay.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.LocalAppWidgetOptions
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.relay.music.MainActivity
import dev.relay.music.formatDuration
import dev.relay.music.library.LocalArtworkCache
import dev.relay.music.library.RoomNowPlayingSnapshotStore
import dev.relay.music.library.UserLibraryStore
import dev.relay.music.playback.NowPlayingSnapshot
import dev.relay.music.playback.PlaybackService
import dev.relay.music.playback.externalSurfaceSnapshot
import dev.relay.music.wallpaper.sampleSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val INK = ColorProvider(Color(0xFF101010))
private val PAPER = ColorProvider(Color(0xFFF3F0E8))
private val MUTED = ColorProvider(Color(0xFF92928B))
private val SIGNAL = ColorProvider(Color(0xFF4B88FF))

class NowPlayingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(120.dp, 72.dp), DpSize(240.dp, 72.dp), DpSize(300.dp, 144.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (snapshot, showLockscreenMetadata) = withContext(Dispatchers.IO) {
            val dao = UserLibraryStore.database(context).userLibraryDao()
            externalSurfaceSnapshot(
                RoomNowPlayingSnapshotStore(dao).read(),
                PlaybackService.sessionPlaying,
            ) to (dao.settingsSnapshot()?.asSettings()?.showLockscreenMetadata ?: false)
        }
        val artwork = withContext(Dispatchers.IO) { decodeWidgetArtwork(context, snapshot?.artworkCacheKey) }
        provideContent { WidgetContent(snapshot, artwork, showLockscreenMetadata) }
    }
}

@Composable
private fun WidgetContent(snapshot: NowPlayingSnapshot?, artwork: Bitmap?, showLockscreenMetadata: Boolean) {
    val size = LocalSize.current
    val sizeClass = widgetSizeClass(size.width.value, size.height.value)
    val bodyWidth = (size.width - size.height).coerceAtLeast(48.dp)
    val keyguard = LocalAppWidgetOptions.current.getInt(
        AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
    ) == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
    Row(
        modifier = GlanceModifier.fillMaxSize().background(INK).clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artwork != null) {
            Image(
                provider = ImageProvider(artwork),
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxHeight().width(size.height),
            )
        } else {
            Column(
                modifier = GlanceModifier.fillMaxHeight().width(size.height).background(ColorProvider(Color(0xFF1A1A1A))),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { Text("RELAY", style = TextStyle(color = MUTED), maxLines = 1) }
        }
        if (sizeClass == WidgetSizeClass.COMPACT) {
            WidgetAction(if (snapshot?.isPlaying == true) "PAUSE" else "PLAY", PlaybackService.ACTION_WIDGET_PLAY_PAUSE, GlanceModifier.width(bodyWidth))
            return@Row
        }
        Column(modifier = GlanceModifier.width(bodyWidth).fillMaxHeight().padding(12.dp)) {
            if (keyguard && !showLockscreenMetadata) {
                Text("RELAY", style = TextStyle(color = PAPER), maxLines = 1)
            } else {
                Text(snapshot?.title ?: "NO ACTIVE TRACK", style = TextStyle(color = PAPER), maxLines = 1)
                Text(snapshot?.artist ?: "RELAY", style = TextStyle(color = MUTED), maxLines = 1)
                if (sizeClass == WidgetSizeClass.EXPANDED) {
                    snapshot?.album?.takeIf(String::isNotBlank)?.let { Text(it, style = TextStyle(color = MUTED), maxLines = 1) }
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            if (sizeClass == WidgetSizeClass.EXPANDED && snapshot != null) {
                Text(
                    "${formatDuration(snapshot.positionMs)} / ${formatDuration(snapshot.durationMs)}",
                    style = TextStyle(color = MUTED),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(4.dp))
                LinearProgressIndicator(
                    progress = if (snapshot.durationMs > 0) (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f) else 0f,
                    color = SIGNAL,
                    backgroundColor = ColorProvider(Color(0xFF333333)),
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                )
                Spacer(GlanceModifier.height(8.dp))
            }
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WidgetAction("PREV", PlaybackService.ACTION_WIDGET_PREVIOUS)
                Spacer(GlanceModifier.width(16.dp))
                WidgetAction(if (snapshot?.isPlaying == true) "PAUSE" else "PLAY", PlaybackService.ACTION_WIDGET_PLAY_PAUSE)
                Spacer(GlanceModifier.width(16.dp))
                WidgetAction("NEXT", PlaybackService.ACTION_WIDGET_NEXT)
            }
        }
    }
}

@Composable
private fun WidgetAction(label: String, action: String, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text = label,
        style = TextStyle(color = SIGNAL),
        maxLines = 1,
        modifier = modifier.clickable(
            actionRunCallback<WidgetPlaybackAction>(actionParametersOf(WIDGET_ACTION_KEY to action)),
        ).padding(8.dp),
    )
}

class WidgetPlaybackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[WIDGET_ACTION_KEY] ?: return
        withContext(Dispatchers.IO) {
            val future = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java)),
            ).buildAsync()
            val controller = runCatching { future.get(5, TimeUnit.SECONDS) }.getOrElse {
                future.cancel(true)
                return@withContext
            }
            try {
                when (action) {
                    PlaybackService.ACTION_WIDGET_PREVIOUS -> controller.seekToPreviousMediaItem()
                    PlaybackService.ACTION_WIDGET_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                    PlaybackService.ACTION_WIDGET_NEXT -> controller.seekToNextMediaItem()
                }
            } finally {
                controller.release()
            }
        }
        NowPlayingWidget().update(context, glanceId)
    }
}

internal enum class WidgetSizeClass { COMPACT, MEDIUM, EXPANDED }

internal fun widgetSizeClass(widthDp: Float, heightDp: Float): WidgetSizeClass = when {
    heightDp >= 120f -> WidgetSizeClass.EXPANDED
    widthDp >= 200f -> WidgetSizeClass.MEDIUM
    else -> WidgetSizeClass.COMPACT
}

private fun decodeWidgetArtwork(context: Context, artworkCacheKey: String?): Bitmap? {
    val file = LocalArtworkCache(java.io.File(context.cacheDir, "relay-artwork")).resolve(artworkCacheKey) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 256, 256)
    })
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

private val WIDGET_ACTION_KEY = ActionParameters.Key<String>("relay.widget.action")
