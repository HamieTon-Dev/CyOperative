import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * A release-signing value that must never be committed. Looked up in an
 * untracked `secrets.properties` at the repo root, then Gradle properties
 * (`~/.gradle/gradle.properties` / `-P`), then the environment
 * (`cyberop.keystore.path` -> `CYBEROP_KEYSTORE_PATH`). See RELEASING.md.
 */
fun Project.secret(name: String): String? {
    val local = rootProject.file("secrets.properties")
    if (local.exists()) {
        val p = Properties()
        local.inputStream().use(p::load)
        p.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    (findProperty(name) as String?)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return System.getenv(name.uppercase().replace('.', '_'))?.trim()?.takeIf { it.isNotEmpty() }
}

val uploadKeystore: File? = project.secret("cyberop.keystore.path")?.let { file(it) }?.takeIf { it.isFile }

android {
    // FINAL application identity (owner, 2026-10-08). Never change it after the
    // first Play Console upload.
    namespace = "com.cyberoperative.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyberoperative.game"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "0.6.0"
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
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
