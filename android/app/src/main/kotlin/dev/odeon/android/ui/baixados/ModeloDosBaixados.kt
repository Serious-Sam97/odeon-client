package dev.odeon.android.ui.baixados

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dev.odeon.android.dados.Baixado
import dev.odeon.android.dados.Baixados
import dev.odeon.android.dados.Cofre
import dev.odeon.android.dados.RelogioQueNaoVolta
import dev.odeon.android.ui.prazoDoEmprestimo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/// `@UnstableApi` porque ela **carrega** `Baixado`, que lê as constantes de
/// estado do Media3. O opt-in acompanha o tipo, não a intenção.
@UnstableApi
data class EstadoDosBaixados(val itens: List<Baixado> = emptyList()) {
    /// ## Quanto o app está ocupando no aparelho
    ///
    /// É a pergunta que faz alguém abrir esta tela. Ela existia no `Baixado`
    /// desde sempre — `bytes` — e nenhuma linha a somava: a tela dizia o estado
    /// de cada arquivo e nunca o peso do conjunto, que é justamente o que se
    /// quer saber antes de decidir o que apagar.
    ///
    /// ⚠️ **Soma o que já desceu, e não o que vai ocupar.** O `bytes` do Media3 é
    /// o baixado até agora; um filme a 64% conta 64%. É o número certo pra
    /// «quanto o app está usando **hoje**», e o errado pra «quanto vai usar» — e
    /// a primeira é a pergunta que a tela responde. Por isso o cartão de quem
    /// está baixando escreve `1,2 de 1,9 GB`: lá o denominador cabe.
    val bytesNoAparelho: Long get() = itens.sumOf { it.bytes }
}

/// Os downloads, e o relógio que decide quais ainda valem.
@UnstableApi
class ModeloDosBaixados(
    private val baixados: Baixados,
    private val cofre: Cofre,
    /// Só pra montar a URL da arte — nada nesta tela chama rota.
    private val odeon: dev.odeon.android.dados.RepositorioOdeon,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDosBaixados())
    val estado: StateFlow<EstadoDosBaixados> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            /// Relê enquanto a tela está aberta.
            ///
            /// O `DownloadManager` tem ouvinte, mas ele fala de **um** download
            /// por vez e não do conjunto. Um segundo de intervalo desenha a fila
            /// inteira andando sem o app ficar reconstruindo lista a cada bloco
            /// de bytes que desce.
            while (true) {
                _estado.value = EstadoDosBaixados(baixados.lista())
                delay(1_000)
            }
        }
    }

    /// A fita venceu?
    ///
    /// A hora que vale é a do `RelogioQueNaoVolta` — a do aparelho, ou o maior
    /// instante já visto, o que for maior. Atrasar o relógio não ressuscita
    /// nada.
    fun venceu(item: Baixado): Boolean = RelogioQueNaoVolta.venceu(
        venceEm = prazoDe(item),
        doAparelho = System.currentTimeMillis(),
        maiorJaVisto = cofre.maiorInstanteVisto,
    )

    /// O prazo que vale pra este download.
    ///
    /// ⚠️ **Só o que veio da locadora expira.** É a decisão tomada depois do
    /// §71: a biblioteca é modo livre, e um arquivo baixado por lá que travasse
    /// deixaria o app mais restrito offline do que online. Ver
    /// `OrigemDoDownload`.
    private fun prazoDe(item: Baixado): Long? =
        if (item.ficha.origemTipada.expira) item.ficha.venceEm else null

    /// O prazo em palavra — `vence em 3 dias`, `vence amanhã`, `vence hoje`.
    ///
    /// ## ⚠️ Ela era uma data, e virou frase
    ///
    /// A versão anterior escrevia `vence 12/08` com um `SimpleDateFormat`. Duas
    /// coisas erradas nisso, e a segunda é a que importa:
    ///
    /// 1. `12/08` obriga quem lê a fazer a conta contra o calendário de hoje —
    ///    e a resposta que a pessoa quer é «dá tempo?», não «que dia é».
    /// 2. **A locadora já dizia a mesma coisa com outras palavras.** A cinta da
    ///    caixa escreve `vence amanhã` pelo `prazoDoEmprestimo`, e é o mesmo
    ///    empréstimo, a mesma fita — dois lugares do app falando do mesmo prazo
    ///    com gramáticas diferentes parecem dois prazos.
    ///
    /// Agora as duas telas leem a mesma função. O `Instant` vem do epoch que o
    /// download guardou; a locadora entra pelo ISO do servidor. Mesma frase.
    ///
    /// `null` quando não há prazo — todo download de biblioteca (§24).
    fun prazoEmPalavra(item: Baixado): Pair<String, Int>? = prazoDe(item)?.let { epoch ->
        prazoDoEmprestimo(java.time.Instant.ofEpochMilli(epoch).toString())
    }

    /// O caminho da arte vira URL.
    ///
    /// ⚠️ **E ela vem da rede, numa tela que existe pra quando não há rede.**
    /// Isso é aceito, e o motivo é o custo: guardar a arte no disco junto do
    /// filme resolveria o caso offline puro e criaria um segundo arquivo por
    /// download pra gerenciar, apagar e migrar — bytes de gestão pra um cartaz.
    ///
    /// Na prática o Coil já cobre quase todos os casos: quem baixou o filme
    /// **viu a arte** pra chegar até o botão de baixar, e ela ficou no cache de
    /// disco dele. O que sobra é o aparelho que baixou, limpou o cache e ficou
    /// sem rede — e aí o cartão desenha só a cor, com o título por cima, que
    /// continua legível.
    fun arte(caminho: String?): String? = odeon.urlDaArte(caminho)

    /// Onde este filme parou, **antes** de abrir o player.
    ///
    /// ## ⚠️ A primeira versão começava do zero, e isso apagava dado real
    ///
    /// O raciocínio parecia certo: a posição mora na obra, no servidor, e esta
    /// tela existe justamente pra quando não há servidor — então começa do
    /// começo. O que ele não considerou é que **o player escreve**. O heartbeat
    /// manda a posição atual pra `POST /api/works/{obra}/progress` a cada poucos
    /// segundos, e um filme aberto no zero grava zero: dez segundos de
    /// reprodução e o «faltam 141min» de quem tinha visto metade vira «faltam
    /// 2h22». Perda silenciosa, sobre dado de uma pessoa real, sem nada na tela
    /// que denuncie.
    ///
    /// ## E a correção não custa o offline
    ///
    /// Com rede, pergunta e abre onde parou. **Sem rede, a chamada falha e cai
    /// em zero — que é exatamente o certo nesse caso**, porque sem rede o
    /// heartbeat também não sobe: não há posição pra apagar. O erro só existia
    /// no cruzamento «tem rede, mas a tela decidiu ignorá-la».
    ///
    /// A viagem acontece **depois do toque** e antes de o player montar. Ela
    /// custa uma ida ao servidor de casa no caminho de abrir um filme — o mesmo
    /// que a ficha já paga —, e falhar não bloqueia nada.
    fun tocar(item: Baixado, aoAbrir: (ondeParou: Double) -> Unit) {
        viewModelScope.launch {
            val posicao = runCatching { odeon.obra(item.ficha.obraId).ondeParou }.getOrDefault(0.0)
            aoAbrir(posicao)
        }
    }

    fun apagar(id: String) {
        baixados.apagar(id)
        _estado.value = EstadoDosBaixados(baixados.lista())
    }
}
