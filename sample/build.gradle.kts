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
        versionName = "3.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":android-pdf-viewer"))
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.pdfium.android)
}
