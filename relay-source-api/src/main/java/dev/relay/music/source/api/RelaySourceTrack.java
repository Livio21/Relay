package dev.relay.music.source.api;

public final class RelaySourceTrack {
    private final String id;
    /** May be null when the source resolves streams lazily through {@code resolveStreamUrl}. */
    private final String streamUrl;
    private final String title;
    private final String artist;
    private final String album;
    private final String albumArtist;
    private final String releaseDate;
    private final Long durationMs;
    private final String artworkUrl;

    public RelaySourceTrack(String id, String streamUrl, String title, String artist, String album, Long durationMs, String artworkUrl) {
        this(id, streamUrl, title, artist, album, null, null, durationMs, artworkUrl);
    }

    /**
     * Extended metadata constructor. Existing source APKs may continue using the seven-argument
     * constructor above.
     */
    public RelaySourceTrack(String id, String streamUrl, String title, String artist, String album, String albumArtist, String releaseDate, Long durationMs, String artworkUrl) {
        this.id = id;
        this.streamUrl = streamUrl;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArtist = albumArtist;
        this.releaseDate = releaseDate;
        this.durationMs = durationMs;
        this.artworkUrl = artworkUrl;
    }

    public String getId() { return id; }
    public String getStreamUrl() { return streamUrl; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getAlbumArtist() { return albumArtist; }
    public String getReleaseDate() { return releaseDate; }
    public Long getDurationMs() { return durationMs; }
    public String getArtworkUrl() { return artworkUrl; }
}
