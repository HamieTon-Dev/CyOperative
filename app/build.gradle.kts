plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Online features (accounts, friends, co-op) use Firebase. The config file is
// per-owner and git-ignored: without app/google-services.json the game still
// builds and plays; the online screens explain how to enable them.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

/**
 * A release-signing value that must never be committed. Looked up in an
 * untracked `secrets.properties` at the repo root, then Gradle properties
 * (`~/.gradle/gradle.properties` / `-P`), then the environment
 * (`cyberop.keystore.path` -> `CYBEROP_KEYSTORE_PATH`). See RELEASING.md.
 */
fun Project.secret(name: String): String? {
    readSecretsFile()[name]?.let { return it }
    (findProperty(name) as String?)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return System.getenv(name.uppercase().replace('.', '_'))?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Reads `secrets.properties` literally: one `key=value` per line, `#` comments.
 * Not java.util.Properties, which eats backslashes (`C:\keys\x.jks` would
 * become `C:keysx.jks`) — the usual reason a Windows build came out unsigned.
 * Also tolerates a UTF-8 BOM from Notepad.
 */
fun Project.readSecretsFile(): Map<String, String> {
    val local = rootProject.file("secrets.properties")
    if (!local.exists()) return emptyMap()
    return local.readText().removePrefix("\uFEFF").lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
        .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim().removeSurrounding("\"") }
        .filterValues { it.isNotEmpty() }
}

val keystorePath: String? = project.secret("cyberop.keystore.path")
val uploadKeystore: File? = keystorePath?.let { file(it) }?.takeIf { it.isFile }

// A release build must never come out unsigned by accident: Play rejects it with
// "All uploaded bundles must be signed". Fail loudly and say what is missing.
gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { t ->
        t.project == project && t.name.endsWith("Release") &&
            (t.name.startsWith("bundle") || t.name.startsWith("assemble") || t.name.startsWith("package"))
    }
    if (wantsRelease && uploadKeystore == null && !project.hasProperty("allowUnsigned")) {
        val secrets = rootProject.file("secrets.properties")
        val why = when {
            !secrets.exists() && keystorePath == null ->
                "No ${secrets.path} found (and no cyberop.keystore.path property or CYBEROP_KEYSTORE_PATH variable)."
            keystorePath == null ->
                "${secrets.path} has no 'cyberop.keystore.path=' line (check spelling; it must be at the start of a line)."
            else -> "The keystore file does not exist: '$keystorePath' (resolved to ${file(keystorePath).path})."
        }
        throw GradleException(
            "Release signing is not set up, so the bundle would be unsigned.\n  $why\n" +
                "  Fix secrets.properties (see RELEASING.md), or pass -PallowUnsigned to build unsigned on purpose."
        )
    }
    if (wantsRelease && uploadKeystore != null) {
        logger.lifecycle("Signing release with upload key: ${uploadKeystore.path}")
    }
}

android {
    // FINAL application identity (owner, 2026-10-08). Never change it after the
    // first Play Console upload.
    namespace = "com.cyberoperative.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyberoperative.game"
        minSdk = 24
        targetSdk = 36
        versionCode = 44
        versionName = "0.12.2"
    }

    signingConfigs {
        // Google Play upload key, supplied from outside source control.
        create("upload") {
            if (uploadKeystore != null) {
                storeFile = uploadKeystore
                storePassword = project.secret("cyberop.keystore.password")
                keyAlias = project.secret("cyberop.key.alias") ?: "cyberoperative-upload"
                keyPassword = project.secret("cyberop.key.password") ?: storePassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Unsigned when no upload key is configured (the .aab must be signed before Play accepts it).
            signingConfig = if (uploadKeystore != null) signingConfigs.getByName("upload") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric renders the real Compose UI on the JVM (screenshot previews).
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperty("robolectric.logging.enabled", "false")
                it.systemProperty(
                    "robolectric.dependency.repo.url",
                    "https://maven-central.storage-download.googleapis.com/maven2"
                )
                it.systemProperty("robolectric.dependency.repo.id", "central-mirror")
                it.maxParallelForks = 1
                // Screenshot previews only run when asked: -PrenderPreviews
                it.systemProperty("cyberop.renderPreviews", project.hasProperty("renderPreviews").toString())
                it.systemProperty("cyberop.previewDir", rootProject.file("docs/screenshots").absolutePath)
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.kotlinx.coroutines.play.services)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
