package dev.odeon.android.tv.telas

import android.content.Intent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import dev.odeon.android.dados.Barramento
import dev.odeon.android.dados.RepositorioOdeon
import dev.odeon.android.tv.lembrarModelo
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import dev.odeon.android.tv.ui.Sala
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableIntStateOf
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.biblioteca.ModeloDaBiblioteca
import dev.odeon.android.ui.guia.ModeloDoGuia
import dev.odeon.android.ui.locadora.ModeloDaLocadora
import dev.odeon.android.ui.mural.ModeloDoMural
import dev.odeon.android.ui.paravoce.ModeloParaVoce
import dev.odeon.android.ui.perfil.ModeloDoPerfil
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.odeon.android.ui.brilhoDoArco
import dev.odeon.android.ui.desenhaAPoeira
import dev.odeon.android.ui.desenhaOCone
import androidx.compose.runtime.LaunchedEffect
import dev.odeon.android.ui.aovivo.ModeloAoVivo
import dev.odeon.android.ui.busca.ModeloDaBusca

/// A casa: o trilho à esquerda, o destino à direita.
///
/// ## ⚠️ Os seis modelos nascem aqui, e não dentro de cada tela
///
/// Parece desperdício — abrir o app dispara seis chamadas de rede, sendo que só
/// uma tela está visível. É de propósito, e o motivo é o D-pad: trocar de aba
/// numa TV custa dois apertos, e é o gesto mais comum do controle. Com os
/// modelos nascendo por tela, cada troca esperaria uma requisição, e ir e voltar
/// entre biblioteca e locadora seria esperar duas vezes.
///
/// Seis requisições contra um servidor que roda na mesma casa é barato; esperar
/// a cada aperto de seta não é.
///
/// ⚠️ E é o `lembrarModelo` que segura isso: com `remember` puro, os seis
/// morreriam e renasceriam a cada recomposição da `when` abaixo — que é
/// exatamente o oposto do que este arranjo quer.
@Composable
fun TelaInicialDaTv(
    odeon: RepositorioOdeon,
    barramento: Barramento,
    /// Tocar direto, sem passar pela ficha — é o que o ao vivo faz ao
    /// sintonizar: ele já sabe a obra, o arquivo e onde a transmissão está.
    aoTocar: (String, String, String, Double, String?, String?) -> Unit,
    aoSintonizarDeFora: (String, String) -> Unit,
    destinoInicial: Destino = Destino.BIBLIOTECA,
    aoAbrirObra: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    /// `rememberSaveable` e não `remember`: numa Google TV, o sistema mata o app
    /// em segundo plano com muito mais gosto que um celular — a memória é menor
    /// e há um filme rodando em outro app metade do tempo. Voltar e cair na
    /// biblioteca depois de estar na locadora é o app se esquecendo de onde
    /// estava.
    /// ⚠️ `destinoInicial` existe por causa do fim de um programa num canal.
    ///
    /// Quando o filme acaba e o canal está num **vão** — a grade do Odeon tem
    /// vãos de minutos entre um programa e o seguinte —, não há o que tocar, e a
    /// tela do ao vivo é o lugar honesto pra devolver alguém: ela mostra o canal,
    /// a agulha, e a que horas começa o próximo. Cair na biblioteca seria trocar
    /// de assunto na cara de quem estava num canal.
    var destino by rememberSaveable { mutableStateOf(destinoInicial) }

    val biblioteca = lembrarModelo("biblioteca") { ModeloDaBiblioteca(odeon) }
    val locadora = lembrarModelo("locadora") { ModeloDaLocadora(odeon, barramento) }
    val mural = lembrarModelo("mural") { ModeloDoMural(odeon) }
    val guia = lembrarModelo("guia") { ModeloDoGuia(odeon) }
    val paraVoce = lembrarModelo("paravoce") { ModeloParaVoce(odeon) }
    val perfil = lembrarModelo("perfil") { ModeloDoPerfil(odeon) }
    val aoVivo = lembrarModelo("aovivo") { ModeloAoVivo(odeon) }
    val busca = lembrarModelo("busca") { ModeloDaBusca(odeon) }

    val focoDoTrilho = remember { FocusRequester() }

    /// ## «Voltar» numa aba que não é a primeira volta pra primeira
    ///
    /// É a convenção do Android TV, e ela resolve o problema que o D-pad cria:
    /// pra trocar de aba é preciso atravessar até o trilho, e sem isto a única
    /// saída de «estou no perfil e quero a biblioteca» seria refazer o caminho.
    ///
    /// ⚠️ Na **primeira** aba ele fica desligado de propósito, e aí a tecla volta
    /// a ser do sistema — que sai pro launcher. Esse é o comportamento certo:
    /// «voltar» na raiz de um app de TV **é** sair. O defeito da ficha era
    /// justamente estar na raiz sem estar na raiz.
    BackHandler(enabled = destino != Destino.BIBLIOTECA) {
        destino = Destino.BIBLIOTECA
    }

    val contexto = LocalContext.current
    val doPerfil by perfil.estado.collectAsStateWithLifecycle()

    /// ⚠️ **O retrato do trilho obrigou o perfil a carregar na abertura** —
    /// visto na TCL, T1.
    ///
    /// Antes o `ModeloDoPerfil` só era acordado quando alguém abria a tela do
    /// perfil, e isso bastava porque nada mais mostrava o rosto. Agora o topo do
    /// trilho é um retrato, e ele fica em **toda** tela: sem esta linha, a
    /// primeira impressão do app é um anel vazio com um `·` no lugar do nível.
    ///
    /// Foi exatamente o que apareceu na foto da primeira versão desta leva.
    ///
    /// `carregarSePreciso` e não `carregar`: ele já tem a guarda de não repetir,
    /// e a tela do perfil chama o mesmo — as duas chamadas viram uma requisição.
    LaunchedEffect(Unit) { perfil.carregarSePreciso() }

    /// A altura da lente, em pixels da janela. A `Trilho` reporta; o feixe usa.
    var alturaDaLente by remember { mutableFloatStateOf(0f) }

    /// Um contador, e não um booleano: escolher **o mesmo** destino duas vezes
    /// tem de fechar o menu as duas vezes, e um booleano que já está `true` não
    /// dispara efeito nenhum.
    var pediramSair by remember { mutableIntStateOf(0) }
    val gerenteDeFoco = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(pediramSair) {
        if (pediramSair == 0) return@LaunchedEffect
        /// Espera o conteúdo do novo destino existir antes de mandar o foco pra
        /// ele. Sem a folga, `moveFocus` não acha alvo e o menu fica aberto.
        kotlinx.coroutines.delay(80)
        gerenteDeFoco.moveFocus(FocusDirection.Right)
    }


    /// ⚠️ **O fundo saiu da `Row` e veio pro `Box`** — T1.
    ///
    /// Enquanto o `Cores.fundo` era da `Row`, ele era pintado **por cima** de
    /// qualquer coisa desenhada atrás, e o feixe não teria onde existir. Agora o
    /// fundo é do contêiner, o feixe vem logo depois dele, e a `Row` por cima é
    /// transparente.
    Box(modifier.fillMaxSize().background(Cores.fundo)) {
        /// ⚠️ O feixe some junto com a lente na locadora. Feixe sem lâmpada é a
        /// coisa que este trilho inteiro existe pra não fazer — foi um defeito
        /// medido na T1, quando o perfil escolhido deixava a luz órfã.
        if (destino != Destino.LOCADORA) {
            FeixeDaCabine(alturaDaLente = { alturaDaLente }, chave = destino)
        }

        /// ## ⚠️ A sobreposição **não sobreviveu ao D-pad** — visto na TCL
        ///
        /// O trilho chegou a flutuar por cima do conteúdo, com o conteúdo parado
        /// atrás. Desenhava bonito e **parou de colapsar**: o ▶ não devolvia o
        /// foco, e o menu ficava aberto pra sempre.
        ///
        /// A causa é geométrica e não tem conserto barato: a busca direcional do
        /// Compose procura um alvo **naquela direção**, e com o painel de 240dp
        /// por cima, tudo o que estava à direita estava *debaixo* dele. Foco de
        /// D-pad não entende profundidade — ele entende posição.
        ///
        /// Por isso TV empurra em vez de sobrepor, e não é falta de imaginação:
        /// é a única topologia em que «à direita» quer dizer a mesma coisa pro
        /// olho e pro foco.
        ///
        /// ⚠️ O que **sobrou** da ideia é o que importava: fechado ele é um vão
        /// de [LARGURA_FECHADO], contra os 96dp de antes. O empurrão só acontece
        /// enquanto o menu está aberto, que é o instante em que a pessoa está
        /// olhando pro menu e não pra grade.
        Row(Modifier.fillMaxSize()) {
        Trilho(
            atual = destino,
            /// ⚠️ **Escolher fecha o menu**, e o fechamento é uma consequência
            /// e não um comando: o trilho abre quando tem foco, então devolver o
            /// foco ao conteúdo é o que o fecha.
            ///
            /// Sem isso, apertar `OK` num destino trocava a tela e **deixava o
            /// painel aberto por cima dela** — a pessoa escolhia um lugar e
            /// continuava no menu, tendo que apertar ▶ pra ver o que pediu.
            ///
            /// ⚠️ O pedido não pode ser feito aqui, na hora do clique: a tela
            /// nova ainda não foi composta, e não há para onde mandar o foco.
            /// Por isso ele vira um recado que o `LaunchedEffect` abaixo entrega
            /// no quadro seguinte.
            aoTrocar = { destino = it; pediramSair = pediramSair + 1 },
            nome = doPerfil.perfil?.let { it.nome.ifBlank { it.username } } ?: "",
            rosto = perfil.arte(doPerfil.perfil?.avatar?.arte),
            nivel = doPerfil.perfil?.progresso?.nivel,
            fatiaDoNivel = doPerfil.perfil?.fatiaDoNivel ?: 0f,
            /// ⚠️ A busca virou **destino**, e não mais um `Intent`. Ela
            /// fecha o trilho pelo mesmo caminho dos outros — o `pediramSair`
            /// devolve o foco ao conteúdo no quadro seguinte.
            aoBuscar = { destino = Destino.BUSCA; pediramSair = pediramSair + 1 },
            aoMoverALente = { alturaDaLente = it },
            /// ⚠️ Só a locadora apaga a cabine, e é a §5.2: entrou-se na loja, e
            /// a luz agora é dela — a marquise da fachada vira a fonte da tela.
            cabineApagada = destino == Destino.LOCADORA,
            foco = focoDoTrilho,
        )


        /// ## ⚠️ Sem este bloco o trilho é **inalcançável** — visto na TCL em
        /// 12/08/2026
        ///
        /// O comentário do `Trilho` afirmava que «a seta esquerda a partir da
        /// primeira coluna de cartazes cai nele». Não caía. Medido com
        /// `uiautomator`: com o foco no primeiro cartão de «continuar
        /// assistindo», ◀ levava pro **terceiro** cartão da mesma fileira
        /// (`[1770,93]`), na borda direita da tela.
        ///
        /// A causa é a `LazyRow`: ela implementa busca de foco além dos limites
        /// pra poder compor itens que ainda não existem, e no item 0 essa busca
        /// enrola pro fim da lista em vez de devolver o evento pra fora. O menu
        /// ficava sem porta de entrada — e com ele, cinco das seis telas.
        ///
        /// `focusProperties { exit }` é a saída certa porque ele age **na
        /// fronteira do grupo**, e não dentro dele: ◀ continua andando entre os
        /// cartões de uma fileira normalmente, e só quando o foco realmente ia
        /// sair é que ele é mandado pro trilho.
        ///
        /// ⚠️ Ele exige o `focusGroup()` ao lado. Sem o grupo não há fronteira, e
        /// o `exit` nunca é consultado — o defeito volta, calado.
        ///
        /// O `exit` ainda é `@ExperimentalComposeUiApi`, e a marca fica aqui e
        /// não no módulo: quando ele estabilizar, é uma linha que some, e o
        /// compilador é quem vai avisar.
        @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
        Box(
            Modifier
                /// ⚠️ O lado direito recupera os 24dp que o `overscanH` perdeu: lá
                /// existe borda de tela, e a regra dos 5% continua valendo.
                .padding(end = Sala.overscanH)
                .focusGroup()
                /// ## ⚠️ O `left` daqui saiu — 14/08/2026
                ///
                /// Ele dizia «esquerda é o trilho», e `focusProperties` **desce
                /// pros descendentes**: quem herdava era cada cartaz, cada tecla,
                /// cada bloco da grade. Ou seja, ◀ em qualquer lugar do conteúdo
                /// pulava pro menu, mesmo havendo um vizinho à esquerda.
                ///
                /// Achado no teclado da busca, onde o defeito é gritante — um
                /// teclado em que ◀ não anda uma letra pra trás é um teclado em
                /// que não se corrige. Conferido depois na **biblioteca**, e lá
                /// estava igual: do segundo cartaz de uma fileira, ◀ abria o
                /// trilho em vez de ir pro primeiro. Passou despercebido porque
                /// numa grade de cartazes quase sempre se anda pra direita.
                ///
                /// ⚠️ O `exit` **sozinho já faz o que se queria**, e faz melhor:
                /// ele só dispara quando o foco realmente sai do grupo — isto é,
                /// quando não há mais nada à esquerda dentro do conteúdo. Que é a
                /// definição de «cheguei na borda».
                .focusProperties {
                    exit = { direcao ->
                        if (direcao == FocusDirection.Left) focoDoTrilho else FocusRequester.Default
                    }
                },
        ) {
            ConteudoDoDestino(
                destino = destino,
                biblioteca = biblioteca,
                locadora = locadora,
                mural = mural,
                guia = guia,
                paraVoce = paraVoce,
                perfil = perfil,
                aoVivo = aoVivo,
                busca = busca,
                aoTocar = aoTocar,
                aoSintonizarDeFora = aoSintonizarDeFora,
                aoAbrirObra = aoAbrirObra,
                saidaEsquerda = focoDoTrilho,
            )
        }
        }


    }
}

/// O feixe: a luz que sai da cabine e abre sobre a tela.
///
/// ## Ele é a §4.2, e é a peça de maior risco do documento
///
/// > «⚠️ O feixe é **decoração de fundo**, desenhado atrás do conteúdo com alfa
/// > baixo. Ele não pode competir com um pôster nem atrapalhar a leitura de um
/// > título. Se na TV ele ficar forte demais, ele diminui — a régua é a mesma do
/// > grão da R6, que foi **testado e reprovado** no celular.»
///
/// A §10 repete: «O feixe pode ser demais. Ele é o efeito com maior chance de
/// virar ruído atrás de um pôster.» Então ele nasce com o alfa que a barra do
/// celular usa **dividido por três**, e o número está numa constante com nome
/// pra ser fácil de mexer depois de ver na TV.
///
/// ## O desenho é o mesmo do celular, deitado
///
/// Nada aqui é novo: o cone e a poeira são os do `Arco.kt`, com o `deitado`
/// ligado. O que muda é de onde a luz nasce — a lente do destino escolhido, na
/// borda esquerda — e pra onde ela abre.
///
/// ⚠️ **O raio é a largura da tela**, e não um múltiplo da altura de uma
/// fileira como no celular. Lá o cone tinha de caber numa barra de 143dp; aqui
/// ele tem a sala inteira, e um cone que morre no meio da tela pareceria uma
/// mancha, não um feixe.
@Composable
private fun FeixeDaCabine(alturaDaLente: () -> Float, chave: Any?) {
    /// Os mesmos dez quadros do trilho, pela mesma chave: a lâmpada é uma só, e
    /// o feixe é o que ela joga. Se as duas animações não partissem do mesmo
    /// evento, a luz e o facho piscariam fora de sincronia.
    val brilho = brilhoDoArco(chave)

    Canvas(Modifier.fillMaxSize()) {
        /// ⚠️ **A altura é lida aqui dentro, e é a otimização inteira.**
        ///
        /// Ela era um `Float` passado por valor: quem lê um estado é quem
        /// **recompõe** quando ele muda, e a leitura acontecia no corpo do
        /// `TelaInicialDaTv` — que é o pai de todas as telas. Cada movimento de
        /// foco no trilho move a lente, e mover a lente recompunha a biblioteca
        /// inteira junto.
        ///
        /// Medido na TCL: **300ms por tecla**, com quase nenhum quadro entre uma
        /// e outra. Não era animação pesada; era a grade toda sendo recomposta
        /// porque uma barrinha de 3dp mudou de lugar.
        ///
        /// Como lambda, a leitura acontece na fase de **desenho** deste `Canvas`.
        /// A composição não é invalidada; só este retângulo é repintado.
        val lente = alturaDaLente()
        if (lente <= 0f) return@Canvas
        val forca = brilho * FORCA_DO_FEIXE
        val origem = Offset(0f, lente)

        /// ## ⚠️ O raio é **curto**, e essa é a diferença entre facho e névoa
        ///
        /// Ele era `size.width` — meio disco de 960dp de raio sobre uma tela de
        /// 960dp. Um radial esticado assim não tem queda visível em lugar nenhum:
        /// vira um lavado marrom uniforme cobrindo tudo. E aumentar a força só
        /// engrossa o lavado, que foi o que aconteceu na primeira tentativa.
        ///
        /// O próprio `Arco.kt` já dizia como: «o radial é isotrópico, e o que faz
        /// ele parecer um cone é o centro cair **fora da área visível**». No
        /// celular a lente fica na aresta de baixo e a barra tem 89dp de altura —
        /// o que se vê é uma fatia, e a fatia é o cone.
        ///
        /// Aqui a fatia se faz pelo raio: 340dp numa tela de 960 significa que a
        /// luz nasce forte na lente, atravessa o trilho, alcança a primeira
        /// coluna de cartazes e **acaba** — e é o acabar que faz o olho ver de
        /// onde ela veio.
        val alcanceDoFacho = 340.dp.toPx()
        desenhaOCone(centro = origem, raio = alcanceDoFacho, forca = forca)
        /// ## ⚠️ Os dois números da poeira foram **medidos na TCL**, e a
        /// primeira versão errou os dois
        ///
        /// Ela usava `alcance = altura/2` e o mesmo raio do cone. Na foto o
        /// resultado não foi um feixe com pó dentro: foi um **campo de
        /// estrelas** cobrindo a tela inteira, com grão aceso no canto superior
        /// direito, a dois metros de qualquer luz.
        ///
        /// É o defeito que a §4.2 previu com todas as letras — «ele não pode
        /// competir com um pôster» — e a §10 marcou como o de maior risco do
        /// documento.
        ///
        /// A causa é geométrica, e não de alfa: no celular esta mesma conta roda
        /// dentro de uma caixa de 143dp de altura, então a região onde o grão
        /// passa do corte é pequena por construção. Aqui a caixa é a sala
        /// inteira, e a mesma conta acende quase tudo. **Baixar a força não
        /// resolveria** — só deixaria o campo de estrelas mais fraco.
        ///
        /// Então o que encolheu foi a **região**:
        ///
        /// | | |
        /// |---|---|
        /// | `alcance` | 20% da altura, e não 50% — a espessura do feixe |
        /// | `raio` | metade da largura, e não a tela toda — até onde o pó chega |
        desenhaAPoeira(
            eixo = lente,
            raio = alcanceDoFacho * 0.8f,
            deitado = true,
            forca = forca,
            alcance = size.height * 0.16f,
        )
    }
}

/// Quanto do facho do celular o feixe da sala usa.
///
/// ⚠️ **Um terço, e é chute honesto — não medida.** A §4.2 manda o feixe ser
/// fraco o bastante pra não competir com um pôster, e a §10 avisa que ele é «o
/// efeito com maior chance de virar ruído». O alfa da barra do celular foi
/// calibrado numa faixa de 143dp de altura; aqui a mesma luz cobre 540dp, e
/// espalhar o mesmo alfa por quatro vezes mais área lê como véu, não como facho.
///
/// Este número existe pra ser mexido **depois de ver na TV**, e é por isso que
/// ele tem nome em vez de estar escrito no meio da conta.
/// ⚠️ Subiu de 0,34 pra 0,55 a pedido do dono: «as luzes não estão legais como eu
/// fiz no Android». A piscada de dez quadros já era a mesma — o que faltava era
/// **força**, e num facho fraco a coreografia acontece sem ninguém ver.
///
/// O pico dos quadros é 1,35, então o brilho real chega a ~0,74 no primeiro
/// estalo e assenta em 0,55. É o que faz a troca de destino parecer uma lâmpada
/// **ligando**, e não um degradê trocando de lugar.
private const val FORCA_DO_FEIXE = 0.85f

/// A tela do destino escolhido.
///
/// Separada da `TelaInicialDaTv` só por arrumação: com ela embutida, o `when`
/// ficava aninhado dentro do `Box` do `focusProperties`, e o bloco de comentário
/// que explica o `exit` passava a ter trinta linhas de layout entre ele e o
/// código que ele descreve.
@Composable
private fun ConteudoDoDestino(
    destino: Destino,
    biblioteca: ModeloDaBiblioteca,
    locadora: ModeloDaLocadora,
    mural: ModeloDoMural,
    guia: ModeloDoGuia,
    paraVoce: ModeloParaVoce,
    perfil: ModeloDoPerfil,
    aoVivo: ModeloAoVivo,
    busca: ModeloDaBusca,
    aoTocar: (String, String, String, Double, String?, String?) -> Unit,
    aoSintonizarDeFora: (String, String) -> Unit,
    aoAbrirObra: (String) -> Unit,
    saidaEsquerda: FocusRequester,
) {
    when (destino) {
        Destino.BIBLIOTECA -> TelaDaBibliotecaDaTv(
            modelo = biblioteca,
            aoAbrirObra = aoAbrirObra,
            aoTocar = { item -> aoAbrirObra(item.id) },
            saidaEsquerda = saidaEsquerda,
        )

        Destino.LOCADORA -> TelaDaLocadoraDaTv(locadora, aoAbrirObra)
        Destino.MURAL -> TelaDoMuralDaTv(mural, aoAbrirObra)
        Destino.GUIA -> TelaDoGuiaDaTv(guia, aoAbrirObra)
        Destino.PARA_VOCE -> TelaParaVoceDaTv(paraVoce, aoAbrirObra)
        Destino.PERFIL -> TelaDoPerfilDaTv(perfil, aoAbrirObra)

        Destino.BUSCA -> TelaDaBuscaDaTv(
            modelo = busca,
            aoAbrirObra = aoAbrirObra,
            saidaEsquerda = saidaEsquerda,
        )

        Destino.AO_VIVO -> TelaAoVivoDaTv(
            modelo = aoVivo,
            aoTocar = aoTocar,
            aoSintonizarDeFora = aoSintonizarDeFora,
            saidaEsquerda = saidaEsquerda,
        )
    }
}

