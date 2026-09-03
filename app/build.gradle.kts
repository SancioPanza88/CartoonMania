plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cartoonmania.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cartoonmania.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 9
        versionName = "1.7"
    }

    signingConfigs {
        create("stable") {
            storeType = "PKCS12"
            storeFile = rootProject.file("android-signing/cartoonmania.keystore")
            storePassword = "cm2026stable"
            keyAlias = "cartoonmania"
            keyPassword = "cm2026stable"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
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
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
}
