plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.revosleap.text"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.revosleap.text"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
    }
    val ciStore = System.getenv("ANDROID_KEYSTORE_PATH")
    val ciPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val ciAlias = System.getenv("ANDROID_KEY_ALIAS")
    signingConfigs {
        if (!ciStore.isNullOrBlank() && !ciPassword.isNullOrBlank() && !ciAlias.isNullOrBlank()) {
            create("ci") {
                storeFile = file(ciStore)
                storePassword = ciPassword
                keyAlias = ciAlias
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: ciPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("ci") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
}
