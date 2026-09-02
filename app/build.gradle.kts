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
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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