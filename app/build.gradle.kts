import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystorePropertiesFile.isFile &&
    keystoreProperties.getProperty("storeFile")?.let { rootProject.file(it).isFile } == true

// OAuth client IDs are injected at build time. They are public identifiers,
// not secrets, and can be overridden with -Pmtz.githubClientId=... or env vars.
val githubClientId: String =
    (project.findProperty("mtz.githubClientId") as String?)
        ?: System.getenv("MTZ_GITHUB_CLIENT_ID")
        ?: ""
val googleClientId: String =
    (project.findProperty("mtz.googleClientId") as String?)
        ?: System.getenv("MTZ_GOOGLE_CLIENT_ID")
        ?: ""

android {
    namespace = "at.libmatti.mtztool"
    compileSdk = 36

    defaultConfig {
        applicationId = "at.libmatti.mtztool"
        minSdk = 28
        targetSdk = 36
        versionCode = 20100
        versionName = "2.1.0"

        buildConfigField("String", "GITHUB_REPO", "\"mattiasploesch-cpu/mtztool\"")
        buildConfigField("String", "DEFAULT_UPDATE_CHANNEL", "\"stable\"")
        buildConfigField("String", "SOURCE_URL", "\"https://github.com/mattiasploesch-cpu/mtztool\"")
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
        // Only the dedicated Play Store build type flips this to true.
        buildConfigField("boolean", "PLAY_BUILD", "false")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        // Play Store only. It is a separate build type, so "assembleDebug"
        // and regular release builds never build the Play variant. Optimised
        // for the smallest possible upload: R8 full mode, resource shrinking,
        // stripped native symbols and per-device AAB splits.
        create("playstore") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            isDebuggable = false
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "PLAY_BUILD", "true")
            ndk {
                debugSymbolLevel = "NONE"
            }
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // No ABI APK splits: they are incompatible with Android App Bundles and
    // made `bundlePlaystore` fail. The Play bundle performs its own ABI,
    // language and density splitting; the GitHub release ships one universal
    // APK.


    bundle {
        language {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Release builds skip lintVital so offline builds and CI bundles do not
    // depend on downloading the lint tool. Lint runs in its own CI job.
    lint {
        checkReleaseBuilds = false
        abortOnError = true
    }

    androidResources {
        noCompress += "mtz"
        generateLocaleConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            // Store the .so files uncompressed in the APK and let the bundle
            // split them by ABI; no legacy packaging, no duplicate symbols.
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    debugImplementation(libs.androidx.ui.tooling)

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // APK signing for generated icon packs
    implementation(libs.apksig)
}
