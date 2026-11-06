plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun signingValue(property: String, env: String): String? =
    (project.findProperty(property) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val airconsStoreFile = signingValue("airconsStoreFile", "AIRCONS_STORE_FILE")
val airconsStorePassword = signingValue("airconsStorePassword", "AIRCONS_STORE_PASSWORD")
val airconsKeyAlias = signingValue("airconsKeyAlias", "AIRCONS_KEY_ALIAS")
val airconsKeyPassword = signingValue("airconsKeyPassword", "AIRCONS_KEY_PASSWORD")
val stableSigning = airconsStoreFile != null && airconsStorePassword != null &&
    airconsKeyAlias != null && airconsKeyPassword != null

android {
    namespace = "app.aircons"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.aircons"
        minSdk = 29
        targetSdk = 35
        versionCode = (project.findProperty("airconsVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("airconsVersionName") as String?) ?: "1.0.0"
        val buildEpoch = (project.findProperty("airconsBuildEpoch") as String?) ?: "0"
        buildConfigField("long", "BUILD_EPOCH", buildEpoch)
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (stableSigning) {
            create("aircons") {
                storeFile = file(airconsStoreFile!!)
                storePassword = airconsStorePassword
                keyAlias = airconsKeyAlias
                keyPassword = airconsKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
            if (stableSigning) signingConfig = signingConfigs.getByName("aircons")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (stableSigning) signingConfig = signingConfigs.getByName("aircons")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.mozilla:rhino:1.7.15")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
