package dev.relay.music.source.api;

/** Public binary API shared by Relay and trusted Android source-extension APKs. */
public final class RelaySourceApi {
    private RelaySourceApi() {
    }

    public static final int VERSION = 2;
    public static final String EXTENSION_FEATURE = "dev.relay.music.source.extension";
    public static final String METADATA_API_VERSION = "relay.source.api";
    public static final String METADATA_ENTRY_CLASS = "relay.source.class";
    public static final String METADATA_EXTENSION_ID = "relay.source.id";
}
