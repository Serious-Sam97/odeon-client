package dev.odeon.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.glance.appwidget.updateAll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.odeon.android.R
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.odeon.android.OdeonApp
import dev.odeon.android.dados.RepositorioOdeon
import dev.odeon.android.ui.baixados.ModeloDosBaixados
import dev.odeon.android.ui.baixados.TelaDosBaixados
import dev.odeon.android.ui.biblioteca.ModeloDaBiblioteca
import dev.odeon.android.ui.biblioteca.TelaDaBiblioteca
import dev.odeon.android.ui.guia.ModeloDoGuia
import dev.odeon.android.ui.guia.TelaDoGuia
import dev.odeon.android.ui.locadora.ModeloDaLocadora
import dev.odeon.android.ui.locadora.TelaDaLocadora
import dev.odeon.android.ui.login.ModeloDeLogin
import dev.odeon.android.ui.login.TelaDeLogin
import dev.odeon.android.ui.mural.ModeloDoMural
import dev.odeon.android.ui.mural.TelaDoMural
import dev.odeon.android.ui.paravoce.ModeloParaVoce
import dev.odeon.android.ui.paravoce.TelaParaVoce
import dev.odeon.android.ui.perfil.GavetaDoEu
import dev.odeon.android.ui.perfil.ModeloDoPerfil
import dev.odeon.android.ui.perfil.TelaDoPerfil
import dev.odeon.android.ui.obra.ModeloDaObra
import dev.odeon.android.ui.obra.TelaDaObra
import dev.odeon.android.ui.player.ModeloDoPlayer
import dev.odeon.android.ui.player.TelaDoPlayer
import dev.odeon.android.widget.WidgetDeContinuar

/// Onde o app está.
///
/// ## Não há biblioteca de navegação, e é escolha
///
/// São **dois destinos**, e a transição entre eles é de mão única: entrou, não
/// volta pro login pelo botão voltar — voltar pra tela de login depois de entrar
/// é o tipo de "volta" que ninguém pede.
///
/// `navigation-compose` resolve pilha, argumento tipado e deep link. Nada disso
/// existe aqui hoje. Ele entra quando houver a terceira tela e uma pilha de
/// verdade — a ficha da obra, na fase 2 —, e aí entra resolvendo problema
/// medido.
/// ## E na fase 2 ele continuou não entrando — agora com motivo medido
///
/// A pilha de verdade chegou (biblioteca → ficha → player), e mesmo assim
/// `navigation-compose` ficou de fora. O que ele resolve, e o que este app tem:
///
/// | o que ele traz | aqui |
/// |---|---|
/// | pilha com estado | **dois níveis**, e o de baixo é sempre a biblioteca |
/// | argumento tipado por rota | três valores, passados como parâmetro de classe |
/// | deep link | não existe ainda — entra quando houver `odeon://obra/{id}` |
/// | transição entre rotas | não há nenhuma desenhada |
///
/// Ou seja: ele entra quando houver deep link, que é o primeiro item da lista
/// que **não** dá pra escrever à mão sem reimplementá-lo. Até lá, um `sealed` e
/// um `BackHandler` fazem o mesmo com menos peça.
private sealed interface Onde {
    data object Decidindo : Onde
    data object Login : Onde
    data class Ficha(val obraId: String) : Onde
    /// As quatro que são **aba**, e não tela empilhada.
    ///
    /// A distinção não é decorativa: quem está nesta lista aparece na barra de
    /// navegação e é alcançável de qualquer outra da lista com um toque. A ficha
    /// e o player não estão, e é o que os deixa serem tela cheia.
    data object Biblioteca : Onde
    data object Locadora : Onde
    data object Mural : Onde
    data object Guia : Onde
    data object ParaVoce : Onde

    /// ⚠️ **Baixados saiu da barra e continua existindo.**
    ///
    /// Ele nunca foi um lugar — é um **estado** do acervo («o que está no
    /// aparelho»), e a biblioteca é onde alguém procura um filme, baixado ou
    /// não. Virou um atalho no cabeçalho da biblioteca, e a tela é a mesma.
    ///
    /// A conta que forçou a decisão: com mural e guia entrando seriam seis abas,
    /// e a seis cada uma fica com 68,5dp — «biblioteca» ocupa 61dp a 12sp, ou
    /// seja **não cabe** com o respiro. Medido em 04/08/2026.
    data object Baixados : Onde

    /// O perfil, e ele **não é aba** pelo mesmo motivo que a ficha não é: chega-se
    /// nele pela gaveta do canto, que já está em toda aba. Uma sexta entrada na
    /// barra pra o que cabe num toque no próprio rosto seria gastar um dos cinco
    /// lugares permanentes com o destino menos visitado do app.
    data object Perfil : Onde

    data class Assistindo(
        /// A obra **e** o arquivo. O player toca o arquivo, mas quem recebe a
        /// marca de "onde eu parei" é a obra — `POST /api/works/{obra}/progress`.
        /// Sem o id da obra aqui, o player teria que reperguntar a ficha só pra
        /// saber a quem pertence o que está tocando.
        val obraId: String,
        val arquivoId: String,
        val titulo: String,
        val ondeParou: Double,
        /// A capa, pra o controle de mídia — R9. `null` é normal: 8.598 obras
        /// não têm pôster, e a notificação some com a arte em vez de desenhar um
        /// quadrado vazio.
        val capaUrl: String?,
        /// A duração **de verdade**, vinda do probe do arquivo.
        ///
        /// Ela viaja daqui porque o player não pode perguntá-la a si mesmo
        /// quando a fonte é HLS em transcodificação: ali `Player.duration`
        /// devolve só o que já foi gerado. Ver `ModeloDoPlayer`.
        val duracaoEmSegundos: Double?,
    ) : Onde
}

/// As quatro abas, na ordem em que aparecem na barra.
///
/// ## De onde elas saíram
///
/// Até aqui os destinos eram **três links no cabeçalho da biblioteca**
/// (`locadora ›`, `baixados ›`, `para você ›`), e o comentário que os
/// acompanhava argumentava que uma barra de abas seria desperdício porque «o app
/// tem um destino além da biblioteca». Estava certo quando foi escrito, na fase
/// 5 — e deixou de estar duas fases depois, sem que ninguém voltasse pra corrigir
/// o texto. São quatro destinos agora, que é exatamente a faixa em que o
/// Material põe barra de navegação (de 3 a 5).
///
/// O custo do arranjo antigo não era o cabeçalho: era que os três destinos só
/// existiam **de dentro da biblioteca**. Sair dos baixados pra locadora passava
/// pela biblioteca no meio, sem motivo nenhum além de ser lá que os links
/// moravam.
///
/// A ordem é a de uso esperado, e não alfabética: chega-se ao app pra ver o
/// acervo, e "para você" fica no fim porque é onde se vai quando não se sabe o
/// que assistir — que é a pergunta feita depois de olhar, não antes.
private enum class Aba(val rotulo: String, val icone: Int, val destino: Onde) {
    Biblioteca("biblioteca", R.drawable.ic_aba_biblioteca, Onde.Biblioteca),
    Locadora("locadora", R.drawable.ic_aba_locadora, Onde.Locadora),
    Mural("mural", R.drawable.ic_aba_mural, Onde.Mural),
    Guia("guia", R.drawable.ic_aba_guia, Onde.Guia),
    ParaVoce("para você", R.drawable.ic_aba_paravoce, Onde.ParaVoce),
}

/// O destino de um nome de aba vindo de um atalho — ou `null` se não houver.
///
/// Comparação sem diferenciar maiúsculas porque o nome vem de um XML escrito à
/// mão (`res/xml/atalhos.xml`), e um `Locadora` com maiúscula lá viraria um
/// atalho que não faz nada — em silêncio, que é o §8b.
private fun abaDe(nome: String?): Onde? =
    nome?.let { pedido -> Aba.entries.firstOrNull { it.name.equals(pedido, true) }?.destino }

/// Em que aba este lugar está — ou `null`, se ele não é aba nenhuma.
///
/// O `null` é o que decide se a barra aparece. Login, ficha e player devolvem
/// `null` e por isso são desenhados fora do esqueleto: os dois últimos são tela
/// cheia, e uma barra de abas por cima de um filme é o oposto do que se quer.
private val Onde.aba: Aba?
    get() = Aba.entries.firstOrNull { it.destino == this }

/// O `@OptIn` aqui é pelo mesmo motivo do `TelaDoPlayer`: a tela dos baixados
/// segura o `DownloadManager` do Media3, que fica **abaixo** da fronteira que o
/// Media3 chama de estável. É opt-in de montagem, não de uso da UI.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppOdeon(abaPedida: androidx.compose.runtime.MutableState<String?>? = null) {
    val app = LocalContext.current.applicationContext as OdeonApp
    var onde: Onde by remember { mutableStateOf(Onde.Decidindo) }

    /// O "eu" mora **aqui**, e não dentro da gaveta que o desenha.
    ///
    /// A insígnia aparece por cima de toda aba e a tela do perfil lê a mesma
    /// resposta; criando o modelo lá dentro, cada troca de aba redesenharia a
    /// gaveta e o `viewModel()` teria que reencontrar a instância pelo tipo.
    /// Aqui em cima ele é um só, explicitamente, e é o mesmo que a tela recebe.
    val eu: ModeloDoPerfil = viewModel(factory = fabricaDoPerfil(app.odeon))
    val estadoDoEu by eu.estado.collectAsStateWithLifecycle()
    val saiu by eu.saiu.collectAsStateWithLifecycle()

    /// Sair volta pro login, e **não** pro "decidindo".
    ///
    /// O `Decidindo` pergunta ao cofre se há sessão guardada — e acabou de não
    /// haver, porque foi isto que o `sair` fez. Passar por ele seria um piscar
    /// de tela pra chegar na mesma resposta.
    LaunchedEffect(saiu) {
        if (saiu) {
            onde = Onde.Login
            eu.jaSaiu()
        }
    }

    /// O perfil é buscado quando se **chega numa aba** — ou seja, depois de
    /// haver sessão. Pedir antes daria 401, e o §53 vale aqui: não perguntar o
    /// que se sabe que vai ser negado.
    LaunchedEffect(onde) {
        if (onde.aba != null) eu.carregarSePreciso()
    }

    /// O barramento, ligado assim que há sessão e token.
    ///
    /// ## Ele é ligado **daqui**, e não do `Application`
    ///
    /// Porque só aqui se sabe que há sessão: o `OdeonApp` nasce antes do login, e
    /// conectar sem token seria abrir uma conexão pra tomar 401. O `ligar` é
    /// idempotente — chamar de novo a cada troca de aba não abre uma segunda.
    ///
    /// ⚠️ O escopo é o da composição, e é de propósito: quando o app sai da
    /// frente, a corrotina é cancelada e a conexão fecha. Um SSE vivo em segundo
    /// plano é o servidor de casa segurando uma conexão pra ninguém.
    val progressoDeFora = remember { mutableIntStateOf(0) }

    /// Há uma sobreposição de tela cheia dentro de uma aba? Ver o parâmetro
    /// `aoMudarSobreposicao` da `TelaDaLocadora`.
    var sobreposicaoCheia by remember { mutableStateOf(false) }
    LaunchedEffect(onde) {
        if (onde.aba == null) return@LaunchedEffect
        val base = app.odeon.base ?: return@LaunchedEffect
        app.barramento.ligar(this, base)
    }
    LaunchedEffect(Unit) {
        app.barramento.eventos.collect { evento ->
            /// ## O que o app faz com cada um, hoje
            ///
            /// `progress` de **outro aparelho** relê a fileira de continuar e a
            /// ficha aberta — é a sincronia que a §5 da espec chama de «você
            /// parou na TV e continua no ônibus», e era o único buraco que o
            /// barramento custava com as telas que existem.
            ///
            /// Os outros seguem pra quem os ouve: a locadora tem o próprio
            /// coletor, e mural, junto e ao vivo ainda não têm tela.
            if (evento is dev.odeon.android.dados.EventoDoServidor.Progresso) {
                progressoDeFora.intValue++
            }
        }
    }

    /// O filtro que o guia pediu, esperando a biblioteca.
    ///
    /// ## Por que ele mora aqui, e não é parâmetro de tela
    ///
    /// Quem toca é o guia; quem filtra é a biblioteca; e as duas são abas
    /// irmãs, sem uma pilha entre elas. O `AppOdeon` é o único lugar que
    /// enxerga as duas — é o mesmo papel que ele já faz com `voltasDoPlayer`.
    ///
    /// ⚠️ **Consumir é zerar.** Sem o `= null` depois de aplicar, qualquer
    /// recomposição reaplicaria o filtro e a biblioteca ficaria presa em
    /// «Terror» — o mesmo defeito que o atalho de aba teve e que está descrito
    /// mais abaixo.
    var filtroPedido: dev.odeon.android.dados.Filtros? by remember { mutableStateOf(null) }

    /// Quantas vezes se voltou do player. Serve de sinal, não de contagem: o que
    /// importa é que o número **mudou**, pra a ficha reler o `position_seconds`.
    var voltasDoPlayer by remember { mutableIntStateOf(0) }

    /// O arranque: havia servidor e sessão guardados?
    ///
    /// Enquanto isso não se resolve, a tela fica no `Decidindo`. Mostrar o login
    /// por um instante e depois trocar pra biblioteca seria piscar uma pergunta
    /// já respondida.
    LaunchedEffect(Unit) {
        onde = if (app.odeon.retomar()) {
            /// O atalho da R9 escolhe a aba de chegada.
            ///
            /// ⚠️ Só **se já houver sessão**. Um atalho de "baixados" que caísse
            /// direto na tela de login pareceria que o app esqueceu o que ele
            /// mesmo ofereceu — e o §53 vale aqui do lado de fora: não oferecer
            /// o que a validação vai negar.
            abaDe(abaPedida?.value).also { abaPedida?.value = null } ?: Onde.Biblioteca
        } else {
            Onde.Login
        }
    }

    /// O corpo, separado do esqueleto que o envolve.
    ///
    /// Ele é lambda e não `when` solto porque as duas montagens abaixo — com
    /// barra e sem barra — precisam desenhar exatamente o mesmo conteúdo. Com o
    /// `when` escrito duas vezes, um destino novo entraria num lugar e não no
    /// outro, e o sintoma seria uma tela em branco em vez de um erro de
    /// compilação.
    /// ## Apagar a luz — leva 3 do segundo redesenho
    ///
    /// Tocar em assistir **não troca de tela**: a sala escurece primeiro. É a
    /// R5 da web, e o comentário da folha é a especificação inteira:
    ///
    /// > «Clicar em tocar não deve trocar de tela: deve APAGAR A LUZ. O fundo
    /// > fecha primeiro, o quadro cresce um tico, e o cromo entra depois — nessa
    /// > ordem, e não junto, porque é a ordem em que uma sala de cinema
    /// > escurece.»
    ///
    /// Aqui são dois tempos, não três: o fundo fecha (240ms), e o player entra
    /// já com os controles visíveis, que é o terceiro tempo dele. O segundo — o
    /// quadro crescendo — é o elemento compartilhado da R7, que já leva o pôster
    /// da grade até a ficha; ele **não** alcança o player, e o motivo está
    /// escrito logo abaixo: o player fica fora do `AnimatedContent` de propósito,
    /// porque animar a superfície de vídeo é como o PiP perde o quadro.
    ///
    /// ⚠️ **O destino fica guardado, e não é navegado antes.** Se o `onde`
    /// mudasse junto com o escurecer, o player montaria por trás do véu e o
    /// primeiro quadro do filme sairia enquanto a tela ainda está preta — que é
    /// pular o começo, não escurecer a sala.
    var indoAssistir: Onde.Assistindo? by remember { mutableStateOf(null) }
    val luz by animateFloatAsState(
        targetValue = if (indoAssistir != null) 1f else 0f,
        animationSpec = tween(240),
        label = "apagar a luz",
    )
    LaunchedEffect(indoAssistir, luz) {
        val destino = indoAssistir
        if (destino != null && luz >= 1f) {
            onde = destino
            indoAssistir = null
        }
    }

    /// O widget é avisado ao voltar do player — R9.
    ///
    /// ## Sem isto ele mente por até 30 minutos
    ///
    /// O launcher repede o conteúdo a cada `updatePeriodMillis`, e 30 minutos é
    /// o **mínimo** que o Android respeita — pedir menos não diminui. Ou seja:
    /// quem assiste meia hora de um filme, sai, e olha a tela inicial, vê a
    /// posição velha. Num widget cujo assunto inteiro é "onde eu parei", isso é
    /// o §18: ele afirma um progresso que não é mais verdade.
    ///
    /// `voltasDoPlayer` já existia como sinal — a ficha e a fileira de continuar
    /// se releem por ele. O widget entra na mesma carona, e é a razão de ele
    /// morar aqui e não no `TelaDoPlayer`: este é o lugar que já sabe que se
    /// **voltou**, e voltar é quando o progresso terminou de ser gravado.
    ///
    /// `updateAll` é `suspend` e varre todas as instâncias — quem tiver dois
    /// widgets na tela recebe os dois atualizados. Falhar não pode derrubar a
    /// volta do player, então vai dentro de `runCatching`: um widget
    /// desatualizado é chato; um app que fecha ao sair do filme, não.
    val contexto = LocalContext.current
    LaunchedEffect(voltasDoPlayer) {
        if (voltasDoPlayer > 0) {
            runCatching { WidgetDeContinuar().updateAll(contexto) }
        }
    }

    /// O atalho pedido **depois** de o app já estar aberto — R9.
    ///
    /// O `onCreate` cobre o caso de abrir do zero; este cobre o de tocar no
    /// atalho com o app em segundo plano, que é o caso comum depois do primeiro
    /// uso do dia. O `AtividadePrincipal.onNewIntent` escreve no estado, e isto
    /// reage.
    ///
    /// ⚠️ Consumir é zerar. Sem o `= null` depois de aplicar, a aba pedida
    /// continuaria valendo e qualquer recomposição jogaria a pessoa de volta pra
    /// ela — o app trancaria numa aba, e o sintoma seria "não consigo sair dos
    /// baixados".
    LaunchedEffect(abaPedida?.value) {
        val destino = abaDe(abaPedida?.value)
        if (destino != null && onde !is Onde.Assistindo) {
            onde = destino
            abaPedida?.value = null
        }
    }

    /// O corpo recebe a **moldura** do pôster como parâmetro, e não a lê de uma
    /// variável de fora.
    ///
    /// A moldura é o elemento compartilhado da R7, e ela só pode ser construída
    /// lá dentro do `AnimatedContent`, que é o único lugar onde os dois escopos
    /// — o do `SharedTransitionLayout` e o da transição em curso — existem ao
    /// mesmo tempo. Guardá-la num `var` de fora e atribuí-la durante a
    /// composição seria escrever estado no meio do desenho, que é a receita da
    /// recomposição infinita.
    val corpo: @Composable (MolduraDoCartaz) -> Unit = { molduraDoCartaz ->
        when (onde) {
                Onde.Decidindo -> Box(
                    Modifier.fillMaxSize().safeDrawingPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Cores.destaque)
                }

                Onde.Login -> {
                    val modelo: ModeloDeLogin = viewModel(factory = fabrica(app.odeon))
                    val entrou by modelo.entrou.collectAsStateWithLifecycle()
                    LaunchedEffect(entrou) { if (entrou) onde = Onde.Biblioteca }
                    TelaDeLogin(modelo)
                }

                Onde.Biblioteca -> {
                    val modelo: ModeloDaBiblioteca = viewModel(factory = fabrica(app.odeon))
                    /// O filtro que veio do guia.
                    LaunchedEffect(filtroPedido) {
                        filtroPedido?.let {
                            modelo.mudouFiltros(it)
                            filtroPedido = null
                        }
                    }
                    /// A fileira de "continuar" relê ao voltar do player, pelo
                    /// mesmo motivo da ficha: o `ViewModel` fica em cache, e sem
                    /// isto quem acabou de assistir volta pra uma fileira que
                    /// ainda não sabe disso.
                    ///
                    /// **Só a fileira**, e não a grade: o acervo tem 8.316
                    /// entradas paginadas e não muda porque alguém assistiu.
                    /// Recarregar tudo jogaria fora a rolagem por nada.
                    /// Relê ao voltar do player **e** quando outro aparelho
                    /// mexeu no progresso — as duas coisas dizem o mesmo: a
                    /// fileira de continuar está velha.
                    LaunchedEffect(voltasDoPlayer, progressoDeFora.intValue) {
                        if (voltasDoPlayer > 0 || progressoDeFora.intValue > 0) {
                            modelo.recarregarParaContinuar()
                        }
                    }
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        TelaDaBiblioteca(
                            modelo,
                            aoAbrirObra = { onde = Onde.Ficha(it) },
                            aoAbrirBaixados = { onde = Onde.Baixados },
                            moldura = molduraDoCartaz,
                        )
                    }
                }

                Onde.Locadora -> {
                    val modelo: ModeloDaLocadora = viewModel(factory = fabricaDaLocadora(app.odeon, app.barramento))
                    BackHandler { onde = Onde.Biblioteca }
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        TelaDaLocadora(
                            modelo = modelo,
                            aoAbrirObra = { onde = Onde.Ficha(it) },
                            /// O menu do disco toca **direto**, sem passar pela
                            /// ficha: escolher «do começo» ou um capítulo já é a
                            /// decisão que a ficha existiria pra ajudar a tomar.
                            aoMudarSobreposicao = { sobreposicaoCheia = it },
                            aoTocar = { obraId, arquivoId, titulo, de, duracao ->
                                indoAssistir = Onde.Assistindo(
                                    obraId = obraId,
                                    arquivoId = arquivoId,
                                    titulo = titulo,
                                    ondeParou = de,
                                    capaUrl = null,
                                    duracaoEmSegundos = duracao,
                                )
                            },
                        )
                    }
                }

                Onde.Baixados -> {
                    val modelo: ModeloDosBaixados =
                        viewModel(factory = fabricaDosBaixados(app))
                    BackHandler { onde = Onde.Biblioteca }
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        TelaDosBaixados(modelo = modelo)
                    }
                }

                Onde.Perfil -> {
                    BackHandler { onde = Onde.Biblioteca }
                    /// Sem `safeDrawingPadding`, e pelo mesmo motivo da ficha: a
                    /// capa é borda a borda e quem respeita as áreas seguras é o
                    /// conteúdo, lá dentro.
                    Box(Modifier.fillMaxSize()) {
                        TelaDoPerfil(
                            modelo = eu,
                            aoVoltar = { onde = Onde.Biblioteca },
                            aoAbrirObra = { onde = Onde.Ficha(it) },
                        )
                    }
                }

                Onde.Mural -> {
                    val modelo: ModeloDoMural = viewModel(factory = fabricaDoMural(app.odeon))
                    BackHandler { onde = Onde.Biblioteca }
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        TelaDoMural(modelo = modelo, aoAbrirObra = { onde = Onde.Ficha(it) })
                    }
                }

                Onde.Guia -> {
                    val modelo: ModeloDoGuia = viewModel(factory = fabricaDoGuia(app.odeon))
                    BackHandler { onde = Onde.Biblioteca }
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        TelaDoGuia(
                            modelo = modelo,
                            aoAbrirObra = { onde = Onde.Ficha(it) },
                            /// O eixo leva à biblioteca **já filtrada**. A troca
                            /// de aba é parte do gesto: quem toca em «Terror»
                            /// está pedindo pra ver os filmes, e vê-los é na
                            /// biblioteca.
                            aoFiltrar = {
                                filtroPedido = it
                                onde = Onde.Biblioteca
                            },
                        )
                    }
                }

                Onde.ParaVoce -> {
                    val modelo: ModeloParaVoce = viewModel(factory = fabricaParaVoce(app.odeon))
                    BackHandler { onde = Onde.Biblioteca }
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        TelaParaVoce(
                            modelo = modelo,
                            aoAbrirObra = { onde = Onde.Ficha(it) },
                        )
                    }
                }

                is Onde.Ficha -> {
                    val alvo = onde as Onde.Ficha
                    /// A `key` é o que faz trocar de obra construir um modelo
                    /// novo. Sem ela, abrir a segunda ficha reaproveitaria o
                    /// `ViewModel` da primeira e mostraria o filme errado — e
                    /// mostraria com cara de certo, que é o pior jeito.
                    val modelo: ModeloDaObra = viewModel(
                        key = "ficha:${alvo.obraId}",
                        factory = fabricaDaObra(app.odeon, alvo.obraId),
                    )
                    /// Voltar do player relê a ficha. Ver `relerSeJaTem`.
                    LaunchedEffect(voltasDoPlayer, progressoDeFora.intValue) {
                        if (voltasDoPlayer > 0 || progressoDeFora.intValue > 0) {
                            modelo.relerSeJaTem()
                        }
                    }
                    BackHandler { onde = Onde.Biblioteca }
                    /// **Sem `safeDrawingPadding` aqui, e é a R8.**
                    ///
                    /// A ficha é borda a borda: o backdrop sobe até debaixo da
                    /// barra de status. Com o padding neste `Box`, a arte
                    /// começaria ~60dp abaixo do topo e a faixa de cima viraria
                    /// uma tarja preta — que é o oposto do item.
                    ///
                    /// Quem respeita as áreas seguras agora é a **própria tela**,
                    /// e só no conteúdo: `windowInsetsPadding` no `Column` de
                    /// dentro, backdrop de fora. É o mesmo arranjo do player, que
                    /// já dispensava o padding pelo mesmo motivo.
                    Box(Modifier.fillMaxSize()) {
                        TelaDaObra(
                            modelo = modelo,
                            moldura = molduraDoCartaz,
                            aoVoltar = { onde = Onde.Biblioteca },
                            aoBaixar = { arquivoId -> app.baixarArquivo(arquivoId, alvo.obraId) },
                            aoTocar = { arquivoId, titulo, ondeParou, duracao, capa ->
                                indoAssistir = Onde.Assistindo(
                                    obraId = alvo.obraId,
                                    arquivoId = arquivoId,
                                    titulo = titulo,
                                    ondeParou = ondeParou,
                                    capaUrl = capa,
                                    duracaoEmSegundos = duracao,
                                )
                            },
                        )
                    }
                }

                is Onde.Assistindo -> {
                    val alvo = onde as Onde.Assistindo
                    /// Volta pra **ficha**, não pra biblioteca.
                    ///
                    /// A primeira versão mandava pra biblioteca, e o efeito só
                    /// apareceu usando: sai-se do filme e perde-se a tela da
                    /// obra, com a sinopse, as versões e o botão que agora diz
                    /// "continuar". Quem fecha um filme quase sempre quer
                    /// exatamente aquela tela — foi de lá que veio.
                    val voltarPraFicha = {
                        voltasDoPlayer++
                        onde = Onde.Ficha(alvo.obraId)
                    }
                    val modelo: ModeloDoPlayer = viewModel(
                        key = "player:${alvo.arquivoId}",
                        factory = fabricaDoPlayer(app.odeon, alvo, app.barramento),
                    )
                    BackHandler(onBack = voltarPraFicha)
                    /// **Sem `safeDrawingPadding` aqui**, e é de propósito: o
                    /// vídeo usa a tela inteira, entalhe e barras incluídos.
                    /// Respeitar as áreas seguras num player é desenhar duas
                    /// tarjas pretas em volta de uma imagem que já é preta.
                    TelaDoPlayer(modelo = modelo, aoVoltar = voltarPraFicha)
                }
        }
    }

    TemaOdeon {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            /// A barra só existe onde há aba.
            ///
            /// Login, ficha e player devolvem `null` no `.aba` e caem no ramo de
            /// baixo, sem esqueleto nenhum. É o que faz o player ser tela cheia
            /// de verdade e não tela cheia com 80dp de abas por cima.
            /// ## O player fica **fora** da transição, e é decisão
            ///
            /// Ele desenha vídeo num `SurfaceView` de verdade, dentro de um
            /// `AndroidView`. Pôr isso dentro de um `AnimatedContent` faria a
            /// superfície ser criada e destruída junto com a animação de
            /// entrada — e o sintoma disso é um piscão preto no começo do filme,
            /// ou pior, o PiP perdendo a superfície na hora de encolher.
            ///
            /// Nada se ganharia em troca: o player entra em tela cheia vindo de
            /// um botão, e não há elemento compartilhado entre a ficha e um
            /// vídeo.
            if (onde is Onde.Assistindo) {
                corpo(MolduraDoCartaz.Nenhuma)
                return@Surface
            }

            SharedTransitionLayout {
                /// A transição da grade pra ficha — R7.
                ///
                /// ## O que ela responde
                ///
                /// «De onde essa tela veio». O pôster tocado **cresce e vira** o
                /// pôster da ficha, em vez de a ficha aparecer por cima. É a
                /// regra 5 do redesenho — movimento tem que significar — e é a
                /// coisa que mais separa um app nativo de uma página.
                ///
                /// ## O `contentKey` é o que evita a animação boba
                ///
                /// Sem ele, `AnimatedContent` compara os estados inteiros: abrir
                /// a ficha da obra A e depois a da obra B seria uma transição, e
                /// as duas fichas fariam cross-fade uma na outra. Com a chave
                /// reduzida ao **tipo** de tela, trocar de obra dentro da ficha
                /// não anima — e trocar de aba também não, porque as quatro abas
                /// já têm a barra pra dizer o que mudou.
                AnimatedContent(
                    targetState = onde,
                    contentKey = { alvo ->
                        when (alvo) {
                            is Onde.Ficha -> "ficha"
                            else -> "abas"
                        }
                    },
                    label = "tela",
                ) { alvo ->
                    /// ⚠️ O conteúdo lê `alvo`, e não `onde`.
                    ///
                    /// Durante a transição os dois existem ao mesmo tempo, e é
                    /// justamente isso que faz a animação. Ler `onde` aqui
                    /// desenharia as duas telas com o estado **novo** — a de
                    /// saída viraria a de entrada antes de sair, e não haveria
                    /// pôster de origem pra crescer.
                    /// A moldura, construída aqui porque é aqui que os dois
                    /// escopos existem. A chave é o id da obra: é ela que faz o
                    /// Compose entender que o cartaz da grade e o pôster da
                    /// ficha são **a mesma coisa em dois lugares**.
                    val moldura = MolduraDoCartaz { id ->
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = "cartaz-$id"),
                            animatedVisibilityScope = this@AnimatedContent,
                        )
                    }

                    /// Uma sobreposição de tela cheia dispensa o esqueleto — o
                    /// mesmo caminho que o player já toma.
                    val abaAtual = if (sobreposicaoCheia) null else alvo.aba
                    if (abaAtual != null) {
                        EsqueletoComAbas(
                            atual = abaAtual,
                            aoTrocar = { onde = it.destino },
                            conteudo = { corpo(moldura) },
                            /// A gaveta do canto — o único pedaço de cromo que
                            /// existe em toda aba. Ela fica **fora** do
                            /// `conteudo` porque as telas rolam e ela não:
                            /// dentro, o rosto sumiria na primeira rolada, e um
                            /// `sair` que só existe no topo da biblioteca é um
                            /// `sair` que ninguém acha.
                            gaveta = {
                                GavetaDoEu(
                                    nome = estadoDoEu.perfil?.nome.orEmpty(),
                                    perfil = estadoDoEu.perfil,
                                    rosto = eu.arte(estadoDoEu.perfil?.avatar?.arte),
                                    aoAbrirPerfil = { onde = Onde.Perfil },
                                    aoSair = { eu.sair() },
                                )
                            },
                        )
                    } else {
                        corpo(moldura)
                    }
                }
            }

            /// O véu. Ele fica **por cima de tudo** e não intercepta toque: a
            /// tela já está a caminho do player, e um segundo toque no meio do
            /// escurecer não deveria fazer nada — mas também não deveria ser
            /// engolido por um retângulo invisível quando a luz está em zero.
            if (luz > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = luz }
                        .background(Color.Black),
                )
            }
        }
    }
}

/// A barra que muda de forma conforme a tela.
///
/// ## ⚠️ O `NavigationSuiteScaffold` saiu, e o motivo é o facho
///
/// Ele resolvia bem duas coisas — as duas formas (barra e trilho) e os encaixes
/// de inset —, e o comentário no `libs.versions.toml` defendia isso. O que ele
/// **não** deixa fazer é desenhar **atrás** dos itens, e o facho é exatamente
/// isso: uma luz que nasce fora da barra e a atravessa.
///
/// O que ficou dele: a decisão de forma pelo `WindowSizeClass`, que continua
/// sendo dele — `currentWindowAdaptiveInfo` ainda é quem responde se a altura
/// está espremida.
///
/// E saiu um defeito junto. A cápsula do item selecionado era pintada com
/// `secondaryContainer`, que **nunca foi definido** no `EsquemaEscuro`: ela caía
/// no lilás de fábrica do Material 3, `#4A4458`. O menu inferior era a única
/// peça do app pintada por outra pessoa.
///
/// ## Em paisagem continua trilho, e ele não leva facho
///
/// Um facho horizontal saindo da lateral não é uma janela de projeção: é uma
/// luz vindo da parede. A metáfora só funciona de baixo pra cima, então o
/// trilho fica com a gramática do `Luz.kt` — quente no escolhido, filamento
/// frio nos outros — sem o cone.
@Composable
private fun EsqueletoComAbas(
    atual: Aba,
    aoTrocar: (Aba) -> Unit,
    conteudo: @Composable () -> Unit,
    /// A gaveta do "eu", desenhada por cima do conteúdo e alinhada ao canto de
    /// cima à direita — nas duas formas, barra e trilho. Em paisagem o trilho
    /// come a esquerda, e o canto direito continua livre; é o mesmo lugar.
    gaveta: @Composable () -> Unit = {},
) {
    val info = currentWindowAdaptiveInfo()
    val alturaEspremida = !info.windowSizeClass
        .isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    /// O conteúdo e a gaveta, empilhados.
    ///
    /// ⚠️ A gaveta leva os **insets** por conta própria: o conteúdo de cada aba
    /// já aplica o `safeDrawingPadding`, mas este `Box` não, e sem isso o rosto
    /// nasceria debaixo do relógio do sistema.
    val corpoComGaveta: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            conteudo()
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.End,
                        ),
                    ),
            ) {
                gaveta()
            }
        }
    }

    if (alturaEspremida) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = Cores.fundo,
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Start + WindowInsetsSides.Vertical,
                    ),
                ),
            ) {
                Aba.entries.forEach { aba ->
                    NavigationRailItem(
                        selected = aba == atual,
                        onClick = { if (aba != atual) aoTrocar(aba) },
                        icon = {
                            Icon(painterResource(aba.icone), contentDescription = null)
                        },
                        label = { Text(aba.rotulo, style = Tipo.pilula, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Cores.destaqueQuente,
                            selectedTextColor = Cores.destaqueQuente,
                            unselectedIconColor = Cores.destaqueApagado,
                            unselectedTextColor = Cores.textoApagado,
                            /// Transparente, e é o conserto do lilás: sem isto o
                            /// trilho volta a pintar a cápsula com o
                            /// `secondaryContainer` de fábrica.
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
            Box(Modifier.weight(1f)) { corpoComGaveta() }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { corpoComGaveta() }
        BarraDoFacho(
            destinos = Aba.entries.map { aba ->
                DestinoDoFacho(
                    rotulo = aba.rotulo,
                    icone = painterResource(aba.icone),
                    selecionado = aba == atual,
                    aoTocar = { if (aba != atual) aoTrocar(aba) },
                )
            },
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
            ),
        )
    }
}

/// A fábrica dos dois modelos.
///
/// Escrita à mão porque o grafo tem **um nó** — o repositório. Um framework de
/// injeção aqui seria configuração para resolver o que um parâmetro resolve.
private fun fabrica(odeon: RepositorioOdeon) = viewModelFactory {
    initializer { ModeloDeLogin(odeon) }
    initializer { ModeloDaBiblioteca(odeon) }
}

/// As duas da fase 2 vão em fábricas próprias porque **levam argumento**.
///
/// O `viewModelFactory` acima resolve por tipo, e resolver por tipo não comporta
/// "o modelo da obra 3f2a" e "o modelo da obra 91cc" ao mesmo tempo. Com o id
/// fechado na fábrica, cada `key` do `viewModel(...)` recebe a sua.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun fabricaDosBaixados(app: OdeonApp) = viewModelFactory {
    initializer { ModeloDosBaixados(app.baixados, app.cofre) }
}

private fun fabricaParaVoce(odeon: RepositorioOdeon) = viewModelFactory {
    initializer { ModeloParaVoce(odeon) }
}

private fun fabricaDoPerfil(odeon: RepositorioOdeon) = viewModelFactory {
    initializer { ModeloDoPerfil(odeon) }
}

private fun fabricaDoMural(odeon: RepositorioOdeon) = viewModelFactory {
    initializer { ModeloDoMural(odeon) }
}

private fun fabricaDoGuia(odeon: RepositorioOdeon) = viewModelFactory {
    initializer { ModeloDoGuia(odeon) }
}

private fun fabricaDaLocadora(
    odeon: RepositorioOdeon,
    barramento: dev.odeon.android.dados.Barramento,
) = viewModelFactory {
    initializer { ModeloDaLocadora(odeon, barramento) }
}

private fun fabricaDaObra(odeon: RepositorioOdeon, obraId: String) = viewModelFactory {
    initializer { ModeloDaObra(odeon, obraId) }
}

private fun fabricaDoPlayer(
    odeon: RepositorioOdeon,
    alvo: Onde.Assistindo,
    barramento: dev.odeon.android.dados.Barramento,
) = viewModelFactory {
    initializer {
        ModeloDoPlayer(
            odeon = odeon,
            obraId = alvo.obraId,
            arquivoId = alvo.arquivoId,
            titulo = alvo.titulo,
            ondeParou = alvo.ondeParou,
            duracaoEmSegundos = alvo.duracaoEmSegundos,
            capaUrl = alvo.capaUrl,
            barramento = barramento,
        )
    }
}
