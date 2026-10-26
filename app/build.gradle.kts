plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.fitlog.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.fitlog.app"
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = providers.gradleProperty("fitlog.testRunner")
            .orElse("com.fitlog.app.WorkoutSaveValidation").get()
        versionCode = 98
        versionName = "beta1.1.1"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs.getByName("debug") {
        val localKey = rootProject.file(".local/debug.keystore")
        if (localKey.exists()) storeFile = localKey
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.coil.compose)
    // Already present at runtime through Coil; expose its EXIF reader to the photo editor.
    implementation(libs.androidx.exif)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.ui.tooling)
}







