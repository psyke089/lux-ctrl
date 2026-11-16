plugins { id("com.android.application") }

val appVersionName = "0.3.0"

// Signing values come from ~/.gradle/gradle.properties (luxctrl.*) or the CI environment.
// The dedicated release key lives at ~/.android/keys/lux-ctrl/lux-ctrl-release.jks and
// replaced the old public debug identity on 2026-11-16.
fun signingValue(property: String, env: String): String? =
    providers.gradleProperty(property).orNull ?: System.getenv(env)

val releaseStoreFile = signingValue("luxctrl.storeFile", "LUXCTRL_STORE_FILE")

android {
    namespace = "app.luxctrl"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "app.luxctrl"
        minSdk = 24
        targetSdk = 37
        versionCode = 6
        versionName = appVersionName
        testInstrumentationRunner = "app.luxctrl.BridgeSmokeInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("luxctrl.storePassword", "LUXCTRL_STORE_PASSWORD")
                keyAlias = signingValue("luxctrl.keyAlias", "LUXCTRL_KEY_ALIAS") ?: "luxctrl"
                keyPassword = signingValue("luxctrl.keyPassword", "LUXCTRL_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        // Optimized release; falls back to debug signing when no key is configured so a
        // contributor checkout still produces an installable APK.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig =
                signingConfigs.getByName(if (releaseStoreFile != null) "release" else "debug")
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output -> output.outputFileName.set("lux-ctrl-$appVersionName.apk") }
    }
}

dependencies { testImplementation("junit:junit:4.13.2") }
