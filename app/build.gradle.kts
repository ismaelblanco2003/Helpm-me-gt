import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

val mapTilerKey = localProperties
    .getProperty("MAPTILER_API_KEY", "")
    .trim()

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.example.helpme"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.helpme"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "2.2"

        buildConfigField(
            "String",
            "MAPTILER_API_KEY",
            "\"${mapTilerKey}\""
        )
    }

    buildFeatures {
        buildConfig = true
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
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))

    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")

    implementation("org.osmdroid:osmdroid-android:6.1.20")
}