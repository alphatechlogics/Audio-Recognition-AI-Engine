plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.voicerecognitionaiengine"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.voicerecognitionaiengine"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}
dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation ("org.tensorflow:tensorflow-lite:2.12.0")
    implementation ("org.tensorflow:tensorflow-lite-select-tf-ops:2.12.0")
}
