package dev.odeon.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import dev.odeon.android.ui.locadora.ModeloDaLocadora
import dev.odeon.android.ui.locadora.TelaDaLocadora
import dev.odeon.android.ui.login.ModeloDeLogin
import dev.odeon.android.ui.login.TelaDeLogin
import dev.odeon.android.ui.paravoce.ModeloParaVoce
import dev.odeon.android.ui.paravoce.TelaParaVoce
import dev.odeon.android.ui.obra.ModeloDaObra
import dev.odeon.android.ui.obra.TelaDaObra
import dev.odeon.android.ui.player.ModeloDoPlayer
import dev.odeon.android.ui.player.TelaDoPlayer

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
    data object Baixados : Onde
    data object ParaVoce : Onde

    data class Assistindo(
        /// A obra **e** o arquivo. O player toca o arquivo, mas quem recebe a
        /// marca de "onde eu parei" é a obra — `POST /api/works/{obra}/progress`.
        /// Sem o id da obra aqui, o player teria que reperguntar a ficha só pra
        /// saber a quem pertence o que está tocando.
        val obraId: String,
        val arquivoId: String,
        val titulo: String,
        val ondeParou: Double,
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
    Baixados("baixados", R.drawable.ic_aba_baixados, Onde.Baixados),
    ParaVoce("para você", R.drawable.ic_aba_paravoce, Onde.ParaVoce),
}

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
@Composable
fun AppOdeon() {
    val app = LocalContext.current.applicationContext as OdeonApp
    var onde: Onde by remember { mutableStateOf(Onde.Decidindo) }

    /// Quantas vezes se voltou do player. Serve de sinal, não de contagem: o que
    /// importa é que o número **mudou**, pra a ficha reler o `position_seconds`.
    var voltasDoPlayer by remember { mutableIntStateOf(0) }

    /// O arranque: havia servidor e sessão guardados?
    ///
    /// Enquanto isso não se resolve, a tela fica no `Decidindo`. Mostrar o login
    /// por um instante e depois trocar pra biblioteca seria piscar uma pergunta
    /// já respondida.
    LaunchedEffect(Unit) {
        onde = if (app.odeon.retomar()) Onde.Biblioteca else Onde.Login
    }

    /// O corpo, separado do esqueleto que o envolve.
    ///
    /// Ele é lambda e não `when` solto porque as duas montagens abaixo — com
    /// barra e sem barra — precisam desenhar exatamente o mesmo conteúdo. Com o
    /// `when` escrito duas vezes, um destino novo entraria num lugar e não no
    /// outro, e o sintoma seria uma tela em branco em vez de um erro de
    /// compilação.
    val corpo: @Composable () -> Unit = {
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
                    /// A fileira de "continuar" relê ao voltar do player, pelo
                    /// mesmo motivo da ficha: o `ViewModel` fica em cache, e sem
                    /// isto quem acabou de assistir volta pra uma fileira que
                    /// ainda não sabe disso.
                    ///
                    /// **Só a fileira**, e não a grade: o acervo tem 8.316
                    /// entradas paginadas e não muda porque alguém assistiu.
                    /// Recarregar tudo jogaria fora a rolagem por nada.
                    LaunchedEffect(voltasDoPlayer) {
                        if (voltasDoPlayer > 0) modelo.recarregarParaContinuar()
                    }
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        TelaDaBiblioteca(
                            modelo,
                            aoAbrirObra = { onde = Onde.Ficha(it) },
                        )
                    }
                }

                Onde.Locadora -> {
                    val modelo: ModeloDaLocadora = viewModel(factory = fabricaDaLocadora(app.odeon))
                    BackHandler { onde = Onde.Biblioteca }
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        TelaDaLocadora(modelo = modelo)
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
                    LaunchedEffect(voltasDoPlayer) {
                        if (voltasDoPlayer > 0) modelo.relerSeJaTem()
                    }
                    BackHandler { onde = Onde.Biblioteca }
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        TelaDaObra(
                            modelo = modelo,
                            aoVoltar = { onde = Onde.Biblioteca },
                            aoBaixar = { arquivoId -> app.baixarArquivo(arquivoId, alvo.obraId) },
                            aoTocar = { arquivoId, titulo, ondeParou, duracao ->
                                onde = Onde.Assistindo(
                                    obraId = alvo.obraId,
                                    arquivoId = arquivoId,
                                    titulo = titulo,
                                    ondeParou = ondeParou,
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
                        factory = fabricaDoPlayer(app.odeon, alvo),
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
            val abaAtual = onde.aba
            if (abaAtual != null) {
                EsqueletoComAbas(
                    atual = abaAtual,
                    aoTrocar = { onde = it.destino },
                    conteudo = corpo,
                )
            } else {
                corpo()
            }
        }
    }
}

/// A barra que muda de forma conforme a tela.
///
/// Barra embaixo no celular em pé; **trilho na lateral** em paisagem e em
/// tablet.
///
/// ## ⚠️ O padrão do Material faz o contrário disso em paisagem, e foi
/// ## o screenshot que denunciou
///
/// `NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo` escolhe **barra
/// inferior** quando a altura é compacta — e altura compacta é exatamente o
/// celular deitado. Medido no emulador em 04/08/2026, com a primeira versão
/// desta tela, que confiava no padrão:
///
/// | | medido |
/// |---|---|
/// | tela em paisagem | 2400×1080 |
/// | a barra, com rótulo | **230px dos 1080 — 21%** |
/// | fileiras da grade visíveis | **nenhuma** |
///
/// Ou seja: sobrava o título, a contagem, a fileira de "continuar" e mais nada
/// do acervo. É o mesmo defeito que fez o cabeçalho fixo sair deste app, que
/// custava 17% — este custava mais.
///
/// O comentário que estava aqui, e o do `libs.versions.toml`, afirmavam que o
/// artefato resolvia isso sozinho. Não resolve: ele resolve **as duas formas** e
/// a troca entre elas, que continua sendo o motivo de ele existir. Qual forma
/// usar em altura compacta é escolha de produto, e num app de pôsteres a
/// resposta é trilho — ele custa ~80dp de **largura** (9% dos 872dp) e devolve
/// a altura inteira.
///
/// `isHeightAtLeastBreakpoint` e não `windowHeightSizeClass`: o segundo está
/// depreciado no `window-core` 1.5.
///
/// ## As cores, e por que a barra não é da cor do fundo
///
/// `fundoElevado` (`--bg-raised`), e não `fundo`. A barra é uma coisa que está
/// **em cima** do acervo, e uma barra da cor exata do fundo é uma barra que
/// flutua sem chão — o rótulo e o ícone ficariam pendurados no nada quando a
/// grade rolasse por baixo.
@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
private fun EsqueletoComAbas(
    atual: Aba,
    aoTrocar: (Aba) -> Unit,
    conteudo: @Composable () -> Unit,
) {
    val info = currentWindowAdaptiveInfo()
    val alturaEspremida = !info.windowSizeClass
        .isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    NavigationSuiteScaffold(
        layoutType = if (alturaEspremida) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(info)
        },
        navigationSuiteItems = {
            Aba.entries.forEach { aba ->
                item(
                    selected = aba == atual,
                    /// Tocar na aba em que já se está não faz nada, e é de
                    /// propósito: reatribuir o mesmo destino trocaria o estado
                    /// por um igual e jogaria fora a rolagem da grade — 8.316
                    /// entradas de volta ao topo por um toque que não pedia isso.
                    onClick = { if (aba != atual) aoTrocar(aba) },
                    /// `contentDescription = null` porque o rótulo ao lado já
                    /// diz o nome, e o `NavigationSuiteScaffold` sempre o
                    /// desenha. Descrever o ícone também faria o leitor de tela
                    /// anunciar "biblioteca biblioteca".
                    icon = {
                        Icon(
                            painter = painterResource(aba.icone),
                            contentDescription = null,
                        )
                    },
                    label = { Text(aba.rotulo) },
                )
            }
        },
        containerColor = Cores.fundo,
        contentColor = Cores.texto,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = Cores.fundoElevado,
            navigationRailContainerColor = Cores.fundoElevado,
        ),
        content = conteudo,
    )
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

private fun fabricaDaLocadora(odeon: RepositorioOdeon) = viewModelFactory {
    initializer { ModeloDaLocadora(odeon) }
}

private fun fabricaDaObra(odeon: RepositorioOdeon, obraId: String) = viewModelFactory {
    initializer { ModeloDaObra(odeon, obraId) }
}

private fun fabricaDoPlayer(odeon: RepositorioOdeon, alvo: Onde.Assistindo) = viewModelFactory {
    initializer {
        ModeloDoPlayer(
            odeon = odeon,
            obraId = alvo.obraId,
            arquivoId = alvo.arquivoId,
            titulo = alvo.titulo,
            ondeParou = alvo.ondeParou,
            duracaoEmSegundos = alvo.duracaoEmSegundos,
        )
    }
}
