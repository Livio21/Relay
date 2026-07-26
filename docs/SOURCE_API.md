# Relay source API v2

How to build an Android source extension for Relay. Start from `relay-android-extension-template`; the FMA source in the `relay-extensions` repository is the real-world reference.

## Contract

An extension APK declares in its manifest:

```xml
<meta-data android:name="relay.source.api" android:value="2" />
<meta-data android:name="relay.source.id" android:value="your.extension.id" />
<meta-data android:name="relay.source.class" android:value=".YourSourceFactory" />
```

and **extends `BaseRelaySource`** (or uses `RelaySourceFactory` for several sources from one APK). Do not implement the `RelaySource` interface directly: the interface is deliberately free of default methods because Android's desugared interface defaults (`$-CC` companions) do not resolve across the extension APK boundary — `BaseRelaySource` provides the safe no-op implementations instead.

| Method | Required | Purpose |
| --- | --- | --- |
| `getId()`, `getName()` | yes | Stable source identity. ID: `[a-z0-9][a-z0-9._-]{0,127}`. |
| `search(query, page)` | yes | Empty query browses the default view. `title:`/`artist:`/`album:` prefix the field. Pages start at 1; set `hasNextPage` only when the next page exists. |
| `getListings()` | no | Browse shelves shown before a query ("Popular", genres, …). Max 24. |
| `browse(listingId, page)` | with listings | One page of a listing. |
| `resolveStreamUrl(trackId)` | for lazy sources | Called just before playback/download. Return tracks **without** `streamUrl` from `search`/`browse` when stream URLs are short-lived or cost one request per track — this is the normal shape for scraped sites. |
| `resolveArtworkUrl(trackId)` | no | Lazy artwork for a selected track. Never one request per result row. |
| `resolveDownloadUrl(trackId)` | no | Only when downloads use a different endpoint than streaming. |
| `getMediaRequestHeaders()` | no | Headers Relay attaches to your stream/artwork/download requests. |
| `getSettings()` | no | User-editable preferences (max 16): `TEXT`, `TOGGLE`, or `CHOICE`. Relay renders them under the extension's details screen and stores values per extension. |
| `applySettings(values)` | with settings | Called right after load and on every user edit. Ignore unknown keys. Never declare secrets here. |

## Host-enforced bounds

Relay validates every response and disables a source that violates them:

- All URLs HTTPS, ≤ 8192 chars. Track IDs ≤ 512 chars. Titles/artists non-blank, ≤ 1024 chars.
- ≤ 100 tracks per page; ≤ 24 listings; queries arrive ≤ 256 chars.
- Timeouts: 5 s load/listings/artwork, 10 s search/browse/stream/download resolution.
- Media headers pass an allow-list (`User-Agent`, `Referer`, `Origin`, `Cookie`, `Authorization`, `Accept`), ≤ 8 entries, values ≤ 4096 chars, no CR/LF.
- A track with no `streamUrl` **must** resolve through `resolveStreamUrl`, or playback of it fails with a visible error.

## Eager vs lazy streams

- **API-backed source, stream URL free in the search response:** set `streamUrl` on each track; skip `resolveStreamUrl`.
- **Scraped source, or short-lived/signed URLs:** return `streamUrl = null` and implement `resolveStreamUrl`. Relay calls it once, just before playback or download, and attaches your media headers to the fetch.

## Versioning

`RelaySourceApi.VERSION` is 2. The manifest value, the factory's `getApiVersion()`, and the repository catalog entry's `api` range must all cover the host's version or Relay refuses to load the source (installed but disabled, with the reason shown). v1 extensions (single-argument `search`) are not loaded by a v2 host — rebuild against the v2 jar.

## Release checklist (repository owners)

1. Build the APK and note its byte size and `shasum -a 256`.
2. Update the catalog entry: `version`, `api`, `artifactUrl`, `sha256`, `artifactSizeBytes`.
3. Re-sign the catalog: `scripts/sign-index.sh`.
4. Publish the APK at `artifactUrl` and push `index.json` + `index.json.sig`.
