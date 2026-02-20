plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)

    id("com.google.dagger.hilt.android") version "2.51"
    kotlin("kapt") // ONLY for Hilt
}

android {
    namespace = "roy.ij.obscure"
    compileSdk = 36

    defaultConfig {
        applicationId = "roy.ij.obscure"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://baatcheet-backend-jrndg.ondigitalocean.app/\""
            )
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://baatcheet-backend-jrndg.ondigitalocean.app/api/\""
            )
            buildConfigField(
                "String",
                "SOCKET_BASE_URL",
                "\"https://baatcheet-backend-jrndg.ondigitalocean.app\""
            )
        }

        debug {
            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://ij.dophera.xyz/\""
            )
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://ij.dophera.xyz/api/\""
            )
            buildConfigField(
                "String",
                "SOCKET_BASE_URL",
                "\"https://ij.dophera.xyz\""
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.common.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.lifecycle.process)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.lottie.compose)

    // Import the Firebase BoM
    implementation(platform(libs.firebase.bom))
    implementation(libs.google.firebase.messaging.ktx)

    val roomVersion = "2.6.1"

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation(libs.android.database.sqlcipher)

    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-compiler:2.51")

    implementation("io.socket:socket.io-client:2.1.0") {
        exclude(group = "org.json", module = "json")
    }

    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("com.google.zxing:core:3.5.3")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    implementation("androidx.biometric:biometric:1.1.0")
}