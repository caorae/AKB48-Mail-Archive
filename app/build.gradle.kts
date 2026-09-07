plugins {
    id("com.android.application")
}

android {
    namespace = "com.local.akbmailarchive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.local.akbmailarchive"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.3.22"
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
