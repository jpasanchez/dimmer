plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.dimmer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "dev.dimmer"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies { }