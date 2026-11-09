import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/** Legge un file .properties opzionale nella radice del progetto (non versionato). */
fun localProps(name: String): Properties = Properties().apply {
    val f = rootProject.file(name)
    if (f.exists()) f.inputStream().use { load(it) }
}

// Firma di rilascio: keystore.properties (vedi keystore.properties.example). Senza file → build non firmata.
val keystore = localProps("keystore.properties")

// ID AdMob: ads.properties (vedi ads.properties.example). Senza file → ID di TEST ufficiali di Google.
val ads = localProps("ads.properties")
val admobAppId = ads.getProperty("admob.appId", "ca-app-pub-3940256099942544~3347511713")
val admobBannerId = ads.getProperty("admob.bannerId", "ca-app-pub-3940256099942544/9214589741")

android {
    namespace = "io.github.drcrck.openlan"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "io.github.drcrck.openlan"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.2.0"
    }

    signingConfigs {
        if (keystore.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
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
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // Due versioni dallo stesso codice:
    //  - foss: nessuna pubblicità, nessuna libreria Google (adatta a F-Droid e a chi compila da sé)
    //  - play: quella pubblicata su Google Play, con un banner AdMob nella scheda dispositivo
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
        }
        create("play") {
            dimension = "distribution"
            manifestPlaceholders["admobAppId"] = admobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // oui.txt è già testo semplice: non serve comprimerlo nell'APK
        noCompress += "txt"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    // Solo nella versione Play
    "playImplementation"(libs.play.services.ads)
    "playImplementation"(libs.ump)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
