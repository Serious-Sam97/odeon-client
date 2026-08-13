plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/// O módulo que **não desenha**.
///
/// ## Ele nasceu quando houve o segundo alvo, e não antes
///
/// O `settings.gradle.kts` prometia isto por escrito: «Quando houver — um
/// `:tv`, um `:wear` —, o `:core` nasce dessa necessidade e não da previsão
/// dela.» A espec (§4) disse a mesma coisa com outras palavras: «Se um dia
/// entrar TV, o caminho já está claro e não é este app: `:core` compartilhado,
/// mais um módulo `:tv` com UI própria.»
///
/// A TV chegou. Este arquivo é a promessa sendo paga.
///
/// ## A régua do que entra
///
/// **Entra o que não desenha.** Saiu do `:app` sem uma linha de mudança porque
/// a fronteira já existia de fato antes de existir de nome — foi medido antes
/// de mover:
///
/// | | Compose de UI dentro |
/// |---|---|
/// | `dados/` — 12 arquivos, 3.426 linhas | **zero** |
/// | os dez `Modelo*.kt` — 2.374 linhas | **zero** |
///
/// Os `Modelo*.kt` são o achado que faz a TV valer a pena: eles já eram
/// `ViewModel` puro sobre `StateFlow`, sem um `Modifier` sequer. As 914 linhas
/// do `ModeloDoPlayer` — o plano de reprodução, o deslocamento do HLS, as
/// faixas, a marca de progresso — servem a TV **como estão**. Reescrevê-las
/// seria criar a segunda contabilidade do §43 de propósito.
///
/// ## E a régua do que fica
///
/// **Fica no `:app` o que é do celular.** `midia/` inteiro ficou lá: o
/// `ServicoDeMidia` depende do `OdeonApp`, o `ServicoDeDownload` depende de um
/// `R.string` do `:app`, e a TV não baixa filme — ela está sempre na rede que
/// serve o filme. A TV escreve o serviço dela, curto, no `:tv`.
///
/// `Insignia.kt` também ficou: ela desenha. A conta que ela faz por baixo — a
/// cor derivada do nome — é `corOklch`, e essa veio.
///
/// ## O `namespace` não é o pacote, e a diferença importa aqui
///
/// `dev.odeon.nucleo` é o `namespace` — ele decide só onde nascem o `R` e o
/// `BuildConfig` **deste módulo**, e precisa ser diferente do `:app` senão os
/// dois `R` colidem. Os pacotes Kotlin lá dentro continuam
/// `dev.odeon.android.dados` e `dev.odeon.android.ui.*`, intactos.
///
/// É de propósito: com o pacote preservado, a extração não mexeu em **nenhum**
/// `import` do `:app`. Um `git mv` e uma linha de dependência. Renomear pacote
/// junto teria misturado "mover 5.800 linhas" com "reescrever 300 imports" no
/// mesmo commit — e quando algo quebrasse, não daria pra saber qual das duas
/// coisas quebrou.
android {
    namespace = "dev.odeon.nucleo"

    /// As duas linhas, pelo mesmo motivo do `:app`: a plataforma alvo é a
    /// **37.1**, e `compileSdk` sozinho só diz a maior. Ver o comentário longo
    /// em `gradle/libs.versions.toml`.
    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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

/// ## Quase tudo aqui é `api`, e num módulo de biblioteca isso é decisão
///
/// `implementation` esconde a dependência de quem consome o módulo;
/// `api` a repassa. A régua é o que aparece em **assinatura pública**, e neste
/// módulo aparece muito:
///
/// - `RepositorioOdeon.clienteHttp()` devolve um `OkHttpClient` — e é ele que o
///   `:tv` entrega ao Coil e ao Media3, que é o ponto inteiro de haver **um**
///   cliente (§43)
/// - todo `Modelo*.kt` é um `ViewModel`
/// - os modelos são `@Serializable`
/// - `corDeHex` devolve um `Color` do Compose
///
/// Escondê-las obrigaria o `:tv` a redeclarar cada uma — e redeclarar é onde
/// nasce a segunda versão.
dependencies {
    api(libs.androidx.core.ktx)

    /// Os `Modelo*.kt` estendem `ViewModel` e usam `viewModelScope`.
    api(libs.androidx.lifecycle.viewmodel.compose)

    /// Onde o `Cofre` guarda o endereço do servidor e os dois tokens.
    api(libs.androidx.datastore.preferences)

    /// A rede. O `okhttp` explícito pelo mesmo motivo do `:app`: este módulo
    /// **usa a instância direto** e a devolve pra quem chamar.
    api(libs.okhttp)
    api(libs.retrofit)
    implementation(libs.retrofit.serialization)
    api(libs.kotlinx.serialization.json)

    /// O `Baixados` (`dados/Downloads.kt`) segura o `DownloadManager` e o
    /// `SimpleCache` do Media3, e o `ModeloDoPlayer` lê `PlaybackException`.
    ///
    /// O `:tv` não baixa nada — mas herda o cache mesmo assim, e de graça: é
    /// ele que faz o filme continuar de onde parou sem repuxar do servidor.
    api(libs.media3.exoplayer)
    api(libs.media3.datasource.okhttp)
    api(libs.media3.exoplayer.hls)

    /// Só pelo `Color` do `ui/Cor.kt`.
    ///
    /// ⚠️ É `ui-graphics`, e **não** `ui` nem `material3`. A diferença é a régua
    /// deste módulo em forma de dependência: `Color` é um valor, um `inline
    /// class` sobre um `ULong`; `Modifier` e `Text` são desenho. Deixar o
    /// `material3` entrar aqui seria abrir a porta pra alguém pôr uma tela
    /// dentro do `:core` sem perceber que atravessou a fronteira.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.graphics)

    /// Pelo `Tipo` e pela `Serifada` do `ui/Tema.kt`, que desceram em
    /// 12/08/2026 — a T0 do `docs/REDESENHO-TV.md`, §3.3.
    ///
    /// ⚠️ É `ui-text`, e a escolha do artefato é o argumento inteiro. Ele traz
    /// `androidx.compose.ui.text.*` — `TextStyle`, `FontFamily`, `Font` — e
    /// **nada** de `Modifier`, layout ou composable. Ou seja, a mesma linha que
    /// o `ui-graphics` acima defende: um `TextStyle` é um valor, do mesmo jeito
    /// que um `Color`; quem desenha é o `Text`, e o `Text` não está aqui.
    ///
    /// A alternativa era o `:tv` declarar a própria serifada — e aí a mesma
    /// fonte, embutida uma vez, seria configurada duas. É literalmente o
    /// argumento que trouxe a paleta pra cá: o `:tv` copiando o dourado.
    ///
    /// O `.ttf` já morava neste módulo antes disto (`src/main/res/font`), e por
    /// isso a `Serifada` desce **sem** mudar de `R`: ela já lia
    /// `dev.odeon.nucleo.R`.
    api(libs.androidx.compose.ui.text)

    testImplementation(libs.junit)
}
