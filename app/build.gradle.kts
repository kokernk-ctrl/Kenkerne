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
        versionCode = 16
        versionName = "0.1.15-fase2"
    }

    // Testes de unidade JVM: métodos do android.jar (ex.: Log.d feito a cada
    // reset de baseline no TouchController) retornam padrão em vez de lançar
    // "not mocked" — sem isso os testes da pinça quebrariam no Log.d.
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Medições em modelos REAIS (auditoria V2): a pasta vem da linha de
        // comando (-Dkenkern.modelos.dir=<pasta>); sem ela o teste é IGNORADO.
        unitTests.all { teste ->
            teste.systemProperty(
                "kenkern.modelos.dir",
                System.getProperty("kenkern.modelos.dir") ?: ""
            )
        }
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
        // BuildConfig.VERSION_NAME como FONTE ÚNICA da versão da engine
        // (ProjectManager.ENGINE_VERSION lê daqui — nunca mais hardcoded).
        buildConfig = true
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
    testImplementation(libs.junit)
    // mockito-core 5.x: mock-maker INLINE por padrão — mocka MotionEvent (classe
    // final do framework) em testes de unidade JVM puros (sem Robolectric).
    implementation(libs.luaj.jse)

    testImplementation(libs.mockito.core)
    // Importador glTF (F2.7): mesmo package org.json do Android, só nos testes JVM.
    testImplementation(libs.org.json)
}
