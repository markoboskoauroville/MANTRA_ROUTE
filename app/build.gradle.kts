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

    /**
     * TEST 4 depends entirely on this.
     *
     * Android refuses an install where the signature differs and reports it as "app not
     * installed", mentioning nothing about signatures. A CI-generated debug key is different
     * every run, so v1 would not have installed over v2 and the failure would have looked
     * like a broken package. The keystore lives in GitHub Actions secrets and is restored to
     * a path passed in by environment; it is never in this repository.
     */
    signingConfigs {
        create("mantra") {
            val path = System.getenv("MANTRA_KEYSTORE")
            if (path != null && file(path).exists()) {
                storeFile = file(path)
                storePassword = System.getenv("MANTRA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MANTRA_KEY_ALIAS")
                keyPassword = System.getenv("MANTRA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Falls back to unsigned when the secret is absent, rather than to a random key
            // that would silently break upgrades.
            if (System.getenv("MANTRA_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("mantra")
            }
        }
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


    testImplementation("junit:junit:4.13.2")
}
