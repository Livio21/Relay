package dev.relay.music.source.api;

/** One browseable listing a source offers, for example "Popular" or a genre shelf. */
public final class RelaySourceListing {
    private final String id;
    private final String name;

    public RelaySourceListing(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
