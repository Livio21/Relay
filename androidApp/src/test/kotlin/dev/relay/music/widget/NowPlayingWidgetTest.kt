package dev.relay.music.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingWidgetTest {
    @Test fun sizeBucketsStayDeterministic() {
        assertEquals(WidgetSizeClass.COMPACT, widgetSizeClass(120f, 72f))
        assertEquals(WidgetSizeClass.MEDIUM, widgetSizeClass(240f, 72f))
        assertEquals(WidgetSizeClass.EXPANDED, widgetSizeClass(240f, 144f))
    }

}
