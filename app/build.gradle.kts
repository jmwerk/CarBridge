plugins {
    id("com.android.application")
}

android {
    namespace = "org.aaspike"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.aaspike"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-spike"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
