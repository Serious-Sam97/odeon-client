/// A raiz do projeto não aplica plugin nenhum — só declara.
///
/// `apply false` registra a versão do plugin no classpath do build sem ligá-lo
/// aqui: quem liga é o `app/build.gradle.kts`. Numa raiz com um módulo só isso
/// parece cerimônia, e seria — se não fosse o que acontece quando o segundo
/// módulo chegar: os dois pegam a mesma versão, de um lugar só.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
