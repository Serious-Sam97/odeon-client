package dev.odeon.android.ui.aovivo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.CanalAberto
import dev.odeon.android.dados.CanalNoAr
import dev.odeon.android.dados.GradeDoOdeon
import dev.odeon.android.dados.Guia
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/// O que a tela do ao vivo sabe.
data class EstadoAoVivo(
    val carregando: Boolean = true,
    val canais: List<CanalNoAr> = emptyList(),
    val guia: Guia? = null,
    val doOdeon: GradeDoOdeon? = null,
    val escolhido: String? = null,
    /// Os `programme_id` com lembrete marcado — o que desenha a estrela.
    val lembretes: Set<Int> = emptySet(),
    /// Canais fixados pelo dono, na ordem em que ele fixou.
    ///
    /// ⚠️ Guardados **no aparelho**, não no servidor: são 23 canais e a resposta
    /// «quais são os meus» é da sala, não da casa. A TV da cozinha pode querer
    /// outros três.
    val favoritos: List<String> = emptyList(),
    /// Quantas horas a grade está deslocada pra frente. `0` é agora.
    val deslocamentoEmHoras: Int = 0,
    val erro: String? = null,
    /// ⚠️ O relógio **do servidor**, adiantado localmente — ver o cabeçalho.
    val agoraMs: Long = 0L,
)

/// Os canais ao vivo, a grade, e o que está no ar agora.
///
/// ## ⚠️ Esta é a primeira coisa que a TV ganha **antes** do celular
///
/// Todo o resto deste redesenho traduziu uma peça que já existia no `:app`. O ao
/// vivo não: ele nasce aqui. O argumento é do dono e é bom — um canal ao vivo é
/// uma coisa de **sala**. Zapear com o polegar num ônibus é percorrer uma lista;
/// zapear com o controle no sofá é televisão.
///
/// O modelo mora no `:core` mesmo assim, pela régua de sempre: ele não desenha.
/// Quando o celular quiser o ao vivo, acha tudo pronto.
///
/// ## ⚠️ O relógio é o do servidor, e isso governa o arquivo inteiro
///
/// A `Guia` traz `agora` junto com os programas, e o comentário da web diz por
/// quê numa frase que vale copiar: «a agulha do "agora" tem que ser desenhada
/// contra o mesmo relógio que produziu a grade».
///
/// Numa TV isso é mais que rigor. O relógio de uma TCL sai de fábrica errado e só
/// acerta se alguém ligar a hora automática — e uma linha vermelha meia hora fora
/// do lugar não parece um relógio errado, parece uma **grade** errada.
///
/// Então o modelo guarda o `agora` do servidor e **anda com ele**: um segundo por
/// segundo, sem consultar o relógio local. A grade se repede de minuto em minuto,
/// e cada resposta reancora.
class ModeloAoVivo(private val odeon: RepositorioOdeon) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoAoVivo())
    val estado: StateFlow<EstadoAoVivo> = _estado.asStateFlow()

    init {
        carregar()
        andarComORelogio()
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            val canais = odeon.canaisAoVivo()
            /// ⚠️ **Doze horas, e não as quatro/cinco de antes.**
            ///
            /// A grade não rolava, e o dono viu: «a timeline tá fixa, eu não
            /// consigo ir além dos horários já mostrados». A rolagem horizontal
            /// estava lá — mas seis horas a 1,6dp por minuto dão 576dp, e a
            /// janela tem ~800dp. **Não havia o que rolar**: o conteúdo cabia
            /// inteiro, e o dado nem chegava a tanto.
            ///
            /// Doze horas dão 1152dp — mais largo que a tela, que é a condição
            /// pra existir «pra frente». E é o horizonte certo pra pergunta que a
            /// grade responde: «o que passa hoje à noite?»
            val guia = odeon.guiaAoVivo(horas = 12)
            val doOdeon = odeon.gradeDoOdeon(horas = 12)
            /// ⚠️ Os lembretes vêm junto e não numa segunda ida: a grade sem
            /// eles é uma grade que mente por omissão — mostra o programa e
            /// esconde que você já pediu pra ser avisado dele.
            val lembretes = odeon.lembretes().map { it.programaId }.toSet()
            val favoritos = odeon.favoritosDeCanal()

            /// ⚠️ **O relógio sai da grade que veio**, com preferência pela do
            /// Odeon: ela é calculada na hora do pedido, então o `agora` dela é o
            /// mais fresco. Sem nenhuma das duas, cai no relógio local — que é o
            /// pior caso, e está anotado como tal em vez de escondido.
            val marca = doOdeon?.agora ?: guia?.agora
            val relogio = marca?.let { emMillis(it) }?.takeIf { it > 0 }
                ?: System.currentTimeMillis()

            _estado.update {
                it.copy(
                    carregando = false,
                    lembretes = lembretes,
                    favoritos = favoritos,
                    canais = canais,
                    guia = guia,
                    doOdeon = doOdeon,
                    agoraMs = relogio,
                    escolhido = it.escolhido ?: primeiroNoAr(canais, doOdeon),
                    erro = if (canais.isEmpty() && doOdeon == null) "nenhum canal no ar" else null,
                )
            }
        }
    }

    /// O relógio anda sozinho, e a grade se repede de minuto em minuto.
    ///
    /// ⚠️ **Um segundo por segundo, e não `currentTimeMillis` a cada quadro.** O
    /// ponto de guardar o relógio do servidor é justamente não consultar o da TV;
    /// ler o local pra «corrigir» a deriva desfaria a decisão inteira.
    private fun andarComORelogio() {
        viewModelScope.launch {
            var desdeORepedido = 0
            while (true) {
                delay(1_000)
                _estado.update { if (it.agoraMs > 0) it.copy(agoraMs = it.agoraMs + 1_000) else it }
                if (++desdeORepedido >= 60) {
                    desdeORepedido = 0
                    carregar()
                }
            }
        }
    }

    fun escolher(canalId: String) = _estado.update { it.copy(escolhido = canalId) }

    /// ## Os lembretes
    ///
    /// ⚠️ A estrela só acende **depois** da confirmação do servidor. Pintar antes
    /// é o otimismo que faz alguém perder o programa: um lembrete que parece
    /// marcado e não está mente sobre a única coisa que ele promete.
    fun alternarLembrete(programaId: Int) {
        viewModelScope.launch {
            val tinha = programaId in _estado.value.lembretes
            val deu = if (tinha) odeon.desmarcarLembrete(programaId) else odeon.marcarLembrete(programaId)
            if (!deu) return@launch
            _estado.update {
                it.copy(
                    lembretes = if (tinha) it.lembretes - programaId else it.lembretes + programaId,
                )
            }
        }
    }

    /// ## Os favoritos
    ///
    /// ⚠️ A ordem é a de **fixação**, não alfabética nem a do servidor: quem
    /// fixou primeiro fica em cima, porque foi a primeira resposta que a pessoa
    /// deu à pergunta «quais são os seus canais».
    fun alternarFavorito(canalId: String) {
        viewModelScope.launch {
            val atuais = _estado.value.favoritos
            val novos = if (canalId in atuais) atuais - canalId else atuais + canalId
            odeon.guardarFavoritosDeCanal(novos)
            _estado.update { it.copy(favoritos = novos) }
        }
    }

    /// ## A janela da grade
    ///
    /// ⚠️ Deslocar **não recarrega**: a grade já vem com horas à frente, e o que
    /// muda é onde a régua começa. Um pedido novo a cada seta seria trocar
    /// latência por nada — e numa TV a seta se aperta rápido.
    fun deslocar(horas: Int) = _estado.update {
        it.copy(deslocamentoEmHoras = (it.deslocamentoEmHoras + horas).coerceIn(0, 12))
    }

    suspend fun sintonizar(canalId: String): CanalAberto? = odeon.sintonizar(canalId)

    fun arte(caminho: String?): String? = odeon.urlDaArte(caminho)

    /// A playlist de um canal externo, **absoluta e com token**.
    ///
    /// ## ⚠️ O `playlist_url` que o servidor manda é um caminho, não uma URL
    ///
    /// Ele vem como `/api/hls/<sessão>/index.m3u8`. Entregue assim ao ExoPlayer,
    /// isso não é «relativo ao servidor» — é um **caminho de arquivo local**, e
    /// foi exatamente o que aconteceu na TCL:
    ///
    /// ```
    /// FileNotFoundException: /api/hls/5793acb4-…/index.m3u8:
    ///   open failed: ENOENT (No such file or directory)
    ///     at androidx.media3.datasource.FileDataSource.openLocalFile
    /// ```
    ///
    /// `FileDataSource` na pilha é a assinatura do defeito: o player foi procurar
    /// no cartão de memória. Sem esquema e sem host, ninguém tem como saber que
    /// aquilo era pra ser HTTP.
    ///
    /// ⚠️ E o token vai junto porque o HLS **não passa pelo Retrofit**: quem baixa
    /// a playlist e os segmentos é o ExoPlayer, com o próprio cliente HTTP, sem os
    /// cabeçalhos que o interceptor da casa adiciona. A `urlDeMidia` resolve os
    /// dois de uma vez — é a mesma que o player de filme usa.
    fun playlist(caminho: String?): String? = odeon.urlDeMidia(caminho)

    /// Ver `RepositorioOdeon.cabecalhosDeMidia` — sem eles o segmento volta 401.
    fun cabecalhos(): Map<String, String> = odeon.cabecalhosDeMidia()

    private fun primeiroNoAr(canais: List<CanalNoAr>, doOdeon: GradeDoOdeon?): String? =
        canais.firstOrNull { it.titulo != null }?.id
            ?: canais.firstOrNull()?.id
            ?: doOdeon?.canais?.firstOrNull()?.slug
}

/// `2026-08-13T07:19:00Z` → millis. `0` quando não dá pra ler.
///
/// ⚠️ Zero e não «agora»: quem desenha trata 0 como «não sei que horas são», e
/// nunca como meia-noite de 1970. Devolver o relógio local aqui esconderia uma
/// data ilegível atrás de um número plausível, que é o §18 ao contrário.
fun emMillis(quando: String): Long =
    runCatching { java.time.Instant.parse(quando).toEpochMilli() }.getOrDefault(0L)
