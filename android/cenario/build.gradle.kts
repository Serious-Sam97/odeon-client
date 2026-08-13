plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

/// A cenografia: **o que desenha e não é de nenhum aparelho**.
///
/// ## Por que ele existe, e por que não deu pra pôr no `:core`
///
/// O `:core` tem uma régua de uma linha — «tudo que não desenha» — e ela é o que
/// impede alguém de escorregar uma tela pra dentro dele. A caixa 3D desenha. A
/// película desenha. Pôr as duas lá apagaria a única frase que aquele módulo
/// tem, e um módulo sem régua vira a pasta `utils` do projeto em seis meses.
///
/// Mas elas também não são do celular. O `docs/REDESENHO-TV.md` mediu isso antes
/// de mover (§3): das **3.716** linhas das oito peças, **1.794** não tocavam em
/// `androidx.compose.material3` de jeito nenhum, e o resto tocava em 25 lugares
/// — quase todos `Text`.
///
/// ```
/// :core     → o que não desenha: dados, modelos, os Modelo*.kt, a paleta
/// :cenario  → o que desenha e não é de nenhum aparelho  ⬅️ este
/// :app      → o celular: toque, PiP, download, Cast, haptics
/// :tv       → a sala: D-pad, foco, dez pés, a home do Google TV
/// ```
///
/// ## ⚠️ A régua deste módulo, e ela é a razão de ele funcionar
///
/// **Só `foundation` + `ui` + `animation`. Nenhum `material3`, de nenhum dos
/// dois sabores.**
///
/// Isso não é purismo. O `tv/build.gradle.kts` explica a briga que a espec §4
/// previu: `androidx.tv.material3` e `androidx.compose.material3` exportam
/// `Card`, `Button`, `Text`, `Surface` e `MaterialTheme` com **o mesmo nome**.
/// Um módulo compartilhado que dependesse de um dos dois só compilaria de um
/// lado; um que dependesse dos dois seria o pesadelo que a separação evitou.
///
/// Dependendo de **nenhum**, ele compila nos dois — e a briga continua não
/// acontecendo.
///
/// Na prática a régua se paga com `BasicText` no lugar de `Text`, que é o
/// componente de texto do `foundation` e é exatamente o que o `Text` do Material
/// chama por baixo depois de resolver cor e estilo do tema. Aqui não há tema
/// pra resolver, e é por isso que a troca não muda um pixel: estas peças já
/// escreviam o `TextStyle` inteiro à mão, porque são **objetos impressos** — o
/// corpo 7,5sp da advertência da contracapa é o corpo da letra de bula de uma
/// capa de VHS, não um papel da escala tipográfica de ninguém.
///
/// ## O que **não** entra aqui
///
/// Nada que dependa de um aparelho. Sem `WindowInsets` de celular, sem foco de
/// D-pad, sem háptico, sem PiP. Uma peça que precise saber onde está deixou de
/// ser cenário e virou tela — e telas moram nos dois módulos de cima.
///
/// A `BarraDoFacho` é o exemplo que quase escapou: ela é a barra de baixo do
/// celular, com o inset do gesto na conta. A **luz** dentro dela atravessa; a
/// barra não. Ver `Facho.kt` no `:app` e `Luz.kt` aqui.
android {
    /// `dev.odeon.cenario`, do mesmo jeito e pelo mesmo motivo que o `:core` é
    /// `dev.odeon.nucleo`: o `namespace` decide só onde nascem o `R` e o
    /// `BuildConfig` deste módulo, e dois módulos com o mesmo `namespace`
    /// colidem.
    ///
    /// Os pacotes Kotlin lá dentro continuam `dev.odeon.android.ui.*`, intactos
    /// — que é o que fez a extração não mexer em **nenhum** `import` do `:app`,
    /// exatamente como aconteceu quando o `:core` nasceu.
    namespace = "dev.odeon.cenario"

    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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

/// `api` pelo mesmo motivo do `:core`: o que aparece em assinatura pública tem
/// que atravessar. E aqui aparece quase tudo — toda peça é um `@Composable` que
/// recebe `Modifier`, e várias recebem ou devolvem `Color`, `TextStyle` e
/// `Painter`.
dependencies {
    /// A paleta, a `Serifada`, os modelos que as peças desenham — `Fita`,
    /// `CaixaExposta`, `ObraDetalhada`, `FolhaDeSprites` — e os `corDeHex`,
    /// `duracaoCompacta` e `tamanhoCompacto`.
    api(project(":core"))

    api(platform(libs.androidx.compose.bom))

    /// ⚠️ Estas três linhas **são** a régua deste módulo, escrita em Gradle.
    ///
    /// `ui` traz `Modifier`, `DrawScope` e o `graphicsLayer` — sem ele não há
    /// caixa em três quartos. `foundation` traz `Canvas`, `BasicText`,
    /// `Image` e os gestos. `animation-core` traz `Animatable` e `keyframes`,
    /// que é onde moram os dez quadros do arco.
    ///
    /// Não há uma quarta linha, e é de propósito. O dia em que alguém precisar
    /// de um `Card` aqui, a peça que ele está escrevendo é uma tela.
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.animation.core)

    /// As peças desenham arte vinda do servidor: o pôster na capa da caixa, a
    /// folha de sprites na película, o still na contracapa. É o **mesmo** Coil
    /// dos dois apps, e por isso o mesmo cache e o mesmo OkHttp.
    api(libs.coil.compose)

    /// ⚠️ **O Palette entra aqui, e ele é a única dependência deste módulo que
    /// custa peso ao `:tv`.**
    ///
    /// É a `TintasDaCapa` que o pede: as duas cores da lombada saem da capa por
    /// `Palette` — vibrante pra tinta, escura pro casco. Sem ele a caixa da
    /// estante volta a ser cinza de interface, que foi exatamente a queixa «o 3D
    /// de ambos tá absurdamente feio».
    ///
    /// O README dizia que o APK do `:tv` era 18 MB contra 23 do celular, e que
    /// «a diferença é o que a sala não carrega: Glance, Cast, **Palette** e o
    /// Media3 de download». Uma dessas quatro deixa de ser verdade nesta leva —
    /// e é uma troca boa: a `palette-ktx` inteira tem ~60 KB, e o que ela compra
    /// é a lombada colorida, que numa TV é o objeto que mais se vê.
    ///
    /// Ela cabe na régua deste módulo por pouco e por um bom motivo: `Palette`
    /// **não desenha** — ela lê um `Bitmap` e devolve `Int`. É insumo de cor,
    /// não componente. O que desenha com o resultado é a `CaixaEm3D`.
    api(libs.androidx.palette)

    testImplementation(libs.junit)
}
