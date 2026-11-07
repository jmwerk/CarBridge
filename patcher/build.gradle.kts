plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.carbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.carbridge"
        minSdk = 35
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Release key lives outside the repo; set CARBRIDGE_* in ~/.gradle/gradle.properties
    val releaseStore = providers.gradleProperty("CARBRIDGE_STORE_FILE").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("CARBRIDGE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("CARBRIDGE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("CARBRIDGE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        // Installs next to the release build, which is signed with a different key
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the release key (e.g. a fresh clone), fall back to debug signing for local tests
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation("io.github.reandroid:ARSCLib:1.4.0")
    implementation("com.android.tools.build:apksig:9.5.1")

    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.11.0")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    // Optional real-APK fixture: ./gradlew :patcher:testDebugUnitTest -PpatchFixture=/path/app.apk
    project.findProperty("patchFixture")?.let { systemProperty("patchFixture", it) }
    // Mirror a phone's heap cap to catch out-of-memory patches: -PpatchHeap=256m
    project.findProperty("patchHeap")?.let { maxHeapSize = it.toString() }
    systemProperty("patchOut", layout.buildDirectory.file("patch-test/out.apk").get().asFile.path)
}
