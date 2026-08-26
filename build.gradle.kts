plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.ksp) apply false
    kotlin("jvm") version "2.4.10"
}

group = "dev.relay"
version = "0.1.0"

dependencies {
    testImplementation(kotlin("test"))
}
kotlin {
    jvmToolchain(8)
}