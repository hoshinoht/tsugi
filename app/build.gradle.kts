import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Looks up a secret from, in order: an environment variable, `.env` at the repo root, then
 * `local.properties`. Files are read through `providers` so the configuration cache notices edits.
 */
fun secret(name: String): String {
    fun fileProps(path: String) = Properties().apply {
        providers.fileContents(rootProject.layout.projectDirectory.file(path)).asText.orNull
            ?.let { load(it.reader()) }
    }
    return providers.environmentVariable(name).orNull
        ?: fileProps(".env").getProperty(name)?.trim()?.removeSurrounding("\"")
        ?: fileProps("local.properties").getProperty(name)
        ?: ""
}

android {
    namespace = "dev.cantabile.tsugi"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "dev.cantabile.tsugi"
        minSdk = 31
        targetSdk = 37
        versionCode = 4
        versionName = "0.4.0"
        buildConfigField("String", "LTA_ACCOUNT_KEY", "\"${secret("LTA_ACCOUNT_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal sideloaded app: sign release with the debug key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    // Glance pulls in WorkManager 2.7.1 (Room 2.2.5), which crashes at startup on Android 17.
    implementation(libs.work.runtime)
    implementation(libs.reorderable)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
