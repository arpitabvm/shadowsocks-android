import com.nishtahir.Versions.android

plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-parcelize")
}

//setupApp()

android {
    namespace = "com.github.shadowsocks"
    buildToolsVersion("33.0.1")
    compileSdkVersion(33)
    defaultConfig {
        minSdk = 23
        targetSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val javaVersion = JavaVersion.VERSION_11
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    defaultConfig.applicationId = "com.github.shadowsocks"

    signingConfigs {
        create("release") {

            storeFile = File("upload12.jks")
            storePassword = "upload12"
            keyPassword = "upload12"
            keyAlias = "upload12"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
//            isPseudoLocalesEnabled = true
//            isShrinkResources = true
//            isMinifyEnabled = true
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
//            isShrinkResources = true
//            isMinifyEnabled = true
            proguardFile(getDefaultProguardFile("proguard-android.txt"))
        }
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }

}

dependencies {
    val cameraxVersion = "1.2.1"

    implementation("androidx.browser:browser:1.5.0-rc01")
//    implementation("androidx.camera:camera-camera2:$cameraxVersion")
//    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
//    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
//    implementation("com.takisoft.preferencex:preferencex-simplemenu:1.1.0")
//    implementation("com.twofortyfouram:android-plugin-api-for-locale:1.0.4")
    implementation("me.zhanghai.android.fastscroll:library:1.2.0")

    dependencies.add("implementation", project(":core"))

    implementation("androidx.multidex:multidex:2.0.1")
}



