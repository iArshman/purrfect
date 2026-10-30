plugins {
    alias(libs.plugins.androidLibrary)
}

val nativeBuildHash = rootProject.ext.get("buildHash").toString()

android {
    namespace = rootProject.ext["applicationId"].toString() + ".nativelib"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "NATIVE_NAME", "\"$nativeBuildHash\"")
        buildConfigField("String", "MODULE_PACKAGE_NAME", "\"${rootProject.ext["applicationId"]}\"")
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("com.google.code.gson:gson:2.10.1")
}
