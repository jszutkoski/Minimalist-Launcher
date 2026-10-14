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
        // A fixed key so every CI build can be installed as an update over the
        // previous one. For personal sideloading only: Google Play uses its own
        // upload key, which must be kept out of the repository.
        create("personal") {
            storeFile = file("personal.keystore")
            storePassword = "minimalist"
            keyAlias = "minimalist"
            keyPassword = "minimalist"
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
