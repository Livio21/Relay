package dev.relay.music.source.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Extend this instead of implementing {@link RelaySource} directly. It supplies safe no-op
 * implementations for every optional operation, and unlike interface default methods these
 * survive Android's desugaring across the extension APK boundary.
 */
public abstract class BaseRelaySource implements RelaySource {
    @Override
    public List<RelaySourceListing> getListings() {
        return Collections.emptyList();
    }

    @Override
    public RelaySourcePage browse(String listingId, int page) throws Exception {
        throw new UnsupportedOperationException("Source has no listings.");
    }

    @Override
    public String resolveStreamUrl(String trackId) throws Exception {
        return null;
    }

    @Override
    public String resolveArtworkUrl(String trackId) throws Exception {
        return null;
    }

    @Override
    public String resolveDownloadUrl(String trackId) throws Exception {
        return null;
    }

    @Override
    public Map<String, String> getMediaRequestHeaders() {
        return Collections.emptyMap();
    }

    @Override
    public List<RelaySourceSetting> getSettings() {
        return Collections.emptyList();
    }

    @Override
    public void applySettings(Map<String, String> values) {
    }
}
