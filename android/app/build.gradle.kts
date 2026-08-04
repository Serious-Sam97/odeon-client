plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    /// `dev.odeon.android`, seguindo o `dev.odeon.shared` e o `dev.odeon.tv` que
    /// já existem em `clients/`. Era um dos pontos em aberto do §7 da espec, e
    /// foi fechado assim.
    namespace = "dev.odeon.android"

    /// A plataforma alvo é a **37.1**, e ela precisa das duas linhas.
    ///
    /// O Android passou a ter versões menores de plataforma — 36.1, 37.0, 37.1 —
    /// e `compileSdk` sozinho só diz a maior. Sem o `compileSdkMinor`, isto aqui
    /// compilaria contra a 37.0, que não é a que está instalada.
    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()

    /// Pinado. Sem esta linha o AGP escolhe sozinho, e a escolha dele muda com a
    /// versão dele — que é como dois computadores compilam o mesmo commit com
    /// ferramentas diferentes.
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "dev.odeon.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            /// O sufixo é o que deixa a versão de trabalho conviver com uma
            /// instalada de verdade no mesmo aparelho. Sem ele, instalar o
            /// debug **desinstala** a outra — e num projeto onde a conferência é
            /// feita no celular do dono, isso é apagar o app que ele usa.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            /// O id do app, como **recurso** — pros atalhos da R9.
            ///
            /// O `res/xml/atalhos.xml` precisa apontar o `targetPackage` da
            /// intenção, e recurso não enxerga `${'$'}{applicationId}`: os
            /// espaços reservados do Gradle valem no manifesto, não em `res/`.
            ///
            /// Com um valor fixo, o atalho do debug apontaria pro pacote de
            /// produção — e como os dois **convivem** no mesmo aparelho (é o
            /// motivo do sufixo logo acima), segurar o ícone do debug abriria o
            /// app de verdade. Silenciosamente.
            resValue("string", "id_do_app", "dev.odeon.android.debug")
        }
        release {
            /// O par do `id_do_app` do debug — ver o comentário lá em cima.
            resValue("string", "id_do_app", "dev.odeon.android")

            /// Desligado, e é decisão consciente pra fase 0.
            ///
            /// O R8 sem regras é o caminho mais curto pra um APK que compila,
            /// instala e quebra em tempo de execução — e quebra justamente no
            /// que o Odeon mais usa: desserialização por reflexão. Ele entra
            /// quando houver modelo de dados pra proteger, com as regras
            /// escritas junto e um APK conferido no aparelho.
            ///
            /// Ligar agora seria prometer que o release funciona sem ter rodado
            /// um.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true

        /// ⚠️ **No AGP 9 o `resValue` não funciona sem esta linha**, e o erro é
        /// de configuração e não de compilação:
        ///
        ///     Build Type debug contains custom resource values,
        ///     but the feature is disabled.
        ///
        /// É a mesma família das outras surpresas do AGP 9 anotadas no
        /// `libs.versions.toml`: coisas que vinham ligadas de fábrica passaram a
        /// ser declaradas. Um exemplo de internet de 2024 não traz esta linha.
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    /// No AGP 9 o bloco `kotlinOptions` **não existe mais** — ele foi removido
    /// em favor deste `compilerOptions`, do próprio plugin de Kotlin. É a
    /// diferença mais visível entre este arquivo e o `clients/tv/build.gradle.kts`,
    /// que está em AGP 8.7 e ainda usa a forma velha.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    /// Onde ficam o endereço do servidor e os dois tokens. Ver `dados/Cofre.kt`
    /// pro que isso protege e o que não protege.
    implementation(libs.androidx.datastore.preferences)

    /// A rede. O `okhttp` está explícito mesmo vindo pelo Retrofit: este projeto
    /// **usa a instância direto** — o Coil pega a mesma, e o Media3 vai pegar na
    /// fase 2. Depender dele por tabela seria depender de um detalhe do Retrofit.
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    /// O player, ligado na fase 2.
    ///
    /// ⚠️ A UI se escreve contra a interface `Player`, **nunca** contra
    /// `ExoPlayer`. O comentário longo está em `gradle/libs.versions.toml`, ao
    /// lado das versões, e o resumo é: `media3-cast` entrega um `CastPlayer` que
    /// implementa a mesma interface, então a fase 4 é trocar a instância.
    ///
    /// O `datasource-okhttp` é o que amarra o player **na mesma instância de
    /// OkHttp** do Retrofit e do Coil. Sem ele o player abre pool próprio, e o
    /// token de mídia passa a ter duas contabilidades — que é onde o §43 morde.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    /// A sessão de mídia: controles do sistema, tela de bloqueio, botão do fone.
    /// Ela é o que faz a tela segurar um `MediaController` em vez de um player —
    /// e `MediaController` também é um `Player`, então a decisão de escrever
    /// contra a interface passou no primeiro teste de verdade dela.
    implementation(libs.media3.session)

    /// O Cast — fase 4.
    ///
    /// `media3-cast` traz o `CastPlayer`, que implementa a **mesma** interface
    /// `Player` que o resto da tela já usa. É a aposta que a fase 2 fez sendo
    /// cobrada: mandar pra TV virou trocar a instância.
    ///
    /// O `play-services-cast-framework` não vem junto e é obrigatório — o
    /// `CastPlayer` recebe um `CastContext`, e ele nasce aqui.
    implementation(libs.media3.cast)
    implementation(libs.play.services.cast.framework)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.exoplayer.hls)

    /// Só no debug. Ele imprime corpo de requisição no logcat, e corpo de
    /// requisição deste app inclui **a senha** do login e o token de sessão.
    debugImplementation(libs.okhttp.logging)

    /// O BOM vai de `platform(...)`: ele não traz código, só decide a versão de
    /// todo artefato de Compose abaixo. Por isso as linhas seguintes não têm
    /// versão nenhuma — e não ter é o certo.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.ui.tooling.preview)

    /// `debugImplementation` e não `implementation`: as ferramentas de inspeção
    /// do Compose são grandes e não têm o que fazer num APK publicado.
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
