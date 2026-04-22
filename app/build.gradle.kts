plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.sdk_qa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.sdk_qa"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Orchestrator: wipe app state (SharedPrefs, singletons) entre cada test.
        // Crítico para SDK testing — el MediaPlayer/ExoPlayer usa estado global.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    testOptions {
        // Cada test corre en su propio proceso — aislamiento real entre tests.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
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

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)

    // Core library desugaring — requerido por el SDK y IMA con minSdk < 26
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Media3 UI (PlayerView en layouts XML)
    implementation("androidx.media3:media3-ui:1.5.0")

    // Mediastream SDK
    implementation("io.github.mediastream:mediastreamplatformsdkandroid:10.0.4-alpha03")

    // --- Test dependencies ---
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("io.mockk:mockk-android:1.13.10")
    androidTestImplementation("com.google.truth:truth:1.4.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // LeakCanary instrumentation — falla el test si el SDK no libera recursos.
    // DetectLeaksAfterTestSuccess corre automáticamente después de cada test exitoso.
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    androidTestImplementation("com.squareup.leakcanary:leakcanary-android-instrumentation:2.14")

    // MockWebServer — inyecta respuestas HTTP (500, timeout, 404) en tests.
    // El SDK usa OkHttp internamente: MockWebServer intercepta esas llamadas.
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")

    // Orchestrator runner — requerido junto con la config testOptions arriba.
    androidTestUtil("androidx.test:orchestrator:1.6.1")
}
