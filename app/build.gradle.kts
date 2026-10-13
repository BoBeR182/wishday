import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.wishday"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "net.stuckless.wishday"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing for GitHub APKs. The key lives outside the repo; without it, release
    // builds are unsigned (F-Droid builds and signs apps itself).
    val signingFile = file(
        providers.gradleProperty("wishday.signing").orNull
            ?: "${System.getProperty("user.home")}/.android-keys/wishday-release.properties",
    )
    val releaseSigning = if (signingFile.exists()) {
        val props = Properties().apply { signingFile.inputStream().use(::load) }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        release {
            signingConfig = releaseSigning
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    dependenciesInfo {
        // An encrypted blob only Google Play can read; F-Droid's scanner rejects it.
        includeInApk = false
        includeInBundle = false
    }
    lint {
        // Known findings are recorded here so CI fails only on new ones.
        baseline = file("lint-baseline.xml")
    }
    androidResources {
        // Lists the app's languages (English, Spanish) for Android's per-app language setting.
        generateLocaleConfig = true
        // Only ship the languages the app itself is translated into.
        localeFilters += listOf("en", "es")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
ksp {
    // Exported schemas are committed so future migrations can be tested.
    arg("room.schemaLocation", "$projectDir/schemas")
}
