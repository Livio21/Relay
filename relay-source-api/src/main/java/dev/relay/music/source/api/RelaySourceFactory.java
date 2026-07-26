package dev.relay.music.source.api;

import java.util.List;

/** Lets an extension APK expose more than one source from a single manifest entry class. */
public interface RelaySourceFactory {
    int getApiVersion();

    List<RelaySource> createSources();
}
