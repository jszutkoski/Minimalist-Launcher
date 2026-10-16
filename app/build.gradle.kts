import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jszutkoski.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jszutkoski.launcher"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // A fixed key so every CI build installs as an update over the previous one.
        // The release key lives only in the repository's Actions secrets, which CI decodes
        // to a file; it is never in the code. Until those secrets exist, builds fall back to
        // the old key below, which is public and so lets anyone sign an "update": it goes
        // as soon as the secret one is in use.
        val releaseKey = System.getenv("MINIMALIST_KEYSTORE_FILE")
        create("personal") {
            if (releaseKey != null) {
                storeFile = file(releaseKey)
                storePassword = System.getenv("MINIMALIST_KEYSTORE_PASSWORD")
                keyAlias = "minimalist"
                keyPassword = System.getenv("MINIMALIST_KEYSTORE_PASSWORD")
            } else {
                storeFile = file("personal.keystore")
                storePassword = "minimalist"
                keyAlias = "minimalist"
                keyPassword = "minimalist"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("personal")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
