package dev.odeon.android

import android.content.Intent
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

        abaPedida.value = intent?.getStringExtra("aba")

        setContent {
            AppOdeon(abaPedida = abaPedida)
        }
    }

    /// A aba que o atalho pediu, como estado observável.
    ///
    /// ## Por que não basta ler o `intent` no `onCreate`
    ///
    /// Porque com o app **já aberto** o toque no atalho não passa pelo
    /// `onCreate`: a Activity é reusada e o sistema entrega o novo `Intent`
    /// aqui embaixo. A primeira versão lia só no `onCreate`, e o efeito era
    /// silencioso e chato de perceber — o atalho funcionava perfeitamente na
    /// primeira vez do dia e não fazia nada nas outras.
    ///
    /// `MutableState` e não `StateFlow` porque quem lê é composição, e um
    /// `State` é o que o Compose observa sem ponte nenhuma. Quem consome zera o
    /// valor depois de usar — senão a mesma aba seria pedida de novo a cada
    /// recomposição, e o app trancaria naquela aba.
    private val abaPedida = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        /// `setIntent` pra o `intent` da Activity passar a ser este. Sem isso,
        /// qualquer código que releia `intent` depois veria o da abertura.
        setIntent(intent)
        abaPedida.value = intent.getStringExtra("aba")
    }
}
