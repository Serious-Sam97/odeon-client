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
            FeixeDaCabine(alturaDaLente = alturaDaLente, chave = destino)
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
            aoTrocar = { destino = it },
            nome = doPerfil.perfil?.let { it.nome.ifBlank { it.username } } ?: "",
            rosto = perfil.arte(doPerfil.perfil?.avatar?.arte),
            nivel = doPerfil.perfil?.progresso?.nivel,
            fatiaDoNivel = doPerfil.perfil?.fatiaDoNivel ?: 0f,
            aoBuscar = { abrirABuscaDoSistema(contexto) },
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
                .focusProperties {
                    exit = { direcao ->
                        if (direcao == FocusDirection.Left) focoDoTrilho else FocusRequester.Default
                    }
                    left = focoDoTrilho
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
private fun FeixeDaCabine(alturaDaLente: Float, chave: Any?) {
    /// Os mesmos dez quadros do trilho, pela mesma chave: a lâmpada é uma só, e
    /// o feixe é o que ela joga. Se as duas animações não partissem do mesmo
    /// evento, a luz e o facho piscariam fora de sincronia.
    val brilho = brilhoDoArco(chave)

    Canvas(Modifier.fillMaxSize()) {
        if (alturaDaLente <= 0f) return@Canvas
        val forca = brilho * FORCA_DO_FEIXE
        val origem = Offset(0f, alturaDaLente)
        desenhaOCone(centro = origem, raio = size.width, forca = forca)
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
            eixo = alturaDaLente,
            raio = size.width * 0.5f,
            forca = forca,
            alcance = size.height * 0.20f,
            deitado = true,
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
private const val FORCA_DO_FEIXE = 0.55f

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

        Destino.AO_VIVO -> TelaAoVivoDaTv(
            modelo = aoVivo,
            aoTocar = aoTocar,
            aoSintonizarDeFora = aoSintonizarDeFora,
            saidaEsquerda = saidaEsquerda,
        )
    }
}

/// Abre a busca **do sistema**, por voz.
///
/// ## Por que não há um campo de texto no lugar disto
///
/// A §5.1 do redesenho: «Digitar com D-pad é soletrar. (…) Um campo de texto
/// aqui seria oferecer o pior caminho como se fosse o principal.»
///
/// O que o Odeon já tem pra isso é o `busca/ProvedorDeBusca.kt`, registrado no
/// manifesto: ele responde as sugestões que a busca da Google TV mostra. Ou
/// seja, a busca do sistema **já sabe** procurar no acervo — o que faltava era
/// um lugar no app pra chamá-la.
///
/// ## ⚠️ Os dois `Intent`, e por que são dois
///
/// `GLOBAL_SEARCH` é o que abre a busca da casa numa Google TV. Nem toda TV a
/// tem — algumas fabricantes trocam o app de busca ou removem — e aí a segunda
/// tentativa é o `ACTION_ASSIST`, que é o assistente e cobre o mesmo pedido.
///
/// ⚠️ **Se as duas falharem, não acontece nada, e é decisão.** A alternativa
/// seria um aviso — mas «a busca não abriu» não é acionável por quem está com o
/// controle na mão, e o §8b vale nos dois sentidos: informar o que não se pode
/// resolver é ruído. O `runCatching` loga, que é o que permitiu achar o defeito
/// do canal da home.
///
/// ## ⚠️ O que aconteceu na TCL, e não é o que se esperava — 12/08/2026
///
/// O botão dispara, o sistema aceita, e a tela que aparece é o **onboarding do
/// launcher da Google TV** — não uma busca.
///
/// Isolado antes de culpar o código: os dois intents resolvem pro app certo
/// (`com.google.android.katniss`, o `SearchActivityTrampoline`), e disparar
/// `android.search.action.GLOBAL_SEARCH` **direto pelo `adb`**, sem passar por
/// aqui, cai exatamente no mesmo onboarding. Ou seja, é estado do aparelho — a
/// conta da Google TV desta TCL não terminou de ser configurada —, e não o
/// caminho errado.
///
/// Fica anotado assim de propósito: **o botão não foi visto funcionando**. O
/// README já listava «a busca por voz: o provedor está no manifesto e responde;
/// ninguém segurou o microfone do controle ainda», e continua listando. Escrever
/// aqui que a busca funciona seria o defeito que este projeto mais paga.
private fun abrirABuscaDoSistema(contexto: android.content.Context) {
    val tentativas = listOf(
        Intent("android.search.action.GLOBAL_SEARCH"),
        Intent(Intent.ACTION_ASSIST),
    )
    for (intent in tentativas) {
        val deu = runCatching {
            contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        if (deu.isSuccess) return
        android.util.Log.w("Odeon", "busca do sistema recusou ${intent.action}", deu.exceptionOrNull())
    }
}
