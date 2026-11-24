plugins { id("com.android.application") }

android {
    namespace = "app.gogamid.dmcoupons"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.gogamid.dmcoupons"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/helper.jks")
            storePassword = "dmcoupons"
            keyAlias = "helper"
            keyPassword = "dmcoupons"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
