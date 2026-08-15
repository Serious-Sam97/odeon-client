/// A raiz do projeto não aplica plugin nenhum — só declara.
///
/// `apply false` registra a versão do plugin no classpath do build sem ligá-lo
/// aqui: quem liga é o `build.gradle.kts` de cada módulo. Numa raiz com um
/// módulo só isso parecia cerimônia, e o comentário original dizia por que não
/// era: «seria — se não fosse o que acontece quando o segundo módulo chegar: os
/// dois pegam a mesma versão, de um lugar só».
///
/// ## O segundo módulo chegou, e cobrou a promessa na hora — 12/08/2026
///
/// O `:core` nasceu, pediu `com.android.library`, e o build parou antes de
/// compilar uma linha:
///
///     Error resolving plugin [id: 'com.android.library', version: '9.3.1']
///     > The request for this plugin could not be satisfied because the plugin
///       is already on the classpath with an unknown version, so compatibility
///       cannot be checked.
///
/// A mensagem engana quem lê rápido — parece conflito de versão, e não é. O AGP
/// entra no classpath do build **inteiro** pela declaração daqui; um módulo que
/// pede um irmão dele (`library` é irmão de `application`) com versão explícita
/// está pedindo pra resolver de novo algo já resolvido, e o Gradle recusa em vez
/// de adivinhar.
///
/// A correção é esta lista: quem declara a versão é a raiz, uma vez, e os
/// módulos só dizem qual querem. Exatamente o que o comentário de cima prometia.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
