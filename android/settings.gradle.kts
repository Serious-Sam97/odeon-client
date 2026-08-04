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

// Um módulo só, e é de propósito.
//
// A espec (§4, "Sem TV") já escreveu o que aconteceria se dividíssemos cedo:
// «A separação da R51 mostrou o custo de dividir cedo demais; dividir **quando
// houver alvo** é outra coisa.» Não há segundo alvo hoje. Quando houver — um
// `:tv`, um `:wear` —, o `:core` nasce dessa necessidade e não da previsão dela.
include(":app")
