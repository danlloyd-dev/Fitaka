import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Navigation 3 saves its back stack via @Serializable keys.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    // Screenshot tests: `./gradlew :app:recordRoborazziDebug` renders screens to PNG on the JVM.
    alias(libs.plugins.roborazzi)
}

// The permanent signing key lives outside the project and is never committed. Every build installed on the
// phone must be signed with it: Android refuses an update signed with a different key, and the uninstall that
// follows erases the app's data. Without the file (a fresh machine), debug builds fall back to Android's
// default debug key and release builds stay unsigned.
val signingFile = File(System.getProperty("user.home"), ".pitakaview-signing/keystore.properties")
val signing = Properties().apply { if (signingFile.isFile) signingFile.inputStream().use { load(it) } }

android {
    namespace = "com.pitakaview.app"
    // androidx core 1.19 / Compose BOM 2026.11.01 require compiling against API 37+.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.pitakaview.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (signingFile.isFile) {
            create("pitaka") {
                storeFile = File(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Same key as release, so a debug build and a release build can update each other on the phone.
            signingConfig = signingConfigs.findByName("pitaka") ?: signingConfig
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("pitaka")
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Room's Android runtime touches android.util.Log in JVM unit tests; return defaults instead of throwing.
        unitTests.isReturnDefaultValues = true
        // Robolectric screenshot tests need the real resources (fonts, themes).
        unitTests.isIncludeAndroidResources = true
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.room3.runtime)
    // Uses the phone's built-in SQLite, so no extra native library ships in the APK.
    implementation(libs.androidx.sqlite.framework)
    ksp(libs.androidx.room3.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.core)
    // Frosted-glass top/bottom bars (real blur on Android 12+, tinted see-through below).
    implementation(libs.haze)
    implementation(libs.haze.blur)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Desktop SQLite so DAO queries run in plain JVM unit tests (no emulator, no Robolectric).
    testImplementation(libs.androidx.sqlite.bundled.jvm)

    // Screenshot tests (test-only; nothing here ships in the app).
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    // Registers the empty ComponentActivity that Compose tests host content in (debug builds only).
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
