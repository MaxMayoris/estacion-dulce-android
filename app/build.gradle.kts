plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.estaciondulce.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.estaciondulce.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 6
        versionName = "3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { viewBinding = true }

    signingConfigs {
        create("release") {
            storeFile = file("F:\\Programacion\\Proyectos\\estacion-dulce-app-android\\my-release-key.jks")
            storePassword = System.getenv("ESTACION_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ESTACION_KEY_ALIAS")
            keyPassword = System.getenv("ESTACION_KEY_PASSWORD")
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        create("prod") {
            dimension = "environment"
            applicationIdSuffix = ".prod"
            versionNameSuffix = "-prod"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}

dependencies {
    // Firebase BOM (ya lo tenías)
    implementation(platform(libs.firebase.bom))

    // Firebase
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.analytics)

    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-appcheck")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")

    // Google Play Services
    implementation(libs.play.services.base)
    implementation(libs.play.services.tasks)

    // AndroidX / Material
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.material)
    implementation(libs.flexbox)
    // (Tenías core-ktx repetido varias veces; con una alcanza)

    // Imágenes
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

apply(plugin = "com.google.gms.google-services")
apply(plugin = "kotlin-parcelize")
