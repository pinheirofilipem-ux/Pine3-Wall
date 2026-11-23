plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Uma só versão para a integração e para a app: o ficheiro VERSION na raiz do repositório (ex.: 1.2.3).
val appVersion: String = rootProject.file("../VERSION").readText().trim()
val appVersionCode: Int = appVersion.split(".").map { it.toInt() }.let { (ma, mi, pa) -> ma * 10000 + mi * 100 + pa }

// Assinatura de release: keystore própria passada por variáveis de ambiente (secrets do GitHub no CI).
// Sem elas (build local), usa a chave debug da máquina.
val releaseKeystore: String? = System.getenv("PINE3_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "pt.pine3.wall"
    compileSdk = 34

    defaultConfig {
        applicationId = "pt.pine3.wall"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("PINE3_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PINE3_KEY_ALIAS")
                keyPassword = System.getenv("PINE3_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Sem minify/shrink: os ícones MDI são procurados por nome em tempo de execução.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// Sem dependências externas na app: só o SDK Android e a stdlib do Kotlin.
// Os testes (só JVM) usam JUnit e o org.json real (o android.jar dos testes só tem stubs).
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
