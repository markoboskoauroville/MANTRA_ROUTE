plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// modules/versioning.md §3 and §6: one whole number, and the BUILD makes the name.
val mantraVersion = (project.findProperty("mantraVersion") as String).toInt()

android {
    namespace = "com.mantra.route"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mantra.route"
        minSdk = 31
        targetSdk = 35
        versionCode = mantraVersion
        versionName = mantraVersion.toString()
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

// §2: the number at BOTH ends of the filename, derived, never typed.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val impl = output as? com.android.build.api.variant.impl.VariantOutputImpl
            impl?.outputFileName?.set(
                "$mantraVersion-mantra-route-v$mantraVersion-${variant.buildType}.apk"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
}
