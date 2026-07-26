package dev.relay.music.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.relay.music.MainActivity
import dev.relay.music.R
import dev.relay.music.library.UserLibraryStore
import dev.relay.music.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NowPlayingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) {
            UserLibraryStore.database(context).userLibraryDao().nowPlayingSnapshot()?.asSnapshot()
        }
        provideContent {
            val title = snapshot?.title ?: "NO ACTIVE TRACK"
            val artist = snapshot?.artist ?: "RELAY"
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .background(ColorProvider(Color.White))
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text(title, style = TextStyle(color = ColorProvider(Color(0xFFF1F1EC))))
                Text(artist, style = TextStyle(color = ColorProvider(Color(0xFF92928B))))
                Spacer(GlanceModifier.defaultWeight())
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    WidgetAction(context, "PREV", PlaybackService.ACTION_WIDGET_PREVIOUS)
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetAction(
                        context,
                        if (snapshot?.isPlaying == true) "PAUSE" else "PLAY",
                        PlaybackService.ACTION_WIDGET_PLAY_PAUSE,
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetAction(context, "NEXT", PlaybackService.ACTION_WIDGET_NEXT)
                }
            }
        }
    }
}

@Composable
private fun WidgetAction(context: Context, label: String, action: String) {
    Text(
        text = label,
        style = TextStyle(color = ColorProvider(Color(0xFF4B88FF))),
        modifier = GlanceModifier.clickable(
            actionStartService(
                Intent(context, PlaybackService::class.java).setAction(action),
                isForegroundService = true,
            ),
        ),
    )
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
