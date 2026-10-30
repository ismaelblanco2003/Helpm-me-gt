plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Se aplica solo cuando agregues el archivo oficial de tu proyecto Firebase.
// Sin él, la APK sigue compilando como demostración local sin administrador.
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
}
