import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties")
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
        versionCode = 1
        versionName = "0.1.0"
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
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    ksp(libs.room3.compiler)
    testImplementation(kotlin("test-junit"))
    // Real org.json for unit tests; the android.jar stub throws "not mocked".
    testImplementation("org.json:json:20240303")
}
