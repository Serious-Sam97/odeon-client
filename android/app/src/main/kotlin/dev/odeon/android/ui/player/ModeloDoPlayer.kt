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

/// Alguém que está no mesmo filme **agora**.
data class NaSala(val nome: String, val posicaoEmSegundos: Double)

data class EstadoDoPlayer(
    val preparando: Boolean = true,
    /// O id do arquivo. Vai pro `MediaItem` como chave de cache, pra o player
    /// achar no disco o que o download escreveu. Ver `TelaDoPlayer`.
    val arquivoId: String = "",
    val titulo: String = "",
    /// A capa da obra, pro controle de mídia — R9. `null` é o caso comum e a
    /// notificação simplesmente sobe sem arte, em vez de com um quadrado vazio.
    val capaUrl: String? = null,
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
    /// ## As cenas do filme, pra encher a tira **sem depender de job nenhum**
    ///
    /// ⚠️ Elas são a resposta a uma pergunta do dono que eu tinha respondido
    /// mal: «por que o web consegue ter os capítulos e você não?». Eu havia
    /// tratado a folha de sprites e as cenas como **alternativas**, e concluído
    /// que sem a folha não havia imagem — o que deixou a tira cinza.
    ///
    /// São dois mecanismos, e o segundo não precisa de varredura:
    ///
    /// | | folha de sprites | cenas |
    /// |---|---|---|
    /// | quando | trabalho em lote, uma vez por arquivo | **sob demanda** |
    /// | quantas | uma a cada `interval_seconds` | **doze** |
    /// | custo | decodifica o arquivo inteiro | ~3s medido, e fica em cache |
    ///
    /// Doze pontos não servem pra preview de arrasto — é pouca resolução. Mas a
    /// tira mostra **11 ou 12 células** num celular, e aí doze é exatamente a
    /// conta. Cada célula pega a cena mais próxima do instante que ela
    /// representa.
    ///
    /// A folha continua sendo melhor quando existe, e por isso ela tem
    /// precedência: ela dá o quadro **daquele** instante, e não o mais próximo.
    val cenas: List<dev.odeon.android.dados.Cena> = emptyList(),
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
    /// As faixas de áudio do **arquivo**, vindas do plano — ver
    /// `dados.FaixaDeAudio`. Uma só, ou nenhuma, na maior parte do acervo.
    val faixasDeAudio: List<dev.odeon.android.dados.FaixaDeAudio> = emptyList(),
    /// Qual delas está tocando. `null` até o primeiro plano responder; depois é
    /// o que o servidor confirmou em `plan.audio_track`, e não o que a tela
    /// pediu — se o pedido for recusado, a tela mostra o que **é**, não o que
    /// ela quis.
    val faixaDeAudioEmUso: Int? = null,
)

/// ## As duas conversões entre o tempo da sessão e o tempo do filme
///
/// Elas existem porque o `deslocamentoMs` estava sendo aplicado em **dois**
/// lugares — a marca de progresso e a perseguição — e esquecido em todos os
/// outros. O resultado foi fotografado em 06/08/2026, retomando *007: A Serviço
/// Secreto* aos **1h22** por HLS: o filme aparecia certo, Blofeld no chalé, e o
/// relógio dizia **`0:43`** com «faltam **2:21:35**» — o filme inteiro. A janela
/// do projetor morava na primeira célula da tira.
///
/// A prova de que era só a tela: o servidor recebeu a posição certa o tempo
/// todo. A biblioteca foi de `faltam 63min` pra `faltam 60min` enquanto o cromo
/// anunciava o filme inteiro pela frente.
///
/// Espalhar `+ deslocamentoMs` pelos sete pontos de uso é o que criou o defeito
/// da primeira vez. Aqui elas são duas funções com nome, e o `TelaDoPlayer`
/// converte **na borda**: tudo que a tela desenha é tempo de filme, e só os
/// `seekTo` voltam pra tempo de sessão.
///
/// ⚠️ Isso **só morde com `deslocamentoMs > 0`**, que é continuar um filme que
/// vem por HLS. Em Direct Play o deslocamento vale zero e as duas funções são a
/// identidade — que é exatamente por que 109 testes verdes e um lint limpo não
/// pegaram nada, e por que a tira foi dada como verificada ontem: ela tinha sido
/// aberta num filme começando do zero.

/// O segundo do **filme** que o player está mostrando.
///
/// Em `direct_play` a posição do player já é a do arquivo e isto devolve o que
/// recebeu. Em HLS a sessão foi aberta com `start=N`, então o segundo zero do
/// player é o segundo N do filme — ver `EstadoDoPlayer.deslocamentoMs`.
fun tempoDeFilme(posicaoDaSessaoMs: Long, deslocamentoMs: Long): Long =
    posicaoDaSessaoMs + deslocamentoMs

/// O caminho de volta: pra onde mandar o player quando alguém escolheu um
/// segundo **do filme** — tocando na tira, arrastando, ou saltando 10s pra trás.
///
/// ⚠️ **O piso em zero não é higiene, é o que a sessão contém.** Uma sessão
/// aberta em 1h19 não tem o minuto 3 do filme: aqueles segmentos nunca foram
/// gerados. Pedir tempo negativo ao player não volta pro começo do filme — ele
/// simplesmente não sabe do que se está falando.
///
/// Então o piso é honesto: rebobinar antes do ponto onde a sessão começou para
/// **no começo da sessão**. Voltar de verdade exigiria abrir outra sessão de
/// HLS, que é outro pedido e custa um ffmpeg novo no `serious-server`.
fun tempoDeSessao(posicaoDoFilmeMs: Long, deslocamentoMs: Long): Long =
    (posicaoDoFilmeMs - deslocamentoMs).coerceAtLeast(0)

/// O que dizer quando a reprodução morre no meio.
///
/// ## Por que não mostrar o que o Media3 diz
///
/// Ele diz `ERROR_CODE_IO_BAD_HTTP_STATUS`. Isso é um nome de constante, e a
/// pessoa que está tentando assistir um filme não tem o que fazer com ele. O §8b
/// manda não falhar calado, mas falar em código é uma forma de calar.
///
/// ⚠️ **E não se inventa causa.** Cada frase aqui sai de um código que o Media3
/// afirma, e não de palpite sobre o que "provavelmente" aconteceu — §18. Onde ele
/// não afirma nada útil (`ERROR_CODE_IO_UNSPECIFIED` e os que não estão
/// mapeados), a frase é genérica de propósito, e a mensagem crua vai junto entre
/// parênteses pra quem for investigar.
///
/// ## O caso do HLS é diferente, e a web já tinha escrito o porquê
///
/// Segmento que devolve 404 ou status ruim **numa sessão de transcodificação**
/// quase sempre é o trecho não existir: o `ffmpeg` escreve do começo ao fim, e
/// alcançar outro ponto exige outra sessão. O `Player.tsx` da web diz isso em
/// texto — «esse trecho não está nesta sessão de transcode — feche e abra de novo
/// a partir dali».
///
/// Aqui a frase é a mesma menos a instrução, porque o app **faz** o que a web
/// pede que a pessoa faça: o `tentar de novo` refaz plano e sessão no ponto onde
/// parou. Mandar alguém fechar e abrir seria descrever um trabalho que o botão
/// ao lado já executa.
fun frasePraFalha(erro: androidx.media3.common.PlaybackException, eHls: Boolean): String =
    frasePraFalha(erro.errorCode, erro.message, eHls)

/// A mesma decisão, sobre **dados** em vez da exceção.
///
/// ⚠️ Ela existe separada por um motivo prático: `PlaybackException` carimba
/// `SystemClock.elapsedRealtime()` no construtor, e num teste de JVM isso estoura
/// com «not mocked». Testar a frase não devia exigir um emulador — então o que se
/// testa é a regra, e a exceção só entrega os dois campos que ela lê.
fun frasePraFalha(codigo: Int, mensagem: String?, eHls: Boolean): String {
    return when {
        codigo == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            codigo == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "a conexão com o servidor caiu no meio do filme"

        eHls && (
            codigo == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                codigo == androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
            ) ->
            "este trecho não está na sessão de transcodificação que estava aberta"

        codigo == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            codigo == androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "o servidor não entregou o arquivo"

        /// ⚠️ **As duas famílias do aparelho são 3xxx e 4xxx, e não uma só** — o
        /// teste é que cobrou: eu tinha escrito `3000..3999` para o decodificador,
        /// e `ERROR_CODE_DECODING_FAILED` vale **4003**. A faixa 3000 é de
        /// *parsing*: contêiner ou manifesto malformado ou não suportado.
        ///
        /// Elas dizem coisas diferentes e por isso não se juntam: 4xxx é «este
        /// aparelho não dá conta», 3xxx é «o que chegou está quebrado ou é de um
        /// formato que ele não lê». A primeira não adianta insistir; a segunda
        /// pode ser uma playlist truncada, que uma sessão nova resolve.
        codigo in 4000..4999 -> "este aparelho não deu conta de decodificar o filme"

        codigo in 3000..3999 -> "o que chegou do servidor veio num formato que não deu pra ler"

        else -> "a reprodução parou" + (mensagem?.let { " ($it)" } ?: "")
    }
}

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
    capaUrl: String?,
    /// O barramento, quando há — é por ele que este player sabe que **outro
    /// aparelho** mexeu na mesma obra.
    private val barramento: dev.odeon.android.dados.Barramento? = null,
) : ViewModel() {

    /// Quem eu sou, pra distinguir «outro aparelho meu» de «outra pessoa».
    ///
    /// Perguntado uma vez ao entrar no player. Falhar aqui não quebra nada: sem
    /// resposta, o player volta a perseguir todo mundo, que é o que ele fazia
    /// antes de o `user_id` existir.
    private var meuUsuarioId: String? = null

    init {
        viewModelScope.launch {
            meuUsuarioId = runCatching { odeon.quemSouEu().id }.getOrNull()
        }
    }

    /// Pra onde o outro aparelho mandou o filme. `null` quando não há nada a
    /// perseguir — que é o estado normal de quem assiste sozinho.
    private val _perseguir = kotlinx.coroutines.flow.MutableStateFlow<Double?>(null)
    val perseguir: kotlinx.coroutines.flow.StateFlow<Double?> = _perseguir

    /// A sincronia entre aparelhos — o que o barramento existe pra fazer.
    ///
    /// ## Os 5 segundos, e por que há uma tolerância
    ///
    /// O número é da web (§1.4 da referência): «persegue a posição do outro
    /// aparelho se a diferença passar de 5s». A tolerância não é folga
    /// preguiçosa — é o que separa **duas pessoas vendo a mesma obra em
    /// aparelhos diferentes**, cujo progresso chega a toda hora e difere por
    /// segundos, de **alguém que pulou de propósito**, que difere por minutos.
    ///
    /// Sem ela, cada batida de progresso do outro aparelho daria um pulinho no
    /// filme de quem está assistindo aqui.
    ///
    /// ⚠️ O eco do próprio aparelho já foi descartado lá no `Barramento`, pelo
    /// `device_id`. Se não fosse, este seria o pior lugar pra descobrir isso: o
    /// player perseguiria a própria posição de um segundo atrás, pra sempre.
    init {
        ouvirOutrosAparelhos()
    }

    private fun ouvirOutrosAparelhos() {
        val barramento = barramento ?: return
        viewModelScope.launch {
            barramento.eventos.collect { evento ->
                if (evento !is dev.odeon.android.dados.EventoDoServidor.Progresso) return@collect
                if (evento.obraId != obraId) return@collect

                /// ## ⚠️ Perseguir **só a si mesmo** — 05/08/2026
                ///
                /// Até hoje o player perseguia a posição de **qualquer** evento
                /// de progresso da mesma obra, porque o evento não dizia de quem
                /// era. Isso estava certo enquanto a única leitura possível era
                /// «outro aparelho»; com o `user_id` no ar, ficou errado.
                ///
                /// O defeito, medido no aparelho: com o filme rodando aos 2min,
                /// um progresso de outra conta aos 1h25 **fez o filme pular** pra
                /// 1h25. Ou seja: o rudney assistir o mesmo filme na sala
                /// arrastaria o seu.
                ///
                /// A regra certa separa as duas coisas que o mesmo evento
                /// carrega:
                ///
                /// | de quem | o que fazer |
                /// |---|---|
                /// | **de outro aparelho seu** | perseguir — é o «parou na TV, continua no ônibus» |
                /// | **de outra pessoa** | **marcar na tira**, e não tocar no seu filme |
                ///
                /// ⚠️ Sem saber quem eu sou, o comportamento antigo continua
                /// valendo. É o conservador: `meuUsuarioId` nulo só acontece
                /// enquanto a resposta do `auth/me` não chegou, e nesse instante
                /// perseguir é o que o app fazia ontem.
                val meu = meuUsuarioId
                val deOutraPessoa = meu != null && evento.userId != null && evento.userId != meu

                if (!deOutraPessoa) {
                    _perseguir.value = evento.posicaoEmSegundos
                    return@collect
                }

                /// A marca de quem está no mesmo filme agora.
                ///
                /// ⚠️ **Chaveada por pessoa, e não por evento.** O heartbeat
                /// manda a posição a cada dez segundos: guardar a lista de
                /// eventos encheria a tira de marcas do mesmo rudney a cada dez
                /// segundos de filme. O mapa sobrescreve — cada pessoa tem uma
                /// marca, na última posição que ela mandou.
                val quem = evento.userId ?: return@collect
                _naSala.value = _naSala.value + (
                    quem to NaSala(
                        /// Sem nome não há marca: um ponto anônimo na timeline
                        /// não diz nada e ainda ocupa espaço (§24).
                        nome = evento.quemNome ?: return@collect,
                        posicaoEmSegundos = evento.posicaoEmSegundos,
                    )
                    )
            }
        }
    }

    /// Quem mais está neste filme **agora**, por conta.
    ///
    /// ## ⚠️ É presença, e não histórico — e isso limita o que ela promete
    ///
    /// O barramento só fala quando alguém **mexe**. Abrir um filme não traz nada
    /// sobre quem o assistiu ontem: as marcas nascem enquanto você assiste, e só
    /// pra quem estiver assistindo junto.
    ///
    /// Numa casa de três pessoas isso é raro — e é justamente por ser raro que
    /// vale: quando acontece, você **vê** a outra pessoa andando na sua timeline.
    /// A versão histórica pediria uma rota que devolvesse a posição de todo mundo
    /// numa obra, e ela não existe.
    ///
    /// O mapa nunca é limpo enquanto a tela vive: quem apareceu continua marcado
    /// mesmo que pare de mandar. Uma marca que some sozinha depois de dez
    /// segundos piscaria na tira toda vez que alguém pausasse.
    private val _naSala = MutableStateFlow<Map<String, NaSala>>(emptyMap())
    val naSala: StateFlow<Map<String, NaSala>> = _naSala.asStateFlow()

    /// Consumido por quem já pulou. Sem isto, a mesma posição seria perseguida a
    /// cada recomposição — e o filme ficaria preso naquele segundo.
    fun jaPerseguiu() {
        _perseguir.value = null
    }

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
            capaUrl = capaUrl,
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
    /// A URL de uma imagem de cena. Mesmo caminho da arte do resto do app.
    fun arteDaCena(caminho: String): String? = odeon.urlDaArte(caminho)

    private fun pedirFolhaDeSprites() {
        /// ## As cenas, em paralelo com a folha
        ///
        /// Elas não competem: a folha, quando existe, é melhor — dá o quadro
        /// **daquele** instante em vez do mais próximo. As cenas são o que
        /// garante que a tira tenha imagem **hoje**, em qualquer filme, sem
        /// depender de a varredura de sprites ter passado por ele.
        ///
        /// A rota falha em silêncio por dentro do repositório (devolve lista
        /// vazia), e é o certo: sem cenas a tira desenha as células vazias, que
        /// é película não revelada e não erro.
        viewModelScope.launch {
            val cenas = odeon.cenasDoDisco(obraId)
            if (cenas.isNotEmpty()) _estado.update { it.copy(cenas = cenas) }
        }
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

    /// Troca a faixa de áudio, refazendo o plano e a sessão.
    ///
    /// ## ⚠️ Por que isto não é um `TrackSelectionOverride` e pronto
    ///
    /// Porque em transcodificação **a outra faixa não está na playlist**. O
    /// `ffmpeg` daquela sessão foi lançado com `-map 0:a:N` e não muda de ideia
    /// no meio — trocar exige sessão nova, exatamente como o `start` do
    /// "continuar de onde parou". É a mesma frase que o servidor registrou ao
    /// entregar o parâmetro.
    ///
    /// ## E o plano é refeito, não só a sessão
    ///
    /// O `mode` depende do codec **da faixa escolhida**. Num `ac3:por | aac:eng`,
    /// pedir a faixa 1 remove o único motivo de transcodificar, e o servidor
    /// devolve `direct_play`. Reabrir só a sessão manteria o selo dizendo
    /// `transcodificando` sobre um plano que deixou de existir — §18 por outro
    /// caminho.
    ///
    /// ⚠️ **A posição vem de fora**, em tempo de filme. A tela é quem sabe onde o
    /// filme está: aqui dentro só existe a posição da sessão, e ela vale zero
    /// assim que a sessão é trocada. Ver `tempoDeFilme`.
    ///
    /// ⚠️ **A sessão velha é encerrada**, e não abandonada. Cada uma é um ffmpeg
    /// vivo no `serious-server`; trocar de faixa três vezes deixaria três rodando
    /// pelo mesmo filme. É o mesmo cuidado do `onCleared`, e o mesmo motivo pelo
    /// qual o plano do mesmo arquivo já oscilou entre `direto` e
    /// `transcodificando` sem nada ter mudado do lado de cá.
    fun trocarFaixaDeAudio(indice: Int, posicaoDoFilmeMs: Long) {
        if (indice == _estado.value.faixaDeAudioEmUso) return
        encerrarSessaoAberta()
        _estado.update {
            it.copy(
                comecarEm = posicaoDoFilmeMs.coerceAtLeast(0),
                deslocamentoMs = 0L,
                url = null,
            )
        }
        preparar(paraCast = _estado.value.paraCast, faixaDeAudio = indice)
    }

    private fun encerrarSessaoAberta() {
        val id = sessaoAberta ?: return
        sessaoAberta = null
        viewModelScope.launch { runCatching { odeon.encerrarSessao(id) } }
    }

    private fun preparar(paraCast: Boolean, faixaDeAudio: Int? = null) {
        viewModelScope.launch {
            _estado.update { it.copy(preparando = true, erro = null, paraCast = paraCast) }
            try {
                val plano = odeon.plano(arquivoId, paraCast = paraCast, faixaDeAudio = faixaDeAudio)
                _estado.update {
                    it.copy(
                        legendas = legendasDe(plano),
                        faixasDeAudio = plano.faixasDeAudio,
                        /// O que o servidor **confirmou**, com a faixa pedida como
                        /// segunda opção: um plano antigo pode não trazer o campo,
                        /// e aí o que a tela pediu ainda é a melhor informação que
                        /// existe. Zero é o padrão dele, e está documentado.
                        faixaDeAudioEmUso = plano.faixaDeAudio ?: faixaDeAudio ?: 0,
                    )
                }

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
                        faixaDeAudio = faixaDeAudio,
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

    /// A reprodução morreu **depois** de ter começado.
    ///
    /// ## ⚠️ O buraco que isto fecha, e o dono descreveu em uma frase
    ///
    /// > «quando eu passo o filme pra frente clicando em um ponto avançado da
    /// > timeline o filme pausa mas para tudo de funcionar até eu voltar e
    /// > iniciar dnv»
    ///
    /// Medido em 06/08/2026, forçando um erro de fonte no meio de um filme:
    ///
    /// | | |
    /// |---|---|
    /// | o player | `ERROR(7)` |
    /// | a rede voltando sozinha | continua `ERROR` |
    /// | apertar o play | continua `ERROR`, posição congelada |
    /// | a tela | título, relógio e «faltam» **como se estivesse tocando** |
    ///
    /// Duas coisas se somavam. A primeira é do Media3 e não é defeito: depois de
    /// um `PlaybackException` o player fica ocioso, e **`play()` não faz nada** —
    /// só `prepare()` o levanta. A segunda é nossa: o `estado.erro` só cobria
    /// falha ao *montar* o plano, então tudo que quebrasse **depois** do
    /// `prepare` não tinha por onde aparecer. Não havia `Player.Listener` no app
    /// inteiro.
    ///
    /// O resultado é o §8b na forma mais cara dele: não é um clique que não faz
    /// nada, é a tela inteira que não faz nada, mentindo um relógio que anda.
    ///
    /// ## E a posição vem junto, pra o «tentar de novo» não voltar pro começo
    ///
    /// O `tentarDeNovo` refaz plano e sessão. Sem guardar onde o filme estava,
    /// ele voltaria pro ponto em que a sessão morta havia começado — que pode ser
    /// meia hora atrás. Aqui a posição do **filme** entra como novo `comecarEm`,
    /// e a sessão nova nasce onde a antiga parou.
    ///
    /// ⚠️ A sessão velha é encerrada antes: ela já não serve, e cada uma é um
    /// ffmpeg vivo no `serious-server`.
    fun falhouTocando(mensagem: String, posicaoDoFilmeMs: Long) {
        encerrarSessaoAberta()
        _estado.update {
            it.copy(
                erro = mensagem,
                url = null,
                preparando = false,
                comecarEm = posicaoDoFilmeMs.coerceAtLeast(0),
                deslocamentoMs = 0L,
            )
        }
    }

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
