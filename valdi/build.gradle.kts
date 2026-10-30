plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = rootProject.ext["applicationId"].toString() + ".valdi"
    compileSdk = 36

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}
