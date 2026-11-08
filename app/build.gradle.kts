plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pythdom.callme"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pythdom.callme"
        minSdk = 34
        targetSdk = 35
        versionCode = (System.getenv("CALLME_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("CALLME_VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        // Release signing comes from environment variables (GitHub secrets in CI).
        // Without them the release APK falls back to the debug key so it stays installable.
        val storeFilePath = System.getenv("CALLME_KEYSTORE_FILE")
        if (storeFilePath != null && file(storeFilePath).exists()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("CALLME_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CALLME_KEY_ALIAS")
                keyPassword = System.getenv("CALLME_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
