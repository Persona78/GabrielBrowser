import java.util.Properties

// AGP 9: o suporte a Kotlin vem integrado (sem o plugin org.jetbrains.kotlin.android).
plugins {
    id("com.android.application")
}

val geckoVersion: String = providers.gradleProperty("geckoviewVersion").get()
require(geckoVersion != "REPLACE_ME") {
    "Define a versão do GeckoView em gradle.properties (ou corre ./scripts/update-geckoview.sh --apply)"
}

// Assinatura de release: cria keystore.properties (ver keystore.properties.example).
// Sem esse ficheiro, o release é assinado com a chave de debug (serve para testar).
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}

android {
    namespace = "pt.gabriel.browser"
    // O GeckoView 159+ (e o androidx.core 1.19) exigem compilar contra a API 37.
    // Isto não muda o comportamento da app (targetSdk) nem os aparelhos suportados (minSdk).
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "pt.gabriel.browser"
        minSdk = 29
        targetSdk = 35
        versionCode = 2026111372
        versionName = "1.0-gv160"

        // Só arm64 (quase todos os telemóveis recentes): reduz muito o tamanho do APK.
        // Se o teu aparelho for de 32 bits, acrescenta "armeabi-v7a".
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // jvmTarget do Kotlin segue automaticamente o targetCompatibility acima.
}

// O GeckoView puxa um kotlin-stdlib mais recente (2.4.x) do que o compilador do AGP 9.1 (2.2.x) consegue ler.
// Fixamos a stdlib na versão do compilador (é retrocompatível).
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
            useVersion("2.2.0")
        }
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:$geckoVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
}
