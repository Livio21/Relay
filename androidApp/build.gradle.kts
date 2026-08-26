import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use(::load)
}

// Version single source: RELAY_VERSION in gradle.properties (see IMPLEMENTATION_PLAN.md Phase 10).
val relayVersion = providers.gradleProperty("RELAY_VERSION").get()
val relayVersionParts = relayVersion.removeSuffix("-SNAPSHOT").split('.').map { it.toInt() }
check(relayVersionParts.size == 3) { "RELAY_VERSION must be MAJOR.MINOR.PATCH, got '$relayVersion'" }
// versionCode = major*100000 + minor*1000 + patch*10; the trailing digit stays free for build metadata.
val relayVersionCode = relayVersionParts[0] * 100_000 + relayVersionParts[1] * 1_000 + relayVersionParts[2] * 10

// Release signing reads an untracked keystore.properties so forks build out of the box.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use(::load)
}

fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose.compiler)
}

android {
    namespace = "dev.relay.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.relay.music"
        minSdk = 23
        targetSdk = 36
        versionCode = relayVersionCode
        versionName = relayVersion
        buildConfigField(
            "String",
            "LASTFM_API_KEY",
            buildConfigString(localProperties.getProperty("LASTFM_API_KEY").orEmpty()),
        )
        buildConfigField(
            "String",
            "LASTFM_SHARED_SECRET",
            buildConfigString(localProperties.getProperty("LASTFM_SHARED_SECRET").orEmpty()),
        )
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Unsigned release falls back to the debug key so a clean clone always builds;
            // with keystore.properties present it is signed for publishing.
            signingConfig = if (keystoreProperties.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":relay-source-api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.extractor)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room3.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime)
    // LAN pairing: the host renders a join-link QR; guests scan it instead of typing addresses.
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    ksp(libs.room3.compiler)
    testImplementation(kotlin("test-junit"))
    // Real org.json for unit tests; the android.jar stub throws "not mocked".
    testImplementation("org.json:json:20240303")
}
