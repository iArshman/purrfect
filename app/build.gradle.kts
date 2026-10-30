import java.io.File

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
    id("com.google.devtools.ksp") version "2.2.20-2.0.3"
}

android {
    namespace = rootProject.ext["applicationId"].toString()
    compileSdk = 36

    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = rootProject.ext["applicationId"].toString()
        versionCode = rootProject.ext["appVersionCode"].toString().toInt()
        versionName = rootProject.ext["appVersionName"].toString()
        minSdk = 28
        targetSdk = 36
        multiDexEnabled = true
        buildConfigField("String", "EXPECTED_CERT_SHA256", "\"\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles += file("proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugConfig")
            proguardFiles += file("proguard-rules.pro")
        }
    }

    packaging {
        jniLibs {
            excludes += "**/*_neon.so"
        }
        resources {
            excludes += "DebugProbesKt.bin"
            excludes += "okhttp3/internal/publicsuffix/**"
            excludes += "META-INF/*.version"
            excludes += "META-INF/services/**"
            excludes += "META-INF/*.kotlin_builtins"
            excludes += "META-INF/*.kotlin_module"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core"))
    compileOnly(files("../core/libs/LSPosed-api-1.0-SNAPSHOT.jar"))
    implementation(project(":common"))
    implementation(project(":native"))
    implementation(libs.androidx.documentfile)
    implementation("androidx.browser:browser:1.8.0")
    implementation(libs.gson)
    implementation(libs.smart.exception.java)
    implementation(libs.osmdroid.android)
    implementation(libs.rhino)
    implementation(libs.androidx.activity.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bcprov.jdk18on)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.ripple)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.colorpicker.compose)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation(libs.androidx.work.runtime.ktx)

    // Patching pipeline dependencies
    implementation(files("libs/apkzlib.jar"))
    implementation(files("libs/ManifestEditor-1.0.2.jar"))
    implementation(libs.apksig)
    implementation(libs.dexlib2)
    implementation(libs.dexkit)
    implementation(libs.jsoup)
    implementation("com.google.auto.value:auto-value-annotations:1.10.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.json:json:20240303")
    implementation(libs.fetch) {
        exclude(group = "androidx.room", module = "room-runtime")
    }
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Compose Foundation & Layout
    implementation(libs.foundation)
    implementation(libs.ui)
    implementation(libs.foundation.layout)

    // Animated navigation + transitions (Accompanist)
    implementation(libs.accompanist.navigation.animation)
}
