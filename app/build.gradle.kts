plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.manager"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    // Add packaging options for OR-Tools native libraries and resolve conflicts
    packaging {
        resources {
            pickFirsts.add("META-INF/AL2.0")
            pickFirsts.add("META-INF/LGPL2.1")
            pickFirsts.add("META-INF/INDEX.LIST")
            excludes.add("META-INF/DEPENDENCIES")
            excludes += listOf(
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt"
            )
            
            // Pick first for duplicate resource files
            pickFirsts += listOf(
                "lib/x86_64/libjniortools.so",
                "lib/x86/libjniortools.so",
                "lib/armeabi-v7a/libjniortools.so",
                "lib/arm64-v8a/libjniortools.so",
                "time.aut",
                "long.aut",
                "org/chocosolver/memory/trailing/trail/IStored_E_Trail.template",
                "org/chocosolver/memory/trailing/trail/chunck/Chuncked_E_Trail.template",
                "double.aut",
                "float.aut",
                "integer.aut",
                "name.aut"
            )
        }
    }
}

dependencies {
    // Include all JAR files in the libs directory
    implementation(fileTree(mapOf(
        "dir" to "libs", 
        "include" to listOf("*.jar")
    )))
    
    // Regular dependencies
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.3")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation(libs.glide)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)
    implementation(libs.play.services.maps)
    
    // Add Trove library for collections (needed by Choco Solver)
    implementation("net.sf.trove4j:trove4j:3.0.3") 
    
    // Choco Solver - Using local JAR file from libs directory
    // implementation("org.choco-solver:choco-solver:4.10.7") {
    //     exclude(group = "org.scala-lang", module = "scala-library")
    // }
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}