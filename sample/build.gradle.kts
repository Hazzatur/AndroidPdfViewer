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
        versionCode = 4
        versionName = "3.0.1"
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
    implementation(project(":android-pdf-viewer"))
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
}
