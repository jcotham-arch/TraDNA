import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
         * Declare the fields for every build variant, but never place
         * workstation credentials in the default/release configuration.
         */
        buildConfigField(
            "String",
            "ALPACA_API_KEY",
            "\"\""
        )

        buildConfigField(
            "String",
            "ALPACA_SECRET_KEY",
            "\"\""
        )
    }

    buildTypes {
        debug {
            /*
             * Temporary local-development access only. Debug APKs must
             * never be distributed as production builds because Android
             * BuildConfig values can be extracted from an APK.
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

ksp {
    arg(
        "room.schemaLocation",
        "$projectDir/schemas"
    )
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

    implementation(
        libs.androidx.room.runtime
    )

    implementation(
        libs.androidx.room.ktx
    )

    ksp(
        libs.androidx.room.compiler
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
        libs.androidx.room.testing
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
