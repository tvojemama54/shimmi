plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cz.example.shimmiyolobot"
    compileSdk = 35
    defaultConfig {
        applicationId = "cz.example.shimmiyolobot"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-test"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
