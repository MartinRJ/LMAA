import java.util.Properties

plugins {
    id("com.android.application")
    id("androidx.room")
    id("com.chaquo.python")
    id("com.google.devtools.ksp")
    id("com.google.protobuf")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningFile = rootProject.file("signing.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.isFile) {
        releaseSigningFile.inputStream().use(::load)
    }
}
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val releaseSigningConfigured = releaseSigningKeys.all { key ->
    !releaseSigningProperties.getProperty(key).isNullOrBlank()
}

android {
    namespace = "de.lmaa.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.lmaa.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        create("instrumented") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".testbed"
            versionNameSuffix = "-testbed"
            matchingFallbacks += listOf("debug")
        }
        getByName("release") {
            signingConfig = if (releaseSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }
    testBuildType = providers.gradleProperty("lmaa.testBuildType")
        .orElse("instrumented")
        .get()

    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Prüft die lokale, nicht versionierte Release-Signing-Konfiguration."
    doLast {
        check(releaseSigningConfigured) {
            "android/signing.properties fehlt oder ist unvollständig; Vorlage: signing.properties.example"
        }
        val configuredStore = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
        check(configuredStore.isFile) {
            "Konfigurierter Release-Keystore existiert nicht: $configuredStore"
        }
    }
}

tasks.configureEach {
    if (name in setOf("packageRelease", "assembleRelease", "bundleRelease")) {
        dependsOn(verifyReleaseSigning)
    }
}

chaquopy {
    defaultConfig {
        version = "3.10"
        pip {
            install("youtube-transcript-api==1.2.4")
            install("requests==2.34.2")
            install("defusedxml==0.7.1")
            install("urllib3==2.7.0")
            install("certifi==2026.7.22")
            install("charset-normalizer==3.5.1")
            install("idna==3.19")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.32.1"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore:1.2.1")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("com.google.crypto.tink:tink-android:1.23.0")
    implementation("com.google.protobuf:protobuf-javalite:4.32.1")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.3.0")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
}
