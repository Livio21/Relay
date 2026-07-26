plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":composeApp"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(kotlin("test"))
}

val nativeOutput = layout.buildDirectory.dir("generated/native/macos-arm64")
val compileNativeAudio = tasks.register<Exec>("compileNativeAudio") {
    val javaHome = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    }.get().metadata.installationPath.asFile
    val output = nativeOutput.get().file("librelay_audio.dylib").asFile
    doFirst { output.parentFile.mkdirs() }
    commandLine(
        "clang", "-dynamiclib", "-O2",
        "-I${javaHome.resolve("include")}",
        "-I${javaHome.resolve("include/darwin")}",
        "-o", output,
        file("src/main/cpp/relay_audio.c"),
        "-framework", "AudioToolbox", "-framework", "AudioUnit", "-framework", "CoreAudio",
    )
}

tasks.processResources {
    dependsOn(compileNativeAudio)
    from(nativeOutput) { into("native/macos-arm64") }
}

compose.desktop {
    application {
        mainClass = "dev.relay.music.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "Relay"
            packageVersion = "1.0.0"
        }
    }
}
