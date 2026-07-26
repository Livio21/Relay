# Relay

Android-first music player built with Kotlin Multiplatform and Compose Multiplatform.

Start with [the project guide](docs/PROJECT_GUIDE.md) for the module map, runtime flow, and safe places to make changes. The ordered implementation work is in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

## Prerequisites

- JDK 17
- Android SDK Platform 36 and Android SDK Build-Tools

## Build

```sh
./gradlew tasks
./gradlew :composeApp:allTests
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:packageDmg
```

Install the debug APK with:

```sh
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## Last.fm

Create a Last.fm API application, then add its credentials to the untracked `local.properties` file:

```properties
LASTFM_API_KEY=your_api_key
LASTFM_SHARED_SECRET=your_shared_secret
```

Use `CONNECT LAST.FM` in Relay, approve access in the browser, then select `FINISH CONNECTION` in the app. The session key is encrypted with Android Keystore; pending scrobbles stay in the local database until Last.fm accepts them.
