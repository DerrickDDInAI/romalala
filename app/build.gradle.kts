plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.romalala"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.romalala"
        minSdk = 29 // android.icu.text.Transliterator
        targetSdk = 35
        versionCode = 11
        versionName = "0.6.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging {
        // Both Kuromoji jars include these files. Preserve both sets of notices.
        resources.merges += setOf("META-INF/CONTRIBUTORS.md", "META-INF/LICENSE.md")
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.code.gson:gson:2.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
