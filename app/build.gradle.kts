plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleAndroidLibrariesMapsplatformSecretsGradlePlugin)
    alias(libs.plugins.gradleLombok)
    alias(libs.plugins.chaquopy)
}

secrets {
    // To add your Maps API key to this project:
    // 1. If the secrets.properties file does not exist, create it in the same folder as the local.properties file.
    // 2. Add these lines, where YOUR_API_KEY is your API key:
    //        MAPS_API_KEY=YOUR_API_KEY
    //        AMAP_API_KEY=YOUR_AMAP_API_KEY
    // 
    // How to get Google Maps API Key:
    //    - Visit: https://console.cloud.google.com/google/maps-apis/
    //
    // How to get AMap API Key (高德地图 API Key):
    //    - Visit: https://console.amap.com/dev/key/app
    //    - Guide: https://lbs.amap.com/api/android-sdk/guide/create-project/get-key
    propertiesFileName = "secrets.properties"

    // A properties file containing default secret values. This file can be
    // checked in version control.
    defaultPropertiesFileName = "local.defaults.properties"
}

android {
    namespace = "dev.wander.android.opentagviewer"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.wander.android.opentagviewer"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "release-keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

    androidResources {
        // generateLocaleConfig = true
    }
}

lombok {
    version = libs.versions.lombokVersion.get()
}

chaquopy {
    defaultConfig {
        version = "3.12"
        pip {
            // SEE: https://chaquo.com/chaquopy/doc/current/android.html#android-requirements
            install("FindMy==0.7.6")
            install("NSKeyedUnArchiver==1.5")
        }
    }
    productFlavors {}
    sourceSets {}
}

dependencies {

    implementation(libs.preference)
    implementation(libs.activity)
    implementation(libs.annotation)
    compileOnly(libs.projectlombok)
    implementation(libs.rxjava3)

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycleLivedataKtx)
    implementation(libs.lifecycleViewmodelKtx)
    implementation(libs.navigationFragment)
    implementation(libs.navigationUi)
    implementation(libs.playServicesMaps)
    implementation(libs.androidRoom)
    implementation(libs.androidRoomPaging)
    implementation(libs.fasterxmlJacksonCore)
    implementation(libs.fasterxmlJacksonDatabind)
    implementation(libs.fasterxmlJacksonAnnotations)
    implementation(libs.fasterxmlJacksonYaml)
    implementation(libs.networkntJsonSchemaValidator)
    implementation(libs.datastorePrefs)
    implementation(libs.datastorePrefsRxjava3)
    implementation(libs.googleCronet)
    implementation(libs.retrofit)
    implementation(libs.retrofitJackson)
    implementation(libs.retrofitRxjava3)
    implementation(libs.googleCronetRetrofit)
    implementation(libs.cronetEmbedded)
    implementation(libs.googlePlayLocation)
    implementation(libs.googlePlaces)
    implementation(libs.androidxEmoji)
    implementation(libs.androidxEmojiViews)
    implementation(libs.androidxEmojiViewsHelper)
    implementation(libs.androidxEmojiPicker)

    // 高德地图SDK - Android 3D地图 V9.8.3
    // 参考文档：https://lbs.amap.com/api/android-sdk/gettingstarted
    implementation(libs.amap3dmap)
    implementation(libs.amapLocation)

    testImplementation(libs.junit)
    testImplementation(libs.androidRoomTesting)
    testCompileOnly(libs.projectlombok)

    androidTestImplementation(libs.extJunit)
    androidTestImplementation(libs.espressoCore)

    annotationProcessor(libs.projectlombok)
    annotationProcessor(libs.androidRoomCompiler)

    testAnnotationProcessor(libs.projectlombok)

    coreLibraryDesugaring(libs.desugarJdkLibs)
}
