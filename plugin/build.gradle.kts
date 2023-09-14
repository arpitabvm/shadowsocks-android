plugins {
    id("com.android.library")
    id("com.vanniktech.maven.publish")
    kotlin("android")
    id("kotlin-parcelize")
    id("maven-publish")
}

setupCommon()

android {
    namespace = "com.github.shadowsocks.plugin"
    lint.informational += "GradleDependency"

//    buildTypes {
//        getByName("debug") {
////            isPseudoLocalesEnabled = true
////            isShrinkResources = true
////            isMinifyEnabled = true
//        }
//        getByName("release") {
////            isShrinkResources = true
////            isMinifyEnabled = true
//            proguardFile(getDefaultProguardFile("proguard-android.txt"))
//        }
//    }

}

publishing {
    publications {
        create<MavenPublication>("mavenAar") {
            groupId = "com.example"
            artifactId = "mylibrary"
            version = "1.0.0"
//            from(components.android)
        }
    }

    repositories {
        maven {
            url = uri("$buildDir/releases")
        }
    }
}


dependencies {
    api(kotlin("stdlib-jdk8"))
    api("androidx.core:core-ktx:1.7.0")
    api("androidx.fragment:fragment-ktx:1.5.5")
    api("com.google.android.material:material:1.6.0")
}
