# Third-party notices

Relay's original source code is licensed under the PolyForm Noncommercial
License 1.0.0 (`LICENSE`). Relay depends on the third-party works listed
below, which remain under their own licenses. Nothing in Relay's license
relicenses these dependencies.

## Runtime dependencies

| Dependency | Version | License |
| --- | --- | --- |
| androidx.activity:activity-compose | 1.13.0 | Apache-2.0 |
| androidx.documentfile:documentfile | 1.1.0 | Apache-2.0 |
| androidx.glance:glance-appwidget | 1.1.1 | Apache-2.0 |
| androidx.media3:media3-exoplayer / media3-session / media3-extractor | 1.10.1 | Apache-2.0 |
| androidx.room3:room3-runtime / room3-compiler | 3.0.0 | Apache-2.0 |
| androidx.work:work-runtime-ktx | 2.11.2 | Apache-2.0 |
| androidx.compose.ui:ui-tooling / ui-tooling-preview | 1.11.4 | Apache-2.0 |
| io.coil-kt.coil3:coil-compose | 3.5.0 | Apache-2.0 |
| io.ktor:ktor-client-* / ktor-serialization-kotlinx-json | 3.5.1 | Apache-2.0 |
| org.jetbrains.compose.* (Compose Multiplatform) | 1.11.1 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core / -android | 1.11.0 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.11.0 | Apache-2.0 |
| com.squareup.okio:okio | 3.16.20 | Apache-2.0 |
| com.google.zxing:core | 3.5.3 | Apache-2.0 |
| com.journeyapps:zxing-android-embedded | 4.3.0 | Apache-2.0 |

Apache License 2.0 text: <https://www.apache.org/licenses/LICENSE-2.0.txt>

## Test-only dependencies

| Dependency | Version | License |
| --- | --- | --- |
| org.json:json | 20240303 | JSON License (MIT-style, with the "shall be used for Good, not Evil" clause). Used in unit tests only; not shipped in release artifacts. |

## Vendored code

| Work | Location | License |
| --- | --- | --- |
| miniaudio (single-file C audio library) | `desktopApp/src/main/cpp/miniaudio.h` | Public domain or MIT-0, at the recipient's choice (dual statement embedded in the file header and at the end of the file). |

## Build-time tooling

Kotlin 2.4.10, Kotlin Multiplatform, Compose compiler plugin, Android Gradle
Plugin 9.1.1, KSP, Gradle 9.3.1 and the foojay toolchains resolver plugin are
build-time tooling distributed under Apache-2.0 terms. They are not part of
the application's distributed code.
