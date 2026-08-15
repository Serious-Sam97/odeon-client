/// O app Android do Odeon — um projeto Gradle próprio.
///
/// ## Por que ele não entra no `clients/`
///
/// O `clients/` é um projeto Kotlin Multiplatform: `shared` em `commonMain`,
/// `composeApp` pra celular e iOS, `tv` pra Android TV. A espec (§1) decidiu que
/// este app é **nativo, do zero, só Android** — e um app nativo não importa
/// `commonMain` sem arrastar o KMP de volta junto.
///
/// Dois projetos Gradle no mesmo repositório é o desenho certo pra isso: cada um
/// tem o seu catálogo de versões e o seu wrapper, e mexer num não recompila o
/// outro. O KMP fica parado onde está, e a espec não propõe apagá-lo.
///
/// ## E por que ele fica dentro do `odeon-client`, e não em repositório próprio
///
/// Porque `web/src/api.ts` já é uma cópia à mão do contrato da API, o `shared`
/// do KMP é a segunda, e este app é a terceira. Num repositório só, um `grep`
/// alcança as três — separados, a terceira cópia envelhece sozinha.
rootProject.name = "odeon-android"

pluginManagement {
    repositories {
        // O filtro por grupo não é enfeite: sem ele o Gradle pergunta ao
        // `google()` por todo plugin do mundo antes de ir ao Maven Central, e
        // cada pergunta é uma ida à rede.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// Eram um módulo só, e o comentário aqui dizia por quê:
//
//     «A espec (§4, "Sem TV") já escreveu o que aconteceria se dividíssemos
//     cedo: "A separação da R51 mostrou o custo de dividir cedo demais; dividir
//     **quando houver alvo** é outra coisa." Não há segundo alvo hoje. Quando
//     houver — um `:tv`, um `:wear` —, o `:core` nasce dessa necessidade e não
//     da previsão dela.»
//
// **O segundo alvo chegou**: uma TCL com Google TV. E a divisão saiu exatamente
// como estava previsto, porque esperar até doer é o que a deixa barata — a
// fronteira já existia no código antes de existir no Gradle. Medido antes de
// mover: `dados/` e os dez `Modelo*.kt` tinham **zero** imports de Compose de
// UI. Foi `git mv` e uma linha de dependência; nenhum `import` do `:app` mudou.
//
//   :core → tudo que não desenha. `dados/` e os `Modelo*.kt`, compartilhados
//   :app  → o celular e o tablet: toque, PiP, download, Cast, haptics
//   :tv   → a sala: D-pad, foco explícito, dez pés, e a home do Google TV
//
// O `:wear` continua não existindo, e continua pelo mesmo motivo de sempre.
//
// ## E o quarto, `:cenario` — 12/08/2026
//
// Ele nasceu pela **segunda** aplicação da mesma régua, e a T0 do
// `docs/REDESENHO-TV.md` é o alvo que a justificou: o `:tv` precisa da caixa 3D,
// da película, da cortina e do facho, e os quatro moravam no `:app`.
//
// A divisão anterior separou por **quem desenha**: `:core` não desenha, `:app` e
// `:tv` desenham. Esta separa por **pra quem se desenha** — e o achado é que há
// coisa que desenha e não é de nenhum dos dois aparelhos. Uma caixa de VHS em
// três quartos é a mesma caixa a trinta centímetros e a três metros; o que muda
// é de que tamanho e como se aponta pra ela.
//
//   :cenario → o que desenha e não é de nenhum aparelho: a caixa 3D, a
//              película, o facho, a cortina, a projeção, as tintas da capa
//
// ⚠️ **Ela saiu mais cara que a primeira, e o número está medido.** A do `:core`
// foi um `git mv` e uma linha, porque `dados/` e os `Modelo*.kt` tinham zero
// imports de Compose de UI. Aqui foram **25 chamadas** de `material3` pra
// desacoplar e **um arquivo partido** — o `Facho.kt`, que era a luz e a barra de
// baixo do celular no mesmo arquivo. Continua barata; não é a mesma conta.
include(":core")
include(":cenario")
include(":app")
include(":tv")
