plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    namespace = "com.github.barteksc.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.github.barteksc.sample"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":android-pdf-viewer"))
    implementation("com.android.support:appcompat-v7:28.0.0")
    kapt("org.androidannotations:androidannotations:4.8.0")
    kapt("org.androidannotations:androidannotations-api:4.8.0")
}
