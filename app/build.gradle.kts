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
        // A fixed key so every CI build installs as an update over the previous one. It lives
        // only in the repository's Actions secrets, which CI decodes to a temporary file; it is
        // never in the code. Without it (a local build, a pull request from a fork) a release
        // build comes out unsigned, and CI refuses to publish one.
        val releaseKey = System.getenv("MINIMALIST_KEYSTORE_FILE")
        if (releaseKey != null) {
            create("release") {
                storeFile = file(releaseKey)
                storePassword = System.getenv("MINIMALIST_KEYSTORE_PASSWORD")
                keyAlias = "minimalist"
                keyPassword = System.getenv("MINIMALIST_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Android's own debug key, and a separate app: it never installs over the real one.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
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
