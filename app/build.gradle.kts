import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val testSecrets = Properties().apply {
    val secretsFile = rootProject.file("test-secrets.properties")
    if (secretsFile.isFile) secretsFile.inputStream().use(::load)
}
val playBuild = providers.gradleProperty("playBuild").orNull?.toBoolean() == true ||
    providers.gradleProperty("playFeasibility").orNull?.toBoolean() == true
val privacyPolicyUrl = providers.gradleProperty("privacyPolicyUrl").orNull
    ?: "https://github.com/pocketforge/pocketforge/blob/main/PRIVACY.md"
val runtimeReleaseBaseUrl = providers.gradleProperty("runtimeReleaseBaseUrl").orNull
    ?: "https://github.com/techjarves/Mobile-Harness/releases/download/runtime-2026.09.4"
val appUpdateManifestUrl =
    providers.gradleProperty("appUpdateManifestUrl").orNull
        ?: "https://github.com/pocketforge/pocketforge/releases/latest/download/pocketforge-update.json"

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.pocketforge.mobile"
    compileSdk = 36
    ndkVersion = providers.gradleProperty("mhNdkVersion").orNull ?: "26.1.10909125"

    val debugKeystore = file("${rootDir}/debug.keystore")
    val debugKeystoreBase64 = file("${rootDir}/debug.keystore.base64")
    if (!debugKeystore.exists() && debugKeystoreBase64.exists()) {
        try {
            val bytes = Base64.getDecoder().decode(debugKeystoreBase64.readText().trim())
            debugKeystore.writeBytes(bytes)
        } catch (_: Exception) {}
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = debugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("releaseConfig") {
            val rawStoreFilePath = System.getenv("MH_UPLOAD_STORE_FILE")
                ?: System.getenv("SIGNING_KEYSTORE_FILE")
                ?: System.getenv("KEYSTORE_FILE")
                ?: providers.gradleProperty("signingStoreFile").orNull
            val storeFilePath = rawStoreFilePath?.takeIf { it.isNotBlank() }

            if (storeFilePath != null && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("MH_UPLOAD_STORE_PASSWORD")
                    ?: System.getenv("SIGNING_STORE_PASSWORD")
                    ?: providers.gradleProperty("signingStorePassword").orNull ?: ""
                keyAlias = System.getenv("MH_UPLOAD_KEY_ALIAS")
                    ?: System.getenv("SIGNING_KEY_ALIAS")
                    ?: providers.gradleProperty("signingKeyAlias").orNull ?: ""
                keyPassword = System.getenv("MH_UPLOAD_KEY_PASSWORD")
                    ?: System.getenv("SIGNING_KEY_PASSWORD")
                    ?: providers.gradleProperty("signingKeyPassword").orNull ?: ""
            } else if (file("${rootDir}/release.keystore").exists()) {
                storeFile = file("${rootDir}/release.keystore")
                storePassword = System.getenv("MH_UPLOAD_STORE_PASSWORD") ?: "pocketforge"
                keyAlias = System.getenv("MH_UPLOAD_KEY_ALIAS") ?: "release"
                keyPassword = System.getenv("MH_UPLOAD_KEY_PASSWORD") ?: "pocketforge"
            } else if (debugKeystore.exists()) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    defaultConfig {
        applicationId = "com.pocketforge.mobile"
        minSdk = 28
        targetSdk = if (playBuild) 36 else 28
        versionCode = 6
        versionName = "1.1.1"
        providers.gradleProperty("appVersionCode").orNull?.toIntOrNull()?.let { versionCode = it }
        providers.gradleProperty("appVersionName").orNull?.let { versionName = it }

        ndk.abiFilters += "arm64-v8a"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("boolean", "IS_PLAY_BUILD", playBuild.toString())
        buildConfigField("String", "PRIVACY_POLICY_URL", buildConfigString(privacyPolicyUrl))
        buildConfigField("boolean", "OFFLINE_RUNTIME_BUNDLES", "false")
        buildConfigField("String", "RUNTIME_RELEASE_BASE_URL", buildConfigString(runtimeReleaseBaseUrl))
        buildConfigField("String", "APP_UPDATE_MANIFEST_URL", buildConfigString(appUpdateManifestUrl))
        buildConfigField("String", "APP_VARIANT", "\"online\"")

        buildConfigField(
            "String",
            "TEST_OPENROUTER_API_KEY",
            "\"\"",
        )
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
            buildConfigField(
                "String",
                "TEST_OPENROUTER_API_KEY",
                buildConfigString(testSecrets.getProperty("openrouter.apiKey", "")),
            )
        }
        release {
            signingConfig = signingConfigs.getByName("releaseConfig")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
    buildFeatures {
        compose = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    packaging.jniLibs.useLegacyPackaging = true
    androidResources.noCompress += "zst"
}

tasks.register("playReadinessCheck") {
    group = "verification"
    description = "Checks configuration required before uploading a PocketForge Play bundle."
    doLast {
        check(playBuild) { "Run with -PplayBuild=true." }
        check(privacyPolicyUrl.startsWith("https://")) {
            "privacyPolicyUrl must be a public HTTPS URL."
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("com.github.luben:zstd-jni:1.5.6-9@aar")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250107")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
