plugins {
    id("com.android.application")
}

android {
    namespace = "com.azudaisuki.akbmailarchive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.azudaisuki.akbmailarchive"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.3.22"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
