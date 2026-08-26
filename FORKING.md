# Forking Relay

Relay is licensed for noncommercial use with attribution (code: PolyForm
Noncommercial 1.0.0; docs/assets: CC BY-NC-SA 4.0). You may fork, modify,
build, and share your version freely for noncommercial purposes — including
accepting donations for it. Paywalling features of your fork violates both
licenses. Keep `LICENSE`, `LICENSE-DOCS`, and attribution notices intact,
and state clearly that your fork is modified if you distribute it.

## Rebrand checklist

If you rename or rebrand (recommended so users can tell forks apart):

1. `androidApp/build.gradle.kts` → `namespace` and `applicationId`
   (e.g. `dev.example.myplayer`). Changing the application ID lets your
   build install alongside upstream.
2. `androidApp/src/main/AndroidManifest.xml` → `android:label`.
3. `androidApp/src/main/res/values/strings.xml` → add/update `app_name`.
4. Shared UI header strings live in `composeApp` screens (`App.kt`,
   screen files) — search commonMain for the product name.
5. Backup archives keep the `.relaybackup` format; the versioned manifest
   is app-ID independent, so upstream and forked builds can restore each
   other's user-data backups.
6. Widget and wallpaper labels come from the manifest/resources above;
   re-check the Glance widget description string.

## Hosting your own extension catalog

Source extensions ship through signed catalogs you can host yourself:

1. Fork <https://github.com/Livio21/relay-extensions> (a standalone repo
   with its own Gradle build).
2. Generate signing keys per its `scripts/sign-index.sh`; never commit the
   private key. The repository descriptor (`repository.json`) carries the
   public key fingerprint users review on import.
3. Build source APKs against **Source API v2**: start from the
   `relay-android-extension-template` layout, extend `BaseRelaySource`
   (never implement `RelaySource` directly — see `docs/SOURCE_API.md` for
   why), set the manifest meta-data, and respect host-enforced bounds.
4. Update `index.json` entries (version, api range, artifact URL, sha256,
   size), re-sign, publish the APK at a stable HTTPS URL, and push
   `index.json` + `index.json.sig`.
5. Users import via Settings → SOURCE REPOSITORIES (raw GitHub URLs or
   `owner/repo` shorthand). Relay verifies signatures before trusting a
   catalog; nothing installs without explicit confirmation.

## Keeping ABI compatibility

- Build extensions against the same `relay-source-api` VERSION your target
  app loads (v2 today). A v1-built extension is refused by a v2 host.
- Do not widen the API surface in your fork casually: extension APKs in the
  wild compile against a frozen Java ABI. Batch additive changes and bump
  the API version deliberately.

## Self-supplied credentials

Last.fm API keys are untracked `local.properties` values, not baked into the
repository. Every fork supplies its own keys and its own Last.fm API
application. Never commit them.

## CI and releases

The included `.github/workflows/android-ci.yml` runs shared + Android tests
and an assembleDebug gate on every push/PR. To publish releases from your
fork follow `docs/RELEASE_CHECKLIST.md`: bump `RELAY_VERSION`, sign with
your own keystore (untracked `keystore.properties`), attach APK +
checksums to a GitHub Release.
