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
        versionCode = 34
        versionName = "6.6"
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
            buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${project.findProperty("GOOGLE_MAPS_API_KEY_DEV") ?: ""}\"")
            resValue("string", "google_maps_key", project.findProperty("GOOGLE_MAPS_API_KEY_DEV") as String? ?: "")
        }
        create("prod") {
            dimension = "environment"
            applicationIdSuffix = ".prod"
            versionNameSuffix = "-prod"
            buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${project.findProperty("GOOGLE_MAPS_API_KEY_PROD") ?: ""}\"")
            resValue("string", "google_maps_key", project.findProperty("GOOGLE_MAPS_API_KEY_PROD") as String? ?: "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { 
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-Xjvm-default=all",
            "-Xstring-concat=inline"
        )
    }
    
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs += listOf(
                "-Xjvm-default=all",
                "-Xstring-concat=inline"
            )
        }
        compilerOptions {
            freeCompilerArgs.add("-Xno-param-assertions")
        }
    }
}

dependencies {
    // Firebase BOM (ya lo tenías)
    implementation(platform(libs.firebase.bom))

    // Firebase
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.analytics)

    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-appcheck")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")
    
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Google Play Services
    implementation(libs.play.services.base)
    implementation(libs.play.services.tasks)
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.maps:google-maps-services:2.2.0")
    
    // New Places API (replaces legacy places library)
    implementation("com.google.android.libraries.places:places:3.3.0")

    // AndroidX / Material
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.material)
    implementation(libs.flexbox)
    
    // DataStore for caching metadata
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Markdown rendering for chat
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    // (Tenías core-ktx repetido varias veces; con una alcanza)

    // Imágenes
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:3.1.0")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

apply(plugin = "com.google.gms.google-services")
apply(plugin = "kotlin-parcelize")
