plugins { id("com.android.application") }

android {
    namespace = "app.luxctrl"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "app.luxctrl"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "app.luxctrl.BridgeSmokeInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies { testImplementation("junit:junit:4.13.2") }
