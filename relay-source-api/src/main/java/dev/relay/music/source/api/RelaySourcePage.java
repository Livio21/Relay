package dev.relay.music.source.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RelaySourcePage {
    private final List<RelaySourceTrack> tracks;
    private final boolean hasNextPage;

    public RelaySourcePage(List<RelaySourceTrack> tracks) {
        this(tracks, false);
    }

    public RelaySourcePage(List<RelaySourceTrack> tracks, boolean hasNextPage) {
        // List.copyOf is API 31+ on Android; this ABI must run on minSdk 23 devices.
        this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
        this.hasNextPage = hasNextPage;
    }

    public List<RelaySourceTrack> getTracks() {
        return tracks;
    }

    public boolean getHasNextPage() {
        return hasNextPage;
    }
}
