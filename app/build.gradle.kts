plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.localpipper.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.localpipper.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // PreferenceManager for SharedPreferences
    implementation("androidx.preference:preference:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.9")
}

// ビルド後に app-debug.apk を LocalPipper.apk としてプロジェクトルートにコピー
tasks.whenTaskAdded {
    if (name == "assembleDebug") {
        doLast {
            val src = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
            val dst = rootProject.layout.projectDirectory.file("LocalPipper.apk").asFile
            if (src.exists()) {
                src.copyTo(dst, overwrite = true)
                println("Copied APK -> ${dst.absolutePath}")
            }
        }
    }
}

