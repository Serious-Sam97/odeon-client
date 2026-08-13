plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/// O Odeon na sala.
///
/// ## Por que ele é um módulo, e não uma pasta do `:app`
///
/// A espec (§4) recusou servir celular e TV no mesmo módulo, e escreveu o
/// motivo antes de haver TV pra provar: «o Compose de TV é outro conjunto de
/// artefatos (`androidx.tv:tv-foundation`, `androidx.tv:tv-material`), com
/// componentes de foco próprios, e misturá-lo com Material3 de celular no mesmo
/// módulo é briga constante. E toda tela nova sairia duas vezes.»
///
/// Continua verdade, e agora dá pra dizer qual é a briga: `androidx.tv.material3`
/// e `androidx.compose.material3` têm `Card`, `Button`, `Text`, `Surface` e
/// `MaterialTheme` com **o mesmo nome**. No mesmo módulo, cada arquivo vira uma
/// escolha de import onde errar compila — e o que sai é um botão de celular no
/// meio de uma tela de TV, sem foco, invisível pro D-pad.
///
/// Separados, não há escolha a fazer: aqui só existe o de TV.
///
/// ## O que ele reaproveita, e o que ele reescreve
///
/// | | |
/// |---|---|
/// | reaproveita | o `:core` inteiro — rede, modelos, e os dez `Modelo*.kt` |
/// | reescreve | **toda** a camada que desenha |
///
/// Não é desperdício: 10-foot UI é outro paradigma, não celular esticado. O que
/// não muda entre uma sala e um ônibus é o que o servidor respondeu — e isso é
/// exatamente o `:core`.
android {
    /// `dev.odeon.android.tv`, e não `dev.odeon.tv`.
    ///
    /// O `dev.odeon.tv` **já existe**: é o app de TV do `clients/`, o do Kotlin
    /// Multiplatform, parado desde a importação inicial. Reusar o id faria os
    /// dois brigarem pela mesma instalação no aparelho — e num aparelho onde se
    /// está justamente comparando o novo com o velho, isso é o pior momento
    /// possível pra um deles sumir.
    namespace = "dev.odeon.android.tv"

    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "dev.odeon.android.tv"

        /// O mesmo 26 do celular, e aqui ele sobra.
        ///
        /// No `:app` o número foi escolhido pelo Picture-in-Picture (§4). Aqui
        /// não há PiP — mas também não há motivo pra subir: a Google TV mais
        /// velha que ainda recebe atualização está muito acima disso, e baixar a
        /// régua não custa nada quando nada abaixo dela é usado.
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            /// Pelo mesmo motivo do `:app`: a versão de trabalho tem que
            /// conviver com uma instalada de verdade. Numa TV isso vale ainda
            /// mais — reinstalar lá é `adb connect` pela rede, não um cabo.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            /// Desligado, e pelo mesmo motivo escrito no `:app`: o R8 sem regras
            /// quebra desserialização por reflexão em tempo de execução, que é
            /// o que este app mais faz. Ele entra com as regras escritas junto e
            /// um APK conferido no aparelho.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    /// A razão de o `:core` existir. Ver `core/build.gradle.kts`.
    implementation(project(":core"))

    /// A razão de o `:cenario` existir — e é literalmente este módulo.
    ///
    /// A tabela acima diz que o `:tv` «reescreve **toda** a camada que
    /// desenha». Isso continua verdade pra **telas**, e deixou de ser verdade
    /// pros **objetos**: a caixa de VHS em três quartos, a película com as cenas
    /// de verdade, a cortina vermelha e a lâmpada de arco são os mesmos objetos
    /// nos dois aparelhos.
    ///
    /// O que a sala faz de diferente com eles é apontar por D-pad em vez de
    /// dedo, e mostrá-los do tamanho de dez pés. Nada disso está dentro das
    /// peças — a `CaixaEm3D` já recebia a `pose` de fora, «quando alguém quer
    /// controlá-la», e esse alguém agora existe.
    implementation(project(":cenario"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)

    /// ⚠️ O Material3 **da TV**, e ele é o único aqui.
    ///
    /// Não há `androidx.compose.material3` neste módulo de propósito: os dois
    /// exportam `Card`, `Button`, `Surface` e `MaterialTheme` com o mesmo nome,
    /// e ter os dois no classpath transforma cada import numa chance de pôr um
    /// componente sem foco numa tela que só se navega por foco.
    ///
    /// O `foundation` comum entra normal — `LazyRow`, `Modifier`, `Canvas` não
    /// têm versão de TV, e a de TV foi descontinuada justamente por isso.
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.compose.foundation)

    /// A home do Google TV. Ver `home/` e o comentário no catálogo.
    implementation(libs.androidx.tvprovider)

    /// Os pôsteres, com **o mesmo OkHttp** do `:core` — ver `OdeonTv`.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    /// O player. `media3-ui` traz o `PlayerView`, que é a superfície de vídeo —
    /// disso o Compose não tem equivalente, nem no celular nem aqui.
    implementation(libs.media3.exoplayer)
    /// ⚠️ **O HLS é o que faz os canais de fora existirem.**
    ///
    /// Sem esta linha o `:tv` sabe abrir arquivo e não sabe abrir playlist, e
    /// `sintonizar` devolve exatamente uma playlist (`playlist_url`). Metade da
    /// sintonia ficava decorativa por falta de uma dependência — não de código.
    ///
    /// O `:app` já a tinha; o `:tv` nasceu sem, e ninguém notou porque os canais
    /// do Odeon tocam pelo caminho direto, com arquivo.
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
