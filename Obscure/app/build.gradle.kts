import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Zip

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

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

val releaseNativeDebugSymbolsZip by tasks.registering(Zip::class) {
    group = "build"
    description = "Packages release native libraries into a Play Console upload zip."

    val extractedNativeSymbolTablesDir =
        layout.buildDirectory.dir("intermediates/native_symbol_tables/release/extractReleaseNativeSymbolTables/out")
    val mergedNativeLibsDir =
        layout.buildDirectory.dir("intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib")

    val zipSourceDir = providers.provider {
        val extractedDir = extractedNativeSymbolTablesDir.get().asFile
        if (extractedDir.exists() && extractedDir.walkTopDown().any { it.isFile }) {
            extractedDir
        } else {
            mergedNativeLibsDir.get().asFile
        }
    }

    dependsOn("mergeReleaseNativeLibs", "extractReleaseNativeSymbolTables")
    archiveFileName.set("native-debug-symbols.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/native-debug-symbols/release"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    includeEmptyDirs = false
    from(zipSourceDir)

    doFirst {
        val sourceDir = zipSourceDir.get()
        if (!sourceDir.exists() || sourceDir.walkTopDown().none { it.isFile }) {
            throw GradleException("No release native libraries were found to package into native-debug-symbols.zip.")
        }
    }
}

tasks.matching { it.name in setOf("assembleRelease", "bundleRelease") }.configureEach {
    dependsOn(releaseNativeDebugSymbolsZip)
}

android {
    namespace = "roy.ij.obscure"
    compileSdk = 36

    defaultConfig {
        applicationId = "roy.ij.obscure"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            ndk {
                debugSymbolLevel = "symbol_table"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")

            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://obscure-load-balancer.ij-roy.workers.dev/\""
            )
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://obscure-load-balancer.ij-roy.workers.dev/api/\""
            )
            buildConfigField(
                "String",
                "SOCKET_BASE_URL",
                "\"https://obscure-load-balancer.ij-roy.workers.dev\""
            )
        }

        debug {
            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://obscure-load-balancer.ij-roy.workers.dev/\""
            )
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://obscure-load-balancer.ij-roy.workers.dev/api/\""
            )
            buildConfigField(
                "String",
                "SOCKET_BASE_URL",
                "\"https://obscure-load-balancer.ij-roy.workers.dev\""
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
