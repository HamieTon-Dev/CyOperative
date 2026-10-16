import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/*
 * Cyber Operative for Steam (Windows + Linux/Steam Deck).
 *
 * Compose Desktop app that compiles the game straight from the Android module's sources
 * (engine, data, UI), swapping only the platform seam: com.cyberoperative.game.platform,
 * audio, save storage and the co-op backend have desktop versions under src/main/kotlin.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

val sharedSrc = rootProject.file("app/src/main/java")
val rawRes = rootProject.file("app/src/main/res/raw")

/** Version shared with the Android build (single source of truth: app/build.gradle.kts). */
val appGradle = rootProject.file("app/build.gradle.kts").readText()
val versionCodeAndroid = Regex("""versionCode = (\d+)""").find(appGradle)!!.groupValues[1].toInt()
val versionNameAndroid = Regex("""versionName = "([^"]+)"""").find(appGradle)!!.groupValues[1]

/** -PdebugBuild: a PC test build (see compose.desktop below). */
val debugBuild = project.hasProperty("debugBuild")

val generatedSrc = layout.buildDirectory.dir("generated/buildinfo")
val genBuildInfo = tasks.register("generateBuildInfo") {
    val out = generatedSrc
    inputs.property("code", versionCodeAndroid)
    inputs.property("name", versionNameAndroid)
    outputs.dir(out)
    doLast {
        val f = out.get().file("com/cyberoperative/game/platform/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(
            "package com.cyberoperative.game.platform\n\n" +
                "internal object BuildInfo {\n" +
                "    const val VERSION_NAME = \"$versionNameAndroid\"\n" +
                "    const val VERSION_CODE = $versionCodeAndroid\n" +
                "}\n"
        )
    }
}

/** Music and art copied out of the Android res tree into classpath resources. */
val genRes = layout.buildDirectory.dir("generated/desktopRes")
val copyAssets = tasks.register<Sync>("copyAndroidAssets") {
    from(rawRes) { include("*.ogg", "*.wav"); into("music") }
    from(rootProject.file("app/src/main/res/drawable")) { include("hamieton_banner.xml"); into("images") }
    into(genRes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    // Any installed JDK 17+ builds it (Android Studio's bundled JBR works).
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    sourceSets.named("main") {
        kotlin.srcDir(sharedSrc)
        kotlin.srcDir(genBuildInfo)
        // Android-only files: replaced by desktop versions (or not used on PC).
        kotlin.exclude(
            "com/cyberoperative/game/MainActivity.kt",
            "com/cyberoperative/game/CyberOperativeApp.kt",
            "com/cyberoperative/game/CrashLog.kt",
            "com/cyberoperative/game/platform/Platform.kt",
            "com/cyberoperative/game/audio/AudioManager.kt",
            "com/cyberoperative/game/save/SaveRepository.kt",
            "com/cyberoperative/game/net/FirebaseCoopBackend.kt",
            // Idle Runs widget, worker and notifications: Android only (owner, 0.37.0).
            "com/cyberoperative/game/widget/**"
        )
        resources.srcDir(copyAssets)
        // Gear icon atlases (Data Vault update) live in the shared Java resources.
        resources.srcDir(rootProject.file("app/src/main/resources"))
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.steamworks4j)
    implementation(libs.jna)
    implementation(libs.vorbisspi)
    implementation(libs.jamepad)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.cyberoperative.game.desktop.MainKt"
        jvmArgs += listOf("-Xmx1g", "-Dsun.java2d.uiScale.enabled=true")
        // Debug test build (-PdebugBuild): free test ◇ in the store when Steam isn't running,
        // a separate CyberOperative-Debug save, and [DEBUG] in the window title.
        if (debugBuild) jvmArgs += "-Dcyberop.debug=true"
        nativeDistributions {
            // Steam ships the app folder (createReleaseDistributable); installers are optional.
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CyberOperative"
            packageVersion = versionNameAndroid.substringBefore('-').let { if (it.startsWith("0.")) "1." + it.substring(2) else it }
            description = "Cyber Operative"
            vendor = "Hamieton"
            modules("java.instrument", "java.management", "jdk.unsupported", "java.naming", "java.desktop")
            windows {
                menuGroup = "Cyber Operative"
                upgradeUuid = "6b1a2c0e-5d1e-4c62-9f0a-2a8f3a6e9b11"
            }
            linux { packageName = "cyberoperative" }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}

// The Steam upload must never be a debug build.
tasks.matching { it.name == "createReleaseDistributable" || it.name.startsWith("packageRelease") }.configureEach {
    doFirst { if (debugBuild) throw GradleException("-PdebugBuild is for test builds only: drop it to build the Steam release.") }
}

// Developer gameplay clips (PNG frames; ffmpeg makes the video): gradlew :desktop:clip
tasks.register<JavaExec>("clip") {
    group = "verification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.cyberoperative.game.desktop.DevClipKt")
    args(layout.buildDirectory.dir("clip").get().asFile.absolutePath)
    jvmArgs("-Djava.awt.headless=true", "-Xmx3g")
}

// Developer screenshots of the landscape layouts: gradlew :desktop:shots
tasks.register<JavaExec>("shots") {
    group = "verification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.cyberoperative.game.desktop.DevShotsKt")
    args(layout.buildDirectory.dir("shots").get().asFile.absolutePath)
    jvmArgs("-Djava.awt.headless=true")
}
