import org.gradle.api.JavaVersion

plugins {
    id("com.android.library")
}

repositories {
    google()
    maven {
        // All of React Native (JS, Obj-C sources, Android binaries) is installed from npm
        url = uri("$rootDir/../node_modules/react-native/android")
    }
    jcenter()
}

android {
    compileSdkVersion = rootProject.findProperty("compileSdkVersion")?.toString()?.toInt() ?: 28

    defaultConfig {
        minSdkVersion(rootProject.findProperty("minSdkVersion")?.toString()?.toInt() ?: 16)
        targetSdkVersion(rootProject.findProperty("targetSdkVersion")?.toString()?.toInt() ?: 28)
        versionCode = 1
        versionName = "1.0.0"
    }

    lintOptions {
        isAbortOnError = false
    }
}

dependencies {
    val reactNativeVersion = rootProject.findProperty("reactNativeVersion")?.toString() ?: "+"
    compileOnly("com.facebook.react:react-native:$reactNativeVersion")
}

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8
