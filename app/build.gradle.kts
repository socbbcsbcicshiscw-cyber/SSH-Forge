plugins {
    id("com.android.application")
}

android {
    namespace = "com.sshforge.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.sshforge.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }
}

dependencies {
    implementation("com.github.mwiede:jsch:2.28.8")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.apache.sshd:sshd-core:2.20.0")
}
