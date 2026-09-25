plugins { id("com.android.application") }

val appVersionName = "0.3.0"

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
    buildTypes {
        // Optimized release, signed with the debug key so it installs without a keystore setup.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
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
