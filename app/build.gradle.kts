import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kenkern.engine"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kenkern.engine"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.1.5-editor"
    }

    // Assinatura de release lida de variáveis de ambiente (mesma estratégia usada no CI).
    // Se as variáveis não existirem, o release usa a chave debug para não travar builds locais.
    signingConfigs {
        val ksPath = System.getenv("KEYSTORE_PATH")
        val ksPass = System.getenv("KEYSTORE_PASSWORD")
        val alias = System.getenv("KEY_ALIAS")
        val keyPass = System.getenv("KEY_PASSWORD")
        if (!ksPath.isNullOrBlank() && !ksPass.isNullOrBlank() && !alias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            // R8 ativo: minificação/shrinking de bibliotecas + recursos.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            val releaseSigning = signingConfigs.findByName("release")
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
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
    }
    lint {
        // Lint roda separadamente no CI; evita OOM em máquinas com pouca RAM.
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
}
