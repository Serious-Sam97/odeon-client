package dev.odeon.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.odeon.android.ui.AppOdeon

/// A única Activity do app, e a intenção é que continue sendo.
///
/// ## Uma Activity, e não uma por tela
///
/// A web do Odeon tem onze telas com endereço próprio (`/biblioteca`,
/// `/ao-vivo`, `/locadora`). A tradução ingênua disso seriam onze Activities —
/// e seria o desenho errado por um motivo concreto: **estado que atravessa
/// tela**. O player que continua tocando enquanto se procura a próxima coisa, a
/// conexão SSE única de `/api/events` (§62), o Cast que segue ligado ao mudar de
/// tela. Com uma Activity por tela, cada um desses vira um serviço ou um
/// singleton pra sobreviver às trocas.
///
/// Com uma só, eles são estado normal, num escopo que dura o que o app dura.
///
/// ## Por que o nome está em português
///
/// Porque o projeto está. `MainActivity` é convenção de exemplo do Android, não
/// exigência da plataforma — o manifesto aponta pro nome que estiver aqui. Os
/// clientes Kotlin de `clients/` usam nomes em inglês porque são de antes, e não
/// são a régua: a régua é o `web/src/`, onde o código novo (`arrasto.ts`,
/// `hls.ts`) nomeia em português.
class AtividadePrincipal : ComponentActivity() {

    /// `savedInstanceState` em inglês, e é a exceção que confirma a regra.
    ///
    /// O projeto nomeia em português, mas isto é a **sobrescrita** de um método
    /// da plataforma: renomear o parâmetro muda o nome que quem chamar com
    /// argumento nomeado tem que usar, e o compilador avisa em todo build por
    /// causa disso. Um aviso permanente que ninguém vai consertar é um aviso que
    /// se aprende a ignorar — e aí o próximo, o que importa, passa junto.
    ///
    /// Nome de parâmetro de sobrescrita é da plataforma. O resto é nosso.
    override fun onCreate(savedInstanceState: Bundle?) {
        /// Antes do `super`, porque é ele que instala os ouvintes de janela que
        /// o `enableEdgeToEdge` configura. Chamar depois funciona por acidente
        /// em algumas versões e não em outras.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppOdeon()
        }
    }
}
