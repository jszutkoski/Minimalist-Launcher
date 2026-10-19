import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** The last version released; builds in between are named after it. */
val BASE_VERSION = "1.2"

android {
    namespace = "com.jszutkoski.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jszutkoski.launcher"
        minSdk = 30
        targetSdk = 36
        // Each CI run numbers its build, so a newer APK always installs over an older one; a
        // local build is 1. App info shows the name: a release's own version ("1.3"), or for
        // every other build the version it grows from and its run ("1.2 (152)").
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = run ?: 1
        versionName = System.getenv("MINIMALIST_VERSION")?.removePrefix("v")?.takeIf { it.isNotEmpty() }
            ?: if (run != null) "$BASE_VERSION ($run)" else BASE_VERSION
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

    testOptions {
        // Plain JVM tests: the Android classes they touch only need to exist.
        unitTests.isReturnDefaultValues = true
        // Screen tests (Robolectric) run the real Android views, with the app's own texts.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "2g" }
    }

    lint {
        abortOnError = true
        // Warnings are reported in the build log; only errors fail it.
        warningsAsErrors = false
        textReport = true
        // Every view is built in code; none is ever inflated from a layout file.
        disable += "ViewConstructor"
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

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
