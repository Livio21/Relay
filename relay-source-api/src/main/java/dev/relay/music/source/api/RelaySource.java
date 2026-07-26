package dev.relay.music.source.api;

import java.util.List;
import java.util.Map;

/**
 * A source owns its own site/API requests and returns normalised records to Relay.
 *
 * <p>Deliberately free of default methods: desugared interface defaults do not resolve reliably
 * across an APK boundary (the {@code $-CC} companion is private to each dex compilation).
 * Extend {@link BaseRelaySource} instead of implementing this interface directly — it provides
 * safe no-op implementations for everything optional.</p>
 */
public interface RelaySource {
    String getId();

    String getName();

    /**
     * Browse listings offered before any search, for example "Popular" or "Recently added".
     * An empty list means the source is search-only.
     */
    List<RelaySourceListing> getListings();

    /**
     * Empty text browses the source's default view; field searches use {@code title:},
     * {@code artist:}, or {@code album:}. Page numbering starts at 1. Return
     * {@link RelaySourcePage#getHasNextPage()} {@code true} only when the next page exists.
     */
    RelaySourcePage search(String query, int page) throws Exception;

    /** Browse one page of a listing returned by {@link #getListings()}. Pages start at 1. */
    RelaySourcePage browse(String listingId, int page) throws Exception;

    /**
     * Just-in-time stream lookup, called immediately before playback or download. A source that
     * cannot cheaply include {@code streamUrl} in search results (short-lived URLs, one page
     * fetch per track) returns tracks without one and resolves here instead.
     */
    String resolveStreamUrl(String trackId) throws Exception;

    /** Optional lazy artwork lookup for a selected track. Search results must not trigger one request per row. */
    String resolveArtworkUrl(String trackId) throws Exception;

    /**
     * Optional just-in-time download URL lookup. Hosts fall back to the resolved stream URL
     * when a source does not need a separate download endpoint.
     */
    String resolveDownloadUrl(String trackId) throws Exception;

    /**
     * Extra HTTP headers Relay attaches when it fetches this source's streams, artwork, and
     * downloads, for example {@code Referer} or a site-required {@code User-Agent}. Relay only
     * forwards a bounded allow-list of header names.
     */
    Map<String, String> getMediaRequestHeaders();

    /** User-editable source preferences, rendered by Relay's own settings UI. Max 16. */
    List<RelaySourceSetting> getSettings();

    /**
     * Stored preference values, applied right after Relay loads the source and again when the
     * user edits a value. Keys match {@link RelaySourceSetting#getId()}; unknown keys must be
     * ignored. Values are never seen by other extensions.
     */
    void applySettings(Map<String, String> values);
}
