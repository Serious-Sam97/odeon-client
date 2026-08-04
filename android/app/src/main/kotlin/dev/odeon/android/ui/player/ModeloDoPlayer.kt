package dev.odeon.android.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.FolhaDeSprites
import dev.odeon.android.dados.PlanoDeReproducao
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/// Uma legenda pronta pra entrar no `MediaItem`.
///
/// ## O rótulo vem do servidor, e não se reescreve aqui
///
/// A web deixa o recado no `api.ts`: «Rótulo pronto, vindo do servidor — não
/// reimplemente aqui». Montar "Português (forçada)" no cliente seria a terceira
/// redação da mesma frase entre web, Android e servidor — e a terceira que
/// diverge no dia em que alguém mexer numa só.
data class LegendaOferecida(
    val indice: Int,
    val rotulo: String,
    val idioma: String?,
    val url: String,
)

data class EstadoDoPlayer(
    val preparando: Boolean = true,
    /// O id do arquivo. Vai pro `MediaItem` como chave de cache, pra o player
    /// achar no disco o que o download escreveu. Ver `TelaDoPlayer`.
    val arquivoId: String = "",
    val titulo: String = "",
    /// A URL final, com o token pendurado. `null` enquanto não se sabe.
    val url: String? = null,
    /// `true` quando a fonte é HLS — o player precisa saber pra montar a fonte
    /// certa, e é a única coisa que ele precisa saber sobre o modo.
    val eHls: Boolean = false,
    val plano: PlanoDeReproducao? = null,
    val comecarEm: Long = 0L,
    val erro: String? = null,
    /// A folha de sprites do preview de seek, quando o servidor já gerou a
    /// desta obra. `null` é normal e não é falha — o player só degrada pra um
    /// arrasto sem miniatura.
    val folha: FolhaDeSprites? = null,
    val urlDaFolha: String? = null,
    /// **Não** é "não há folha". É "a pergunta pela folha falhou", e os dois
    /// desenham coisas diferentes — ver `pedirFolhaDeSprites`.
    val erroDaFolha: String? = null,
    /// As legendas que dá pra oferecer, já com a URL montada.
    val legendas: List<LegendaOferecida> = emptyList(),
    /// O quanto a sessão de HLS já pulou antes do primeiro quadro.
    ///
    /// ## Sem ele o "continuar" volta pro lugar errado
    ///
    /// Em `direct_play` a posição do player **é** a posição no arquivo, e isto
    /// vale zero. Em HLS não: a sessão é aberta com `start=N`, então o segundo
    /// zero do player é o segundo N do filme. Mandar `currentPosition` cru
    /// gravaria a posição deslocada — quem parou em 1h20 voltaria pra 0h00 na
    /// próxima vez, e a marca de "assistido até o fim" nunca fecharia.
    ///
    /// A web tem o mesmo campo e o mesmo comentário no `Player.tsx`, escrito
    /// depois de o defeito acontecer lá. Aqui ele entrou antes, por leitura.
    val deslocamentoMs: Long = 0L,
    /// Se o plano em vigor foi montado **pro Chromecast**, e não pro celular.
    ///
    /// A tela precisa disto pro selo não mentir: durante um cast, `direto` e
    /// `transcodificando` falam da TV, não deste aparelho. Afirmar sobre o
    /// celular uma coisa que é da sala é o §18 por outro caminho — e a §4c
    /// manda explicitamente a tela dizer de quem está falando.
    val paraCast: Boolean = false,
    /// A duração de verdade, em milissegundos. `0` quando o servidor não sabe.
    ///
    /// ## Ela existe porque `Player.duration` mente durante a transcodificação
    ///
    /// Medido no emulador em 04/08/2026, num filme de **2h22** servido por HLS
    /// com o selo `transcodificando`: aos 38s de reprodução o player dizia que a
    /// duração total era **14:07**; um minuto depois, **34:48**. A playlist está
    /// sendo escrita enquanto se assiste, e `duration` só enxerga o que já foi
    /// gerado.
    ///
    /// Três coisas quebravam com isso, e a terceira é a pior:
    ///
    ///   1. a linha do tempo — a fração é `posição/duração`, e com o
    ///      denominador crescendo o traço **anda pra trás**
    ///   2. o preview de seek — o segundo do quadro sai de `fração × duração`,
    ///      então a miniatura mostrava outro trecho do filme
    ///   3. **a marca de progresso** — ela manda `duration_seconds` pro servidor,
    ///      e estava gravando 14 minutos como sendo a duração de um filme de
    ///      2h22. Isso não some ao fechar o app: fica no banco, e é o número que
    ///      a fase 3 vai usar pra decidir se a obra foi vista até o fim.
    ///
    /// O conserto é não perguntar ao player o que o servidor já sabe: a duração
    /// vem do probe do arquivo e viaja desde a ficha.
    val duracaoConhecidaMs: Long = 0L,
)

/// Quem decide **como** o filme chega, antes de o player existir.
///
/// ## Ele repergunta o plano, e não confia no da ficha
///
/// A ficha já mostrou um selo, mas aquele plano pode ter minutos de idade — e
/// entre uma coisa e outra o servidor pode ter mudado de ideia (outra sessão de
/// transcodificação ocupando a CPU, por exemplo). Reperguntar custa uma
/// requisição e evita preparar o player pra uma fonte que não existe mais.
///
/// ## E ele NÃO renova o token de mídia
///
/// É o §43, e é a decisão que a fase 1 já tinha tomado: emitir um token novo
/// aposenta o anterior, e o anterior é justamente o que está dentro deste
/// player. Aqui a gente só usa o que já existe.
class ModeloDoPlayer(
    private val odeon: RepositorioOdeon,
    private val obraId: String,
    private val arquivoId: String,
    titulo: String,
    ondeParou: Double,
    duracaoEmSegundos: Double?,
) : ViewModel() {

    /// A sessão de HLS aberta por este player, se houve.
    ///
    /// ## Ela precisa ser fechada, e esquecer disso custa CPU de casa
    ///
    /// Cada sessão é um **ffmpeg vivo** no `serious-server`. A web fecha a dela
    /// ao sair, com o comentário: «sem isto o ffmpeg fica vivo até o reaper
    /// passar». Aqui a primeira versão não fechava nenhuma — e o efeito colateral
    /// disso é a explicação mais provável de um mistério que apareceu antes:
    ///
    /// O mesmo arquivo devolveu `direto` numa execução e `transcodificando` na
    /// outra, com as capacidades declaradas **idênticas** nas duas (conferido no
    /// log: `a=aac,mp3,opus,vorbis,flac`, sem ac3 sempre). Se o servidor
    /// considera uma sessão já aberta ao montar o plano, sessões abandonadas por
    /// mim é exatamente o que faria a resposta oscilar sem nada ter mudado do
    /// lado de cá.
    ///
    /// Não está provado — é hipótese com uma medida a favor. Mas fechar a sessão
    /// é certo de qualquer jeito.
    @Volatile
    private var sessaoAberta: String? = null

    private val _estado = MutableStateFlow(
        EstadoDoPlayer(
            arquivoId = arquivoId,
            titulo = titulo,
            comecarEm = (ondeParou * 1000).toLong(),
            duracaoConhecidaMs = ((duracaoEmSegundos ?: 0.0) * 1000).toLong(),
        ),
    )
    val estado: StateFlow<EstadoDoPlayer> = _estado.asStateFlow()

    init {
        preparar(paraCast = false)
        pedirFolhaDeSprites()
    }

    /// As legendas que este player consegue mostrar.
    ///
    /// ## Só as de texto, e a omissão é honesta
    ///
    /// O servidor marca `text_based`. As que não são — PGS e VOBSUB, que são
    /// **imagem** — não viram WebVTT: mostrá-las exigiria o servidor queimá-las
    /// no vídeo (é o que o `burn_subtitle` do plano faz), e isso muda o modo de
    /// reprodução inteiro, não a faixa.
    ///
    /// Oferecer uma faixa que não vai aparecer é o §53 ao contrário. Então elas
    /// somem da lista — e voltam quando houver a decisão sobre queimar, que é
    /// escolha de produto e custa CPU do servidor de casa.
    private fun legendasDe(plano: PlanoDeReproducao): List<LegendaOferecida> =
        plano.subtitles
            .filter { it.baseadaEmTexto }
            .mapNotNull { faixa ->
                val url = odeon.urlDeMidia("/api/media/$arquivoId/subtitles/${faixa.index}")
                    ?: return@mapNotNull null
                LegendaOferecida(
                    indice = faixa.index,
                    rotulo = faixa.label,
                    idioma = faixa.language,
                    url = url,
                )
            }

    /// A folha vai em paralelo com o plano, e falhar nela não atrapalha nada.
    ///
    /// Ela é enfeite funcional: sem folha o arrasto continua funcionando, só não
    /// mostra a miniatura. Por isso não entra no caminho que decide se dá pra
    /// tocar — esperar por ela seria atrasar o filme por causa do preview.
    ///
    /// ## "Não existe" e "não deu certo" são estados diferentes aqui
    ///
    /// A primeira versão disto era um `runCatching { }.getOrNull()`, que é
    /// exatamente o defeito que a web pagou caro e deixou escrito: lá um 401
    /// virou "não há sprite", em silêncio, **pra todo arquivo do acervo** — e os
    /// sprites que existiam no banco nunca apareceram, porque ninguém tinha como
    /// distinguir os dois casos olhando a tela.
    ///
    /// `RepositorioOdeon.folhaDeSprites` já devolve `null` só no 404. O que
    /// faltava era não jogar fora o resto: falha de verdade fica guardada e a
    /// tela diz uma linha discreta. Sem ela, "servidor fora do ar" e "este filme
    /// ainda não tem preview" seriam a mesma tela em branco.
    private fun pedirFolhaDeSprites() {
        viewModelScope.launch {
            try {
                val folha = odeon.folhaDeSprites(arquivoId) ?: return@launch
                _estado.update {
                    it.copy(folha = folha, urlDaFolha = odeon.urlDeMidia("/scrub/${folha.path}"))
                }
            } catch (e: Exception) {
                _estado.update { it.copy(erroDaFolha = e.message ?: "falhou") }
            }
        }
    }

    /// Onde eu parei, mandado ao servidor.
    ///
    /// Quem chama é a tela, que é quem tem o `Player` e sabe a posição. O
    /// `tipo` distingue o batimento periódico do fim da sessão — e é o fim que
    /// decide se amanhã dá pra continuar.
    ///
    /// ⚠️ Os nomes de `tipo` são **`progress` e `abandon`**, e não são escolha:
    /// são os que a web manda, e o servidor é o mesmo. A primeira versão daqui
    /// inventou `heartbeat` e `stop`, e o servidor devolveu **HTTP 500** — o que
    /// só apareceu porque a falha ia pro log. Ver `RepositorioOdeon.marcarProgresso`.
    fun marcar(posicaoDoPlayerMs: Long, duracaoDoPlayerMs: Long, tipo: String) {
        /// Tempo de **arquivo**, não tempo de sessão. Ver `deslocamentoMs`.
        val posicaoMs = posicaoDoPlayerMs + _estado.value.deslocamentoMs
        if (posicaoMs <= 0) return
        /// ⚠️ A duração que sobe é a **conhecida**, não a do player. Ver o campo
        /// `duracaoConhecidaMs` pro que a do player grava no banco quando a
        /// fonte é HLS em geração.
        val duracaoMs = _estado.value.duracaoConhecidaMs.takeIf { it > 0 } ?: duracaoDoPlayerMs
        viewModelScope.launch {
            odeon.marcarProgresso(
                obraId = obraId,
                posicaoEmSegundos = posicaoMs / 1000.0,
                duracaoEmSegundos = duracaoMs.takeIf { it > 0 }?.let { it / 1000.0 },
                arquivoId = arquivoId,
                tipo = tipo,
            )
        }
    }

    /// Refaz o plano quando a reprodução muda de aparelho.
    ///
    /// É o que a §4c chama de "a negociação muda de sujeito": o mesmo arquivo
    /// pode ser `direct_play` no celular e `transcode` na TV, porque os codecs
    /// declarados passam a ser os dela.
    fun mudouParaCast(paraCast: Boolean) {
        if (_estado.value.paraCast == paraCast) return
        preparar(paraCast = paraCast)
    }

    private fun preparar(paraCast: Boolean) {
        viewModelScope.launch {
            _estado.update { it.copy(preparando = true, erro = null, paraCast = paraCast) }
            try {
                val plano = odeon.plano(arquivoId, paraCast = paraCast)
                _estado.update { it.copy(legendas = legendasDe(plano)) }

                if (plano.eDireto) {
                    /// `direct_play`: o arquivo como está, sem o servidor tocar
                    /// num byte. É o caminho que o `mkv` na lista de contêineres
                    /// compra — a web não pede `mkv` porque o navegador não abre,
                    /// e este app abre.
                    val url = odeon.urlDeMidia(plano.urlDireta)
                        ?: error("plano direto sem URL")
                    _estado.update {
                        it.copy(preparando = false, url = url, eHls = false, plano = plano)
                    }
                } else {
                    /// Remux ou transcodificação: quem serve é uma sessão de HLS,
                    /// e ela **começa no segundo pedido**. Mandar o `start` aqui
                    /// é o que faz "continuar de onde parou" não custar o
                    /// servidor gerar desde o minuto zero.
                    val sessao = odeon.abrirSessao(
                        arquivoId = arquivoId,
                        comecandoEm = (_estado.value.comecarEm / 1000).toInt(),
                        paraCast = paraCast,
                    )
                    val url = odeon.urlDeMidia(sessao.urlDaPlaylist)
                        ?: error("sessão sem playlist")
                    /// Guardado pra ser encerrado em `onCleared`. Ver lá.
                    sessaoAberta = sessao.id
                    _estado.update {
                        it.copy(
                            preparando = false,
                            url = url,
                            eHls = true,
                            plano = plano,
                            /// A sessão já começou no ponto pedido, então o
                            /// player abre no zero **dela**. Somar de novo
                            /// saltaria o dobro.
                            comecarEm = 0L,
                            /// E o que ela pulou vira o deslocamento, pra a
                            /// marca de progresso voltar a falar em tempo de
                            /// arquivo. Ver `deslocamentoMs`.
                            deslocamentoMs = it.comecarEm,
                        )
                    }
                }
            } catch (e: Exception) {
                _estado.update {
                    it.copy(preparando = false, erro = e.message ?: "não deu pra preparar a reprodução")
                }
            }
        }
    }

    fun tentarDeNovo() = preparar(_estado.value.paraCast)

    /// Fecha a sessão quando o player sai de cena.
    ///
    /// ⚠️ Num escopo **próprio**, e não no `viewModelScope`: este último já está
    /// cancelado quando `onCleared` roda, e uma corrotina lançada nele aqui
    /// morreria antes de a requisição sair — deixando o ffmpeg vivo com o código
    /// parecendo correto, que é o pior tipo de vazamento.
    override fun onCleared() {
        val id = sessaoAberta ?: return super.onCleared()
        sessaoAberta = null
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()).launch {
            odeon.encerrarSessao(id)
        }
        super.onCleared()
    }
}
