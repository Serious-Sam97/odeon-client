package dev.odeon.android.ui.locadora

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.Emprestada
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.odeon.android.dados.CaixaExposta
import dev.odeon.android.dados.EstanteExposta
import dev.odeon.android.ui.Serifada
import dev.odeon.android.ui.Tipo
import dev.odeon.android.ui.corDeHex
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.RotuloDeSecao
import dev.odeon.android.ui.chega
import dev.odeon.android.ui.inclinacao
import dev.odeon.android.ui.prazoDoEmprestimo
import dev.odeon.android.ui.viraQuando
import kotlin.math.sin

/// A locadora.
///
/// ## Ela é 2D, e isso é decisão da espec — não limitação encontrada
///
/// A estante da web é **CSS 3D**: `perspective`, `preserve-3d`, `translateZ`. A
/// §3 diz que o Compose não tem equivalente — `graphicsLayer` faz rotação e
/// câmera, mas não compõe uma hierarquia com filhos em profundidade — e propõe
/// o substituto: **prateleira 2D com a arte das caixas, e o girar da caixa
/// virando um `flip` de duas faces**.
///
/// O que se perde é a profundidade da cena, não a metáfora: a caixa continua
/// sendo objeto, continua tendo frente e verso, e continua saindo da estante
/// quando alguém a leva.
/// ## O "‹ biblioteca" saiu daqui, e não foi esquecimento
///
/// A locadora virou **aba**, e um destino de primeiro nível não tem pra onde
/// voltar: ele é raiz tanto quanto a biblioteca. O botão que estava aqui vinha
/// de quando esta tela só era alcançável por um link no cabeçalho da grade.
///
/// O botão físico de voltar continua levando à biblioteca — quem trata disso é o
/// `BackHandler` no `AppOdeon`, que é onde a navegação mora.
@Composable
fun TelaDaLocadora(
    modelo: ModeloDaLocadora,
    aoAbrirObra: (String) -> Unit = {},
    /// Tocar direto, vindo do menu do disco — com o ponto de partida escolhido
    /// lá (o começo, o «continuar» ou um capítulo).
    aoTocar: (obraId: String, arquivoId: String, titulo: String, de: Double, duracao: Double?) -> Unit =
        { _, _, _, _, _ -> },
    /// Avisa quem desenha a barra de abas que há uma **sobreposição de tela
    /// cheia** aqui dentro.
    ///
    /// ## Foi a paisagem que revelou a necessidade
    ///
    /// Em pé, a barra fica embaixo e o palco escuro por cima dela quase engana.
    /// Deitado, a navegação vira **trilho lateral** — e o menu do disco apareceu
    /// com «biblioteca · locadora · mural» de pé ao lado dele, recortados pela
    /// largura do trilho. Um menu de DVD com a navegação do app do lado não é
    /// uma tela cheia: é uma janela.
    ///
    /// O palco e o menu são sobreposições (§14 da referência), como a ficha e o
    /// player — e esses dois já são desenhados fora do esqueleto.
    aoMudarSobreposicao: (Boolean) -> Unit = {},
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    val cheia = estado.naMao != null || estado.menu != null
    androidx.compose.runtime.LaunchedEffect(cheia) { aoMudarSobreposicao(cheia) }

    Box(Modifier.fillMaxSize()) {
        Loja(modelo = modelo, estado = estado)
        PalcoPorCima(
            modelo = modelo,
            estado = estado,
            aoAbrirObra = aoAbrirObra,
            aoTocarDoMenu = { menu, segundos ->
                modelo.fecharOMenu()
                modelo.guardar()
                aoTocar(menu.obraId, menu.arquivoId, menu.titulo, segundos, menu.duracao)
            },
            aoTocarOFilme = aoTocar,
        )
    }
}

/// A loja: as regras, o que saiu da estante e a vitrine.
@Composable
private fun Loja(modelo: ModeloDaLocadora, estado: EstadoDaLocadora) {
    if (estado.carregando && estado.prateleira == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cores.destaque)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        /// ## A entrada da loja — a arandela acesa e o título na luz dela
        ///
        /// «A loja da esquina, 21h», o desenho que o dono aprovou em 06/08: o
        /// topo deixou de ser um cabeçalho de app e virou a parede da entrada.
        /// A arandela é quem ilumina; o título não tem brilho próprio — o halo
        /// dele é o facho da lâmpada, e por isso os dois andam juntos.
        Box(Modifier.fillMaxWidth()) {
            Arandela(Modifier.align(Alignment.TopCenter))
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "locadora",
                    style = TextStyle(
                        fontFamily = Serifada,
                        fontSize = 34.sp,
                        letterSpacing = 0.04.em,
                        color = Color(0xFFE8CF9A),
                        shadow = Shadow(
                            color = Cores.destaque.copy(alpha = 0.45f),
                            blurRadius = 26f,
                        ),
                    ),
                )
                /// O subtítulo do mock dizia «aberta até meia-noite» — e saiu:
                /// a loja não fecha de verdade, e horário inventado é mentira
                /// com cara de metadado (§18). «Acervo da casa» é o que ela é.
                Text(
                    text = "ACERVO DA CASA",
                    style = Tipo.rotulo.copy(fontSize = 10.sp, letterSpacing = 0.3.em),
                    color = Cores.destaqueApagado,
                )
            }
        }

        /// **A porta da loja** — as contagens, agora em etiquetas de papel
        /// penduradas por barbante, como o mock aprovado.
        ///
        /// Só nasce com a vitrine na mão: sem ela, «nada com capa por aqui»
        /// seria o app afirmando sobre um acervo que ele não conseguiu ler. Erro
        /// de rede não é resposta vazia (§18). E o «de N no acervo» que morava
        /// aqui desceu pra **nota do caixa**, no fim da rolagem — o topo ficou
        /// com o que muda, o resumo foi pro recibo.
        ///
        /// ⚠️ A frase inteira continua indo à semântica — o desenho quebrou as
        /// contagens em dois papeizinhos, mas quem lê por leitor de tela recebe
        /// a gramática da [portaDaLoja], a mesma que os testes guardam.
        estado.loja?.let { loja ->
            val frase = portaDaLoja(estado.naPrateleira, estado.sorteadas, loja.noAcervo)
            if (estado.naPrateleira == 0) {
                Text(
                    text = frase,
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.textoApagado,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { contentDescription = frase },
                    horizontalArrangement = Arrangement.spacedBy(
                        22.dp,
                        Alignment.CenterHorizontally,
                    ),
                ) {
                    EtiquetaPendurada(
                        numero = "${estado.naPrateleira}",
                        /// O buraco continua andando junto do número de que ele
                        /// é buraco — a régua da porta antiga não mudou.
                        rotulo = buildString {
                            append("na prateleira")
                            val fora = estado.sorteadas - estado.naPrateleira
                            if (fora > 0) append(" · $fora fora")
                        },
                        angulo = -3f,
                    )
                    EtiquetaPendurada(
                        numero = "${estado.sorteadas}",
                        rotulo = "nesta semana",
                        angulo = 2f,
                    )
                }
            }
        }

        estado.erro?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Cores.perigo)
        }

        /// **O balcão** — quem está na loja, o seu limite, o recado ao vivo e o
        /// que acabou de voltar.
        ///
        /// Ele fica **acima** das regras da casa, e é de propósito: as regras são
        /// o contrato da loja, que muda quase nunca; o balcão é o que aconteceu
        /// nela, que muda a toda hora. O olho de quem abre a locadora procura a
        /// segunda coisa.
        estado.prateleira?.let { prateleira ->
            Balcao(prateleira = prateleira, recado = estado.recado)
        }

        /// ## ⚠️ As regras da casa saíram da tela — 05/08/2026
        ///
        /// Elas eram duas linhas de 84px imediatamente antes da primeira estante,
        /// e diziam «escassez ligada: uma cópia por caixa · limite de 3 por
        /// pessoa · prazo de 7 dias». O contrato da loja no lugar mais caro da
        /// tela, mudando quase nunca.
        ///
        /// **O que sai com elas, e onde cada coisa continua dita:**
        ///
        /// | | |
        /// |---|---|
        /// | o limite | na linha dos chips — «pegar mais 3», e ele é o número que muda |
        /// | o prazo | na cinta de cada caixa — «5 dias», «vence amanhã» |
        /// | a escassez | **no buraco da fileira**, que passou a existir hoje |
        ///
        /// A terceira linha é a que autoriza esta remoção. Enquanto a caixa
        /// alugada continuava de pé na prateleira, «escassez ligada» era a única
        /// pista de que uma cópia por caixa era regra; agora a fileira encurta na
        /// frente de quem olha, e a porta da loja conta quantas — a regra virou
        /// coisa vista, e uma frase que repete o que se vê é legenda de tela.
        ///
        /// **O que se perde de verdade:** o estado `escassez desligada`, que não
        /// tem desenho — quando ninguém barra ninguém, não há buraco nenhum pra
        /// notar a ausência. É perda aceita, não esquecida: numa loja sem
        /// escassez, a informação «ninguém te barra» é justamente a que não muda
        /// nada no que você pode fazer.

        /// O mesmo `key` das estantes, pelo mesmo motivo: as seções não podem
        /// renascer só porque o vizinho de cima mudou de tamanho.
        if (estado.minhas.isNotEmpty()) {
            key("comigo") {
            Secao("comigo", quantos = estado.minhas.size) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(estado.minhas, key = { _, f -> f.id }) { i, fita ->
                        Caixa(
                            indice = i,
                            fita = fita,
                            arte = modelo.arte(fita.poster),
                            /// Devolver só existe nas minhas. Nas dos outros o
                            /// gesto seria mexer no empréstimo de alguém — e o
                            /// §11 é explícito sobre isso.
                            aoDevolver = { modelo.devolver(fita.id) },
                            devolvendo = estado.devolvendo == fita.id,
                            ehVhs = estado.ehVhs(fita.ano),
                            aoAbrir = { modelo.pegarNaMao(fita) },
                        )
                    }
                }
            }
            }
        }

        if (estado.dosOutros.isNotEmpty()) {
            key("dos-outros") {
            Secao("na mão de alguém", quantos = estado.dosOutros.size) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(estado.dosOutros, key = { _, f -> f.id }) { i, fita ->
                        Caixa(
                            indice = i,
                            fita = fita,
                            arte = modelo.arte(fita.poster),
                            aoDevolver = null,
                            devolvendo = false,
                            aoPedirDeVolta = { modelo.pedirDeVolta(fita.id) },
                            pedindo = estado.pedindo == fita.id,
                            ehVhs = estado.ehVhs(fita.ano),
                        )
                    }
                }
            }
            }
        }

        /// Vazio de verdade tem frase, e não silêncio.
        ///
        /// Aqui o §24 **não** vale: uma locadora sem nenhuma caixa fora é um
        /// estado normal e informativo — "está tudo na estante" é notícia. Uma
        /// tela em branco, não.
        ///
        /// ⚠️ **Mas só quando não há vitrine.** Com a porta da loja desenhada, a
        /// frase virou eco: `40 caixas na prateleira` **sem** o `, N fora` já diz
        /// que não há nenhuma fora, e diz melhor, porque diz com número. Eram
        /// duas linhas a 950px de distância afirmando o mesmo fato — e a de baixo
        /// era a que custava um respiro de 42dp logo antes da primeira estante.
        ///
        /// Sem vitrine a frase continua sendo necessária: aí a porta diz «nada com
        /// capa por aqui», que é sobre a loja, e esta é sobre os empréstimos.
        if (estado.minhas.isEmpty() && estado.dosOutros.isEmpty() && estado.erro == null &&
            estado.expostas.isEmpty()
        ) {
            /// ⚠️ As tábuas vazias só entram quando **não há vitrine**.
            ///
            /// Elas nasceram pra consertar um vazio: a tela sem empréstimo era
            /// 1.400 pixels de preto com uma frase. Com a vitrine desenhada
            /// abaixo, esse vazio não existe mais — e duas prateleiras vazias
            /// empurrando a loja pra baixo passam de conserto a estorvo.
            ///
            /// É a mesma decisão que o próprio conserto tomou, invertida: o que
            /// muda é o que está em volta.
            EstanteVazia(comTabuas = estado.expostas.isEmpty())
        }

        /// ## A vitrine — e ela é a locadora
        ///
        /// Até aqui esta tela mostrava só o que **saiu** da estante: os seus
        /// empréstimos e os dos outros. O acervo exposto — a loja em si — nunca
        /// foi pedido, e `/api/locadora/estantes` existe desde antes deste app.
        ///
        /// É o mesmo padrão de `height`, `size_bytes` e `tags`: o servidor já
        /// dava e o cliente não pegava. A diferença é o tamanho — não era um
        /// campo, era metade de uma tela.
        ///
        /// ⚠️ **São as `expostas`, e não `loja.estantes`.** A caixa que alguém
        /// levou sai da fileira e o vão fica aberto — ver `EstadoDaLocadora`.
        /// ## A vitrine segue a mão — a `Inclinacao` chega à locadora
        ///
        /// Na estante o dedo não gira caixa (o arrasto é da fileira, e a decisão
        /// está na `CaixaNaEstante`). Mas o aparelho **inclina**, e é o mesmo
        /// sensor que a ficha já usa na paralaxe do pôster: a vitrine inteira
        /// muda de pose alguns graus, junta — como uma prateleira de verdade
        /// quando a cabeça se move. É volume sem disputar gesto nenhum.
        ///
        /// Lida **uma vez** pra tela inteira, e não por estante: cada chamada de
        /// `inclinacao()` registra um listener de acelerômetro, e cinco estantes
        /// com cinco listeners é o mesmo sensor pago cinco vezes. Com animação
        /// desligada no sistema o listener nem liga — a régua mora lá.
        val tilt by inclinacao()
        val poseDaVitrine = Pose(
            giroY = Pose.POSE_DE_REPOUSO_Y + tilt.x * 6f,
            giroX = Pose.POSE_DE_REPOUSO_X + tilt.y * 4f,
        )

        estado.expostas.forEachIndexed { indice, estante ->
            /// ⚠️ Tocar numa caixa **não abre mais a ficha**: põe a caixa na
            /// mão. É a locadora da web (§6) — a ficha é o caminho da
            /// biblioteca, e aqui o caminho é o objeto. Quem quiser a ficha
            /// abre a caixa e toca na mídia.
            ///
            /// ## ⚠️ O `key(nome)` é o que impede a loja de «dar reload»
            ///
            /// O dono viu as caixas **pularem** ao voltar do palco: a estante
            /// nua por um quadro, e as caixas caindo de novo. A causa é
            /// composição posicional — quando o recado do barramento nasce ou o
            /// balcão some, tudo abaixo desliza de posição na `Column`, o
            /// Compose descarta e recria as estantes, e o `chega` reanima do
            /// zero como se a tela tivesse recarregado. Com a chave, a estante
            /// «terror» continua sendo ela mesma em qualquer posição.
            key(estante.nome) {
                Estante(
                    estante = estante,
                    arte = modelo::arte,
                    ehVhs = estado::ehVhs,
                    pose = poseDaVitrine,
                    prazoEmDias = estado.prateleira?.opcoes?.prazoEmDias ?: 0,
                    indice = indice,
                    aoAbrir = { id -> estante.caixas.firstOrNull { it.id == id }?.let(modelo::pegarNaMao) },
                )
            }
        }

        /// Quando a vitrine vira.
        ///
        /// O comentário da web diz o que este campo carrega: «é o que torna a
        /// rotação **promessa, não sorteio**». Uma seleção que muda sem data
        /// anunciada é aleatoriedade; com data, é programação — e é a diferença
        /// entre um acervo embaralhado e uma locadora que troca a vitrine na
        /// segunda.
        ///
        /// ⚠️ A palavra vem do `viraQuando`, e não do campo cru. Até aqui esta
        /// linha imprimia o `vira_em` como veio do banco — uma data ISO no meio
        /// de uma frase em português. E é o **mesmo instante** da revista do
        /// guia: duas telas dizendo a mesma segunda com palavras diferentes
        /// pareceriam dois relógios.
        viraQuando(estado.loja?.viraEm)?.let { quando ->
            Text(
                text = "a vitrine vira $quando",
                style = MaterialTheme.typography.labelSmall,
                color = Cores.textoApagado,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        /// ## A nota do caixa — o fim da visita
        ///
        /// Quem rolou a loja inteira sai pelo caixa, e o caixa imprime o resumo:
        /// acervo, prazo, as pessoas com as três contagens, seu limite. É o
        /// dado que morava no topo (o «de N no acervo») e nos chips do balcão,
        /// reordenado como uma visita de verdade termina. Ver o porquê inteiro
        /// na [NotaDoCaixa].
        estado.prateleira?.let { prateleira ->
            NotaDoCaixa(
                prateleira = prateleira,
                noAcervo = estado.loja?.noAcervo ?: 0,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 20.dp, bottom = 8.dp),
            )
        }
    }
}

/// Uma seção da locadora: rótulo, as caixas, e **a tábua** por baixo.
///
/// ## A tábua é a leva 2, e ela conserta o que a leva 3 deixou pela metade
///
/// A caixa ganhou lombada, pose e verniz — e ficou de pé **no nada**. Não havia
/// prateleira: as caixas flutuavam sobre o preto, o que desfaz metade do
/// trabalho de fazê-las parecerem objetos. Objeto que não repousa em nada é
/// desenho, não coisa.
///
/// A web sabe disso e o comentário dela diz por quê:
///
/// > «A tábua encosta na base das caixas. Com folga embaixo o conjunto lê como
/// > cartão, não como objeto.»
///
/// Por isso a madeira encosta — sem vão, sem margem. E ela **sangra pras
/// laterais** (os −16dp) porque prateleira de loja não acaba onde acaba a
/// fileira: ela atravessa a parede.
///
/// ## O halo é a luz da loja batendo na madeira
///
/// Um dourado difuso subindo da tábua, a 55% e desfocado. Sem ele a madeira é
/// uma tarja marrom; com ele, ela é uma superfície **iluminada** — e é o mesmo
/// argumento do arquivo `Luz.kt`, aplicado a um lugar em vez de a um objeto.
/// Uma estante da vitrine: placa, caixas e tábua.
///
/// ## A placa é serifada e **acesa**, e isso é da folha
///
/// `.placa span` (`styles.css:4105`): 24px em `--font-display`, `--accent`, com
/// `text-shadow: 0 0 24px` a 42%. Ou seja — o nome da estante não é um rótulo,
/// é um **letreiro aceso** pendurado sobre a prateleira. É o dourado como luz na
/// forma mais literal que a web tem.
///
/// Compose não tem `text-shadow` colorido difuso como o CSS, então o halo vem
/// pelo `shadow` do `TextStyle`: mesma ideia, e o borrão de 24px vira `blurRadius`.
///
/// ## «16 de 113» é a promessa da vitrine
///
/// `total` é quantas caixas a estante tem **no acervo**, não quantas estão à
/// vista. O comentário da web insiste nisso, e é o que impede a placa de mentir:
/// a vitrine é uma amostra que gira, não o estoque.
/// O palco fica **por cima da tela inteira**, e fora da rolagem.
///
/// Dentro da `Column` que rola, a caixa na mão subiria e desceria com a
/// prateleira — e o que se quer é o contrário: o resto da loja para, e sobra o
/// objeto. É o mesmo arranjo do véu do `AppOdeon`.
@Composable
private fun PalcoPorCima(
    modelo: ModeloDaLocadora,
    estado: EstadoDaLocadora,
    aoAbrirObra: (String) -> Unit,
    aoTocarDoMenu: (dev.odeon.android.dados.MenuDoDisco, Double) -> Unit,
    aoTocarOFilme: (obraId: String, arquivoId: String, titulo: String, de: Double, duracao: Double?) -> Unit,
) {
    /// O menu do disco vem **por cima do palco**: quem está no menu já tirou o
    /// disco da caixa, e voltar é fechar o menu, não a caixa.
    estado.menu?.let { menu ->
        androidx.activity.compose.BackHandler(onBack = modelo::fecharOMenu)
        MenuDeDVD(
            disco = menu,
            cenas = estado.cenas,
            arte = modelo::arte,
            aoTocar = { segundos -> aoTocarDoMenu(menu, segundos) },
            aoFechar = modelo::fecharOMenu,
        )
        return
    }

    val naMao = estado.naMao ?: return
    androidx.activity.compose.BackHandler(onBack = modelo::guardar)
    Palco(
        caixa = naMao,
        arte = modelo.arte(naMao.poster),
        fita = estado.fita,
        obra = estado.obraNaMao,
        ehVhs = estado.ehVhs(naMao.ano),
        arteDe = modelo::arte,
        rebobinando = estado.rebobinando,
        aoFechar = modelo::guardar,
        /// ## Assistir **é assistir** — não é abrir a ficha
        ///
        /// O botão do verso e a fita rebobinada caem aqui, e daqui vai pro
        /// player. A ficha era o caminho da versão anterior e o dono cortou:
        /// «clicar em play tem que cair no filme, não nos detalhes».
        ///
        /// E o verso já é a ficha, aliás: sinopse, cena e ficha técnica estão
        /// impressos na própria caixa que a pessoa está segurando.
        ///
        /// ⚠️ Sem arquivo, **nada acontece** — e o botão nem nasce, porque a
        /// `Contracapa` só o desenha com `files` na mão (§53).
        aoAssistir = {
            val obra = estado.obraNaMao
            val arquivo = obra?.files?.firstOrNull()
            if (obra != null && arquivo != null) {
                modelo.guardar()
                aoTocarOFilme(
                    obra.id,
                    arquivo.id,
                    obra.title,
                    obra.ondeParou,
                    arquivo.duracaoEmSegundos ?: obra.duracaoEmSegundos,
                )
            }
        },
        /// O disco abre o menu — §14.4, «só pela locadora, e só em DVD». Quando
        /// o menu não vem, cai na ficha: um caminho que às vezes não leva a
        /// lugar nenhum é o §8b.
        aoAbrirOMenu = {
            modelo.abrirOMenu(naMao.id) {
                modelo.guardar()
                aoAbrirObra(naMao.id)
            }
        },
        aoRebobinar = modelo::rebobinar,
        /// Os corpos do **celular**. Ver [LetraDoPalco]: eles não têm padrão de
        /// propósito, pra que o `:tv` seja obrigado a escolher os dele em vez de
        /// herdar 16sp e ninguém reparar.
        ///
        /// São os mesmos quatro slots que estavam escritos dentro do `Palco`
        /// antes de ele mudar de módulo — nenhum corpo mudou de tamanho aqui.
        letra = LetraDoPalco(
            titulo = MaterialTheme.typography.titleMedium,
            dica = Tipo.pilula,
            ponteiro = MaterialTheme.typography.headlineSmall,
            aviso = MaterialTheme.typography.bodyMedium,
            botao = MaterialTheme.typography.labelLarge,
        ),
    )
}

@Composable
private fun Estante(
    estante: EstanteExposta,
    arte: (String?) -> String?,
    /// O corte fita × disco, vindo do estado — é o `ultimo_ano_vhs` do servidor,
    /// e é ele que decide o **formato da caixa** em cada vão da prateleira.
    ehVhs: (Int?) -> Boolean,
    /// A pose da vitrine — repouso somado à inclinação do aparelho, lida uma vez
    /// pela tela e compartilhada por todas as estantes.
    pose: Pose,
    /// O prazo real da casa (`opcoes.prazo_dias`), pra etiqueta colorida da
    /// tábua. Zero — servidor sem a regra — não vira etiqueta (§24).
    prazoEmDias: Int,
    /// Qual estante desta é na loja — decide a cor do papel da plaquinha e da
    /// etiqueta, que ciclam numa paleta fixa.
    indice: Int,
    aoAbrir: (String) -> Unit,
) {
    if (estante.caixas.isEmpty()) return

    /// ## A estante virou móvel — «A loja da esquina, 21h»
    ///
    /// Era um rótulo dourado flutuando sobre caixas soltas com uma tábua de 6dp
    /// por baixo. No desenho aprovado ela é um **móvel de madeira**: o fundo com
    /// veio, a plaquinha de papel escrita à mão presa com fita crepe, a tábua
    /// grossa com a etiqueta de prazo. O nome em neon dourado saiu — numa loja
    /// de verdade quem nomeia a estante é um papel, não um letreiro por seção.
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                /// O respiro pro papel da plaquinha morder o topo da madeira.
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF6B4E32), Color(0xFF5A4028), Color(0xFF44311E)),
                    ),
                )
                .drawBehind {
                    /// O veio da madeira: riscos verticais quase invisíveis. É o
                    /// mesmo truque da fibra da corda — textura por repetição,
                    /// não por imagem.
                    var x = 8.dp.toPx()
                    while (x < size.width) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.07f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                        x += 14.dp.toPx()
                    }
                },
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 22.dp),
            ) {
                itemsIndexed(estante.caixas, key = { _, c -> c.id }) { i, caixa ->
                    CaixaNaEstante(
                        caixa = caixa,
                        arte = arte(caixa.poster),
                        indice = i,
                        ehVhs = ehVhs(caixa.ano),
                        pose = pose,
                        aoAbrir = { aoAbrir(caixa.id) },
                    )
                }
            }

            /// A tábua grossa: o tampo que pega luz e a testa na sombra — a
            /// `Tabua` fina de 6dp continua existindo só na estante vazia.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF7A5A38), Color(0xFF4A3520)),
                        ),
                    ),
            ) {
                if (prazoEmDias > 0) {
                    EtiquetaDePrazo(
                        prazoEmDias = prazoEmDias,
                        indice = indice,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 14.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Color(0xFF2E2114)),
            )
        }

        PlaquinhaDaEstante(
            nome = estante.nome,
            indice = indice,
            modifier = Modifier.padding(start = 14.dp),
        )

        /// A contagem continua sendo dita — agora discreta, no canto da madeira,
        /// onde o lojista penduraria o inventário.
        Text(
            text = "${estante.caixas.size} de ${estante.total}",
            style = Tipo.rotulo.copy(letterSpacing = 0.14.em),
            color = Cores.destaqueApagado,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = 12.dp),
        )
    }
}

/// Uma caixa **na** estante — de pé, na mesma pose das que saíram.
///
/// Ela não vira: o verso de uma caixa emprestada tem quem levou e o prazo, e uma
/// que está na estante não tem nada disso pra mostrar. Tocar abre a ficha, que é
/// onde se pega a fita.
@Composable
private fun CaixaNaEstante(
    caixa: CaixaExposta,
    arte: String?,
    indice: Int,
    ehVhs: Boolean,
    pose: Pose,
    aoAbrir: () -> Unit,
) {
    /// ## Ela virou um objeto de verdade — 05/08/2026
    ///
    /// Até aqui eram duas `graphicsLayer` encostadas à mão, cada uma com a
    /// própria câmera. Agora é o `CaixaEm3D`, com uma projeção só — e o ganho
    /// não é só a junta fechar: é o **topo** existir, que é o lado que prova
    /// espessura, e a luz mudar de face conforme o ângulo.
    ///
    /// ⚠️ **Na estante ela não gira com o dedo, e é decisão.** A fileira rola na
    /// horizontal, e um arrasto que começasse na caixa seria roubado dela — a
    /// prateleira deixaria de rolar justamente onde há caixa, que é o lugar todo.
    /// O giro no dedo é do **palco**, onde a caixa está sozinha e o arrasto não
    /// disputa com nada. O que a estante ganhou foi a **inclinação do aparelho**
    /// — a `pose` que chega de fora —, que dá o movimento sem tomar o gesto.
    ///
    /// ## As medidas são as dos objetos, e a prateleira mistura os dois
    ///
    /// Era uma caixa genérica de 96×144×26 pra tudo — e uma locadora de verdade
    /// não tem caixa genérica. Um keep case de DVD mede 135×190×14mm; um estojo
    /// de VHS, 103×187×25mm. Na mesma altura de prateleira (144dp), isso dá:
    ///
    /// | | largura | espessura |
    /// |---|---|---|
    /// | DVD | 144 ÷ (190/135) ≈ **102dp** | 102 × 0,104 ≈ **11dp** |
    /// | VHS | 144 ÷ (187/103) ≈ **79dp** | 79 × 0,243 ≈ **19dp** |
    ///
    /// A fileira com fitas gordas e estreitas entre discos finos e largos é o
    /// que faz a prateleira parecer acervo, e não grade de miniaturas — e o
    /// corte é o mesmo `ultimo_ano_vhs` que já decide o rebobinar.
    Column(
        modifier = Modifier.chega(indice),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CaixaEm3D(
            largura = if (ehVhs) 79.dp else 102.dp,
            altura = 144.dp,
            espessura = if (ehVhs) 19.dp else 11.dp,
            poseControlada = pose,
            aoTocar = aoAbrir,
        ) { lado, luz, poseDoQuadro ->
            FaceDaCaixa(
                lado = lado,
                luz = luz,
                pose = poseDoQuadro,
                titulo = caixa.titulo,
                arte = arte,
                cor = corDeHex(caixa.corDominante),
                ehVhs = ehVhs,
                ano = caixa.ano,
                id = caixa.id,
                /// ⚠️ **`serie` e `temporadas` têm que valer os dois**, e é a
                /// web que corta assim. Uma obra solta pode vir com
                /// `temporadas` preenchido por engano do lado de lá, e «1
                /// TEMPORADA» estampado num filme é o §18 impresso na capa.
                temporadas = if (caixa.serie) caixa.temporadas else 0,
            )
        }
    }
}

@Composable
private fun Secao(titulo: String, quantos: Int? = null, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RotuloDeSecao(texto = titulo, numero = quantos)
        conteudo()
        Tabua()
    }
}

/// A loja com nada emprestado — e **ela continua sendo uma loja**.
///
/// ## A tela dizia uma coisa e desenhava outra
///
/// A frase é «nenhuma caixa fora da estante», que é uma afirmação **positiva**:
/// está tudo guardado. E o desenho era um retângulo preto de 1.400 pixels com
/// uma linha cinza no topo. Copy e desenho se contradiziam: um dizia "está tudo
/// no lugar", o outro dizia "não há nada aqui".
///
/// Duas tábuas vazias com a frase entre elas resolvem isso sem inventar dado: é
/// a **mobília da loja**, que existe independentemente de haver caixa fora. O
/// que se vê é uma prateleira de empréstimo vazia, que é exatamente o que é.
///
/// ⚠️ **Não desenho caixas cheias**, e a tentação existia — uma estante lotada
/// leria muito melhor. Seria §18 na forma mais direta: o app não sabe o que está
/// na estante. Esta tela só conhece o que **saiu** dela; o acervo inteiro é da
/// biblioteca. Desenhar caixas aqui seria afirmar um estoque que ninguém contou.
@Composable
private fun EstanteVazia(comTabuas: Boolean) {
    if (!comTabuas) {
        Text(
            text = "nenhuma caixa fora da estante",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.textoApagado,
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        /// A altura da fileira que **estaria** aqui, vazia. Sem ela as duas
        /// tábuas ficariam coladas e leriam como duas linhas, não como estante.
        Box(Modifier.fillMaxWidth().height(96.dp))
        Tabua()
        Text(
            text = "nenhuma caixa fora da estante",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.textoApagado,
            modifier = Modifier.padding(vertical = 20.dp),
        )
        Box(Modifier.fillMaxWidth().height(96.dp))
        Tabua()
    }
}

/// Uma caixa de fita, **de pé e em três quartos** — R5.
///
/// ## Ela não fica de frente, e isso é o item inteiro
///
/// A web nunca desenha a caixa chapada: ela repousa em `rotateX(3deg)
/// rotateY(22deg)` (`styles.css:4256`) e é essa pose que mostra a **lombada**.
/// Um retângulo de frente é uma capa; um retângulo girado com uma faixa escura
/// na lateral é uma caixa numa prateleira. A §1.3 do redesenho chama isso de «a
/// coisa que o Odeon é e o app ainda não».
///
/// As medidas saem da folha, não do olho:
///
/// | | web (`.caixa.vhs`) | aqui |
/// |---|---|---|
/// | largura × altura | 104 × 200 | 140dp × 210dp (2:3) |
/// | **espessura** | **28px** — 27% da largura | 38dp, a mesma proporção |
/// | pose | `rotateY(22deg)` | 22° |
///
/// ## O que **não** dá pra copiar, e o substituto
///
/// A web compõe as cinco faces em `preserve-3d`, num espaço 3D compartilhado. O
/// Compose não tem isso — a §3 da espec já registrava —, então cada face aqui é
/// uma camada com transformação própria, e as duas são **encostadas por conta**:
/// a lombada é posicionada à esquerda da capa e girada sobre a própria aresta
/// direita, de modo que as arestas coincidam na pose escolhida.
///
/// É aproximação, e ela tem um limite honesto: como as camadas não dividem o
/// mesmo ponto de fuga, a junta só fecha **na pose de repouso**. Por isso a pose
/// é fixa e não acompanha o dedo — animar o ângulo abriria a junta no meio do
/// caminho, e uma caixa com fresta é pior que uma caixa chapada.
///
/// ## O verniz
///
/// A faixa diagonal clara sobre a capa é o `.brilho` (`:4385`), e o comentário
/// da folha diz o que ela faz: «é o que faz o olho ler objeto em vez de
/// imagem». Custa um gradiente e é o item de melhor retorno desta fase.
///
/// ⚠️ Ela é decoração e **não pode parecer dado** (§18). Por isso é branco a
/// 24% sobre a arte, na diagonal, e não uma faixa colorida na horizontal — que
/// é a forma que a barra de progresso tem em toda outra tela deste app.
@Composable
private fun Caixa(
    fita: Emprestada,
    arte: String?,
    aoDevolver: (() -> Unit)?,
    devolvendo: Boolean,
    indice: Int = 0,
    /// Nulo nas minhas: não se pede de volta o que já está com você.
    aoPedirDeVolta: (() -> Unit)? = null,
    pedindo: Boolean = false,
    /// O corte entre fita e disco, vindo do `ultimo_ano_vhs` do servidor.
    ehVhs: Boolean = false,
    /// Tocar **abre o palco** em vez de virar o cartão — é o caminho das
    /// «comigo», onde o toque tem que levar ao filme («ao clicar só mostra
    /// devolver e nem abre o 3D», 07/08). Nulo nas dos outros, onde não há
    /// filme a tocar e o verso do cartão continua sendo a resposta do toque.
    aoAbrir: (() -> Unit)? = null,
) {
    var virada by remember { mutableStateOf(false) }
    val giro by animateFloatAsState(
        targetValue = if (virada) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "giro da caixa",
    )
    val haptico = LocalHapticFeedback.current

    /// A espessura da caixa, na proporção da web: 28 de 104 é 27%.
    val espessura = 38.dp

    /// ## Arrastar pra baixo devolve a fita — R8
    ///
    /// É o gesto que fecha a metáfora: a caixa **volta pra estante**, e a mão
    /// faz o movimento em vez de procurar um botão.
    ///
    /// ### ⚠️ Ele só existe nas minhas
    ///
    /// `aoDevolver` é nulo nas caixas dos outros, e aí nem o detector é montado.
    /// Um arrasto que devolvesse a fita de alguém seria o §11 na pior versão —
    /// e "sem querer" não é desculpa quando o dado é de outra pessoa.
    ///
    /// ### O limite é alto de propósito, e a tela avisa antes
    ///
    /// 96dp é quase metade da altura da caixa. Um limite curto transformaria
    /// qualquer rolagem mal-agarrada numa devolução — e devolver **escreve no
    /// acervo de três pessoas**, então o gesto tem que ser deliberado.
    ///
    /// E ele não é secreto: a caixa acompanha o dedo, e passado o limite aparece
    /// a frase "solte pra devolver". Gesto escondido que faz coisa grave é o §8b
    /// ao contrário — errar em silêncio é ruim, mas acertar em silêncio numa
    /// ação destrutiva é pior.
    ///
    /// ### ✅ Visto rodando, e o primeiro teste enganou
    ///
    /// Verificado em 04/08/2026 de ponta a ponta: a caixa desce com o dedo,
    /// desbota, o rótulo vira "solte pra devolver", e soltar devolve — a fita
    /// some da prateleira e o limite volta a subir. Feito duas vezes.
    ///
    /// ⚠️ **A primeira tentativa disse que não funcionava, e estava errada.** Um
    /// `adb input swipe` de 900ms não dispara nada; com 1600ms dispara sempre. O
    /// arrasto precisa vencer o `touchSlop` **e** andar 96dp, e rápido demais os
    /// dois chegam numa rajada de eventos que o detector lê como um só. É limite
    /// da ferramenta de teste, não do gesto — e vale anotar porque o sintoma é
    /// idêntico ao de um gesto quebrado.
    ///
    /// ### O que ele custa, e vale dizer
    ///
    /// A tela rola na vertical, e um arrasto vertical que começa **na caixa**
    /// passa a ser dela. Ou seja: não dá pra rolar a página agarrando uma caixa
    /// minha — tem que agarrar o resto da tela. É o preço do gesto, e é o mesmo
    /// que qualquer app de lista com deslizar-pra-apagar paga.
    var arrasto by remember { mutableFloatStateOf(0f) }
    val limiteDeDevolucao = with(LocalDensity.current) { 96.dp.toPx() }
    val passouDoLimite = arrasto > limiteDeDevolucao
    val descida by animateFloatAsState(
        targetValue = arrasto,
        animationSpec = spring(),
        label = "descida da caixa",
    )

    Column(
        /// A caixa **cai** na prateleira ao chegar, escalonada — ver `Chegada`.
        modifier = Modifier.chega(indice).width(140.dp + espessura),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(140.dp + espessura)
                .aspectRatio((140f + 38f) / 210f)
                /// A caixa desce com o dedo e desbota — o desbotar é o que diz
                /// "isto está saindo daqui" sem escrever nada.
                .graphicsLayer {
                    translationY = descida
                    alpha = 1f - (descida / (limiteDeDevolucao * 2f)).coerceIn(0f, 0.45f)
                }
                .then(
                    if (aoDevolver == null || devolvendo) {
                        Modifier
                    } else {
                        Modifier.pointerInput(fita.id) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (arrasto > limiteDeDevolucao) {
                                        /// A batida da R5 no fim do gesto —
                                        /// a mesma de tocar em "devolver".
                                        haptico.performHapticFeedback(
                                            HapticFeedbackType.LongPress,
                                        )
                                        aoDevolver()
                                    }
                                    arrasto = 0f
                                },
                                onDragCancel = { arrasto = 0f },
                                onVerticalDrag = { _, delta ->
                                    /// Só pra baixo: arrastar pra cima não
                                    /// desdevolve nada, então não move nada.
                                    arrasto = (arrasto + delta).coerceAtLeast(0f)
                                },
                            )
                        }
                    },
                )
                /// ⚠️ **O giro é do objeto inteiro, e mora aqui — não na capa.**
                ///
                /// A primeira versão punha o `giro` na `rotationY` da capa, que
                /// já girava −22° sobre a **aresta esquerda** pra encostar na
                /// lombada. O screenshot mostrou o resultado: virar a caixa a
                /// espremia numa tira escura de dois dedos. Girar 158° em torno
                /// da aresta esquerda não vira o objeto — **joga ele pra fora**,
                /// como uma porta abrindo.
                ///
                /// Aqui a origem é o centro (o padrão), então a caixa gira sobre
                /// o próprio eixo. A pose de −22° da capa fica **dentro** desta
                /// camada e é somada a ela, que é o que o `preserve-3d` da web
                /// faz de graça e o Compose faz por aninhamento.
                .graphicsLayer {
                    rotationY = giro
                    cameraDistance = 12f * density
                }
                .clickable {
                    /// Girar a caixa é mexer num objeto, e a mão avisa.
                    ///
                    /// `TextHandleMove` é o mais **seco** dos dois tipos que o
                    /// Compose expõe — um tique, não uma batida. Virar a caixa é
                    /// gesto leve; a batida fica pra devolver, que é o que muda
                    /// o acervo de todo mundo.
                    haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (aoAbrir != null) aoAbrir() else virada = !virada
                },
        ) {
            if (giro <= 90f) {
                /// A lombada.
                ///
                /// Girada sobre a **própria aresta direita** (`TransformOrigin(1f,
                /// .5f)`) pra encostar na aresta esquerda da capa. O ângulo é
                /// 90° − 22° = 68°: a capa está a 22° do plano da tela, e a
                /// lombada é perpendicular a ela.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(espessura)
                        .fillMaxHeight()
                        .graphicsLayer {
                            rotationY = 68f
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            cameraDistance = 12f * density
                        }
                        .background(
                            /// Escurece do meio pras bordas: é a curvatura do
                            /// plástico pegando luz de cima. Chapada, a lombada
                            /// lê como um retângulo colado ao lado da capa.
                            Brush.horizontalGradient(
                                listOf(
                                    Cores.fundoAfundado,
                                    Cores.fundoElevado,
                                    Cores.fundoAfundado,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    /// O título na vertical, como em toda lombada de fita.
                    ///
                    /// `rotationZ` e não uma fonte vertical: o Compose não tem
                    /// `writing-mode`. O texto é medido deitado e girado depois,
                    /// então a largura dele vira a altura da lombada.
                    ///
                    /// ⚠️ `requiredWidth` e **não** `width`, e o screenshot é que
                    /// denunciou: com `width(190.dp)` o título saía `007 C…`.
                    /// `graphicsLayer` gira o que já foi desenhado e não mexe em
                    /// medição — então o texto era medido dentro dos 38dp da
                    /// lombada, cortava ali, e só depois girava. O `required` é o
                    /// que ignora a restrição do pai; sem ele toda lombada
                    /// mostraria três letras.
                    Text(
                        text = fita.titulo,
                        style = MaterialTheme.typography.labelSmall,
                        color = Cores.textoApagado,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .requiredWidth(190.dp)
                            .graphicsLayer { rotationZ = 90f },
                    )
                }

                /// A capa, girada 22° sobre a aresta esquerda — o mesmo eixo em
                /// que a lombada encosta.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(140.dp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            rotationY = -22f
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            /// Sem isto a rotação é ortográfica e a caixa parece
                            /// achatar em vez de girar. O número é distância de
                            /// câmera em múltiplos da densidade — 12 dá
                            /// perspectiva sem a deformação de grande-angular.
                            cameraDistance = 12f * density
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(Cores.fundoElevado),
                    contentAlignment = Alignment.Center,
                ) {
                    if (arte != null) {
                        AsyncImage(
                            model = arte,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = fita.titulo,
                            style = MaterialTheme.typography.labelMedium,
                            color = Cores.texto,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp),
                        )
                    }

                    /// O verniz — o `.brilho` da web (`styles.css:4385`). Só
                    /// sobre a frente: o verso de uma caixa de fita é papel, não
                    /// plástico.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    0.00f to Color.White.copy(alpha = 0.24f),
                                    0.14f to Color.White.copy(alpha = 0.05f),
                                    0.32f to Color.Transparent,
                                    0.74f to Color.Transparent,
                                    1.00f to Color.White.copy(alpha = 0.10f),
                                ),
                            ),
                    )
                }
            } else {
                /// O verso — a etiqueta.
                ///
                /// Ele ocupa a caixa inteira, lombada incluída: virada, a caixa
                /// mostra o papel de trás e não há mais lateral pra ver deste
                /// lado. E leva `rotationY = 180f` pra desespelhar — sem isso o
                /// texto sairia invertido, porque o pai já girou meia-volta.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .clip(RoundedCornerShape(4.dp))
                        .background(Cores.fundoElevado),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = fita.titulo,
                            style = MaterialTheme.typography.labelMedium,
                            color = Cores.texto,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "com ${fita.quemNome}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Cores.textoApagado,
                        )

                        /// O prazo — e ele **chegava e ninguém desenhava**.
                        ///
                        /// `vence_em` está na resposta desde que esta tela
                        /// existe, e o §8 deste documento listava o campo como
                        /// dívida. Sem ele, uma caixa emprestada não dizia
                        /// quando volta — que é a única coisa que um prazo tem
                        /// pra dizer.
                        ///
                        /// Vermelho a dois dias, como na web: `vence hoje` e
                        /// `vence amanhã` são aviso, `5 dias` é informação.
                        prazoDoEmprestimo(fita.venceEm)?.let { (frase, dias) ->
                            Text(
                                text = frase,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (dias <= 1) Cores.perigo else Cores.textoApagado,
                            )
                        }

                        fita.pedidoPorNome?.let {
                            Text(
                                text = "$it pediu de volta",
                                style = MaterialTheme.typography.labelSmall,
                                color = Cores.destaque,
                            )
                        }
                        /// Pedir de volta — só nas dos outros, e some quando
                        /// alguém já pediu.
                        ///
                        /// ⚠️ Ele **não encurta o prazo de ninguém**. O que faz
                        /// é pôr o recado que aparece logo acima, na caixa de
                        /// quem está com ela. Oferecer o botão duas vezes seria
                        /// oferecer mandar o mesmo recado de novo (§53).
                        if (aoPedirDeVolta != null && fita.pedidoPorNome == null) {
                            TextButton(
                                onClick = aoPedirDeVolta,
                                enabled = !pedindo,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = if (pedindo) "pedindo…" else "pedir de volta",
                                    color = Cores.destaque,
                                )
                            }
                        }

                        if (aoDevolver != null) {
                            TextButton(
                                onClick = {
                                    /// A batida de devolver.
                                    ///
                                    /// `LongPress` é o mais **encorpado** dos
                                    /// dois tipos que o Compose expõe —
                                    /// devolver escreve no acervo de três
                                    /// pessoas, e a mão sente a diferença entre
                                    /// isto e virar a caixa, que leva o tique
                                    /// seco.
                                    haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                                    aoDevolver()
                                },
                                enabled = !devolvendo,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = if (devolvendo) "devolvendo…" else "devolver",
                                    color = Cores.destaque,
                                )
                            }
                        }
                    }
                }
            }
        }

        /// A frase só aparece depois do limite, e é o aviso antes do fato.
        if (passouDoLimite) {
            Text(
                text = "solte pra devolver",
                style = MaterialTheme.typography.labelSmall,
                color = Cores.destaque,
                maxLines = 1,
            )
        } else {
            Text(
                text = fita.titulo,
                style = MaterialTheme.typography.bodySmall,
                color = Cores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/// A placa da porta virou **etiquetas penduradas** — ver a entrada da [Loja] e
/// a [EtiquetaPendurada]. A frase única continua sendo montada aqui embaixo,
/// porque semântica e teste falam gramática, não desenho.
/// «37 caixas na prateleira, 3 fora · 40 nesta semana, de 600 no acervo».
///
/// ## As três contagens são coisas diferentes, e é por isso que são três
///
/// | | |
/// |---|---|
/// | **na prateleira** | o que dá pra pegar **agora** |
/// | **nesta semana** | o que a vitrine sorteou, antes de alguém levar |
/// | **no acervo** | o que a loja tem, e a vitrine é uma amostra dele |
///
/// Só a primeira faria a pessoa concluir que a locadora tem 37 filmes. Só a
/// última esconderia a loja de hoje atrás do estoque inteiro. É o mesmo §14 do
/// «Biblioteca 300» — número sem denominador vira afirmação errada.
///
/// ## O buraco é **dito**, e não deduzido
///
/// O `, 3 fora` existe porque a caixa alugada sumiu da fileira (ver
/// `EstadoDaLocadora.expostas`). Sem esta frase, quem viu 40 ontem e vê 37 hoje
/// conclui que a loja quebrou — o vão precisa de nome, senão lê como defeito em
/// vez de escassez.
///
/// E ele só aparece quando existe: `, 0 fora` é ruído, e §24.
///
/// ## ⚠️ `no_acervo` vem do servidor, e **não é a soma das estantes**
///
/// A tentação é somar os `total` das placas, e dá outro número: uma estante que
/// não recebeu caixa no sorteio **não vem na resposta**, e o acervo dela some
/// junto.
///
/// **Medido no servidor de casa em 05/08/2026**, nas dez estantes desta semana
/// (145 + 16 + 26 + 125 + 49 + 179 + 112 + 62 + 89 + 10): a soma dá **813**, e o
/// `no_acervo` que o servidor manda é **850**. Somar as placas perderia 37
/// caixas — as das estantes que a semana não sorteou. A web aprendeu o mesmo por
/// foto, quando a porta disse «597 no acervo» de 600 na semana em que o faroeste
/// ficou de fora.
///
/// Quando o servidor não manda o número, a oração inteira some (§24) — «de 0 no
/// acervo» seria uma loja vazia com quarenta caixas na tela.
///
/// ## A concordância é nossa, e a web não tem
///
/// Lá está escrito `${total} caixas na prateleira`, e com uma caixa sai «1
/// caixas». Em português isso é erro de leitura, não economia de código — a
/// mesma razão que fez o `N temporada/temporadas` existir três linhas adiante.
internal fun portaDaLoja(naPrateleira: Int, sorteadas: Int, noAcervo: Int): String {
    /// Os dois vazios dizem coisas **opostas**, e trocá-los é mentir.
    ///
    /// Prateleira vazia com sorteio cheio é uma loja funcionando cujo estoque
    /// saiu inteiro — notícia boa, quase. Sorteio vazio é a vitrine não ter
    /// nascido: nenhuma obra com capa entrou nela.
    if (naPrateleira == 0) {
        return if (sorteadas > 0) "a prateleira está vazia — está tudo emprestado" else "nada com capa por aqui"
    }
    return buildString {
        append("$naPrateleira ${if (naPrateleira == 1) "caixa" else "caixas"} na prateleira")
        val fora = sorteadas - naPrateleira
        if (fora > 0) append(", $fora fora")
        append(" · $sorteadas nesta semana")
        if (noAcervo > 0) append(", de $noAcervo no acervo")
    }
}

/// ⚠️ A `regras()` morava aqui e **foi apagada em 05/08/2026**, com a linha que
/// ela montava. O porquê está escrito no lugar onde a linha era desenhada, na
/// `Loja` — junto do mapa de onde cada uma das três informações continua sendo
/// dita.
///
/// Fica o registro de que ela existiu, e não um corpo comentado: código morto
/// guardado «por via das dúvidas» é a próxima pessoa lendo duas versões da mesma
/// regra e não sabendo qual vale. O git guarda melhor.
