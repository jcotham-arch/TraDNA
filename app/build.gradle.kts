import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/*
 * Load private local configuration.
 * These values come from the root-level local.properties file.
 */
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")

    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use {
            load(it)
        }
    }
}

val alpacaApiKey =
    localProperties.getProperty(
        "ALPACA_API_KEY",
        ""
    )

val alpacaSecretKey =
    localProperties.getProperty(
        "ALPACA_SECRET_KEY",
        ""
    )

android {
    namespace = "com.tradna.APP"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.tradna.APP"

        minSdk = 26
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        /*
         * Makes the private Alpaca credentials available to the app
         * through BuildConfig.
         */
        buildConfigField(
            "String",
            "ALPACA_API_KEY",
            "\"$alpacaApiKey\""
        )

        buildConfigField(
            "String",
            "ALPACA_SECRET_KEY",
            "\"$alpacaSecretKey\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true

        /*
         * Required because we're adding custom fields
         * to BuildConfig.
         */
        buildConfig = true
    }
}

dependencies {
    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )

    implementation(
        libs.androidx.activity.compose
    )

    implementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    implementation(
        libs.androidx.compose.ui
    )

    implementation(
        libs.androidx.compose.ui.graphics
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview
    )

    implementation(
        libs.androidx.compose.material3
    )

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
}