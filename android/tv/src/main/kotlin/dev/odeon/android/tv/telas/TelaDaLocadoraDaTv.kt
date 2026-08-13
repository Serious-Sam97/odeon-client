package dev.odeon.android.tv.telas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.Cartaz
import dev.odeon.android.tv.ui.FileiraFantasma
import dev.odeon.android.tv.ui.Pilula
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.RotuloDeSecao
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.locadora.ModeloDaLocadora
import dev.odeon.android.ui.prazoDoEmprestimo
import dev.odeon.android.ui.viraQuando
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.odeon.android.tv.ui.TipoDaSala
import dev.odeon.android.ui.Serifada
import dev.odeon.android.ui.locadora.Arandela
import dev.odeon.android.ui.locadora.EtiquetaPendurada
import androidx.compose.foundation.lazy.itemsIndexed
import dev.odeon.android.ui.locadora.PlaquinhaDaEstante
import dev.odeon.android.ui.locadora.Tabua
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/// A locadora, na sala.
///
/// ## O que ela **não** traz do celular, e por quê
///
/// O `:app` desenha a locadora como um lugar: a caixa em três quartos com
/// lombada e verniz (R5), o palco onde a fita vai parar na mão, o balcão, o
/// menu de DVD, o háptico com dois pesos ao pegar. É a tela mais trabalhada do
/// app de celular.
///
/// Quase nada disso atravessa, e não é preguiça — é que **as duas coisas que
/// sustentam aquela tela não existem aqui**:
///
/// | | celular | sala |
/// |---|---|---|
/// | pegar a caixa | o dedo a levanta, e o aparelho vibra na mão | não há mão, não há vibração |
/// | girar a caixa | o objeto responde à inclinação do aparelho | a TV não se inclina |
///
/// ## ⚠️ Os dois parágrafos abaixo estavam errados, e o aparelho provou — 12/08/2026
///
/// Eles diziam: «A caixa em três quartos existe pra ser **pegada**. Numa TV ela
/// vira um desenho bonito de uma coisa que ninguém toca — e o esforço de três
/// quartos, lombada e verniz vai todo pra um efeito que a distância de três
/// metros achata.»
///
/// Duas afirmações, as duas falsas, e cada uma falsa por um motivo diferente:
///
/// | o que eu disse | o que é |
/// |---|---|
/// | a caixa **não atravessa** de código | atravessa inteira. O argumento era sobre afordância de toque e escorregou pra uma conclusão sobre módulo. O dedo não atravessa; o objeto sim. Ver §3 do `docs/REDESENHO-TV.md` |
/// | a três metros ela **achata** | não achata. A `CaixaEm3D` foi posta nesta prateleira em tamanho de sala e fotografada na TCL: a lombada ficou **mais** legível que no celular — título, tarja, miniatura e `2024 · DVD` |
///
/// A segunda é a que dói mais, porque nunca tinha sido **vista**. Era palpite
/// escrito com voz de medida, que é o defeito que este projeto mais paga — e o
/// `README.md` já tem uma seção inteira sobre ele.
///
/// ## ⚠️ Mas a prateleira voltou ao cartaz plano assim mesmo, e por um número
///
/// A caixa é bonita a três metros **e cara**. Medido no mesmo dia, mesmo gesto
/// dos dois lados, `dumpsys gfxinfo` zerado antes:
///
/// | | jank | 50º percentil |
/// |---|---|---|
/// | esta tela, cartaz plano | 44,7% | **42ms** |
/// | esta tela, caixa 3D | 100% | **200ms** |
///
/// 200ms é 5 fps. E `Slow bitmap uploads` deu **zero** — não é o pôster
/// carregando, é composição: seis faces por caixa, cada uma com um
/// `BoxWithConstraints`, vezes quatro caixas na tela.
///
/// ⚠️ **Isso não é argumento contra compartilhar.** Uma cópia da caixa dentro
/// deste módulo seria exatamente igual de lenta — o custo é da peça, não da
/// fronteira. Ver a §10.1 do `docs/REDESENHO-TV.md`, que registra o número e os
/// três caminhos possíveis.
///
/// A §5.2 põe a caixa 3D **no palco** — uma, no centro, quando se escolhe — e é
/// lá que a T3 vai medi-la de novo. Uma caixa não é oito.
///
/// O que atravessa **hoje** é o que é da locadora e não do objeto: as estantes
/// com a vitrine que gira, o prazo de quem pegou, quem está com o quê, e pedir
/// de volta. A loja continua sendo uma loja; a fita, por ora, volta a ser um
/// cartaz — e agora por um motivo medido, não por um palpite.
@Composable
fun TelaDaLocadoraDaTv(
    modelo: ModeloDaLocadora,
    aoAbrirObra: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    /// ## ⚠️ A rolagem da loja mora **fora** do `if` do palco — defeito relatado
    ///
    /// > «ao entrar na capa 3d tu aperta voltar e ele volta pro começo e abre o
    /// > menu lateral, deveria só guardar a capa mas continuar no filme que tu
    /// > tava»
    ///
    /// Duas coisas aconteciam, e as duas por causa do `return` que desenha o
    /// palco: ele **descarta** a `LazyColumn` inteira. Com ela vai a posição de
    /// rolagem — e vai também o último nó focado, então o foco caía no primeiro
    /// alvo da tela, que é o trilho. Daí «volta pro começo **e** abre o menu».
    ///
    /// Guardar o estado aqui em cima resolve o primeiro: o `rememberLazyListState`
    /// pertence à tela, e a tela não sai de composição quando a caixa é pega. O
    /// segundo se resolve com o `focoDaEstante` logo abaixo.
    val rolagem = rememberLazyListState()

    /// Onde o foco volta depois de guardar a caixa. É a mesma ideia do
    /// `saidaEsquerda` das fileiras: um alvo nomeado, porque «o foco volta
    /// sozinho» não é verdade quando o nó que o tinha deixou de existir.
    val focoDaEstante = remember { FocusRequester() }

    /// ⚠️ **Qual caixa foi pega, lembrado à parte** — e a primeira versão errou
    /// isto de um jeito que só o aparelho mostrou.
    ///
    /// Eu apontava o foco de volta pro «primeiro cartaz da primeira estante»
    /// quando `naMao` virava `null`. Mas nesse instante `naMao` **já é null** —
    /// não dá mais pra saber qual caixa era —, e o primeiro cartaz da primeira
    /// estante costuma estar fora da tela, seis fileiras acima. Um
    /// `requestFocus` num nó não composto não faz nada, e o foco cai no primeiro
    /// alvo que existe: o trilho. Daí «abre o menu lateral».
    ///
    /// Guardando o id **antes** de guardar a caixa, o alvo é o cartaz de onde ela
    /// saiu — que está na tela por construção, porque foi de lá que a pessoa
    /// veio.
    var ultimaPega by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(estado.naMao) {
        val agora = estado.naMao
        if (agora != null) {
            ultimaPega = agora.id
        } else if (ultimaPega != null) {
            runCatching { focoDaEstante.requestFocus() }
        }
    }

    /// ## O palco por cima da loja — T3
    ///
    /// ⚠️ Ele **retorna** em vez de desenhar por baixo: com a caixa na mão, a
    /// loja não é fundo, é o lugar de onde a caixa saiu. Deixar as duas
    /// compostas custaria a estante inteira desenhando atrás de um preto de 86%
    /// — e a §10.1 mostrou que esta TV não tem esse troco.
    ///
    /// É a mesma decisão que o `PalcoPorCima` do celular tomou, e pelo mesmo
    /// motivo escrito lá.
    estado.naMao?.let { naMao ->
        PalcoDaSala(
            caixa = naMao,
            arte = modelo.arte(naMao.poster),
            obra = estado.obraNaMao,
            ehVhs = estado.ehVhs(naMao.ano),
            /// ⚠️ **Assistir sai pela ficha**, e é honesto em vez de conveniente.
            ///
            /// A tela do player pede `arquivoId`, `titulo` e onde continuar — e
            /// nada disso está na `CaixaExposta` da vitrine, que é um resumo. A
            /// ficha já busca esses três e já sabe tocar.
            ///
            /// Mandar direto daqui exigiria uma segunda busca de arquivo escrita
            /// neste arquivo, e aí seriam **duas** contabilidades de «como se
            /// toca um filme» — o §43 exatamente.
            aoAssistir = {
                modelo.guardar()
                aoAbrirObra(naMao.id)
            },
            aoVerFicha = {
                modelo.guardar()
                aoAbrirObra(naMao.id)
            },
            aoFechar = modelo::guardar,
            modifier = modifier,
        )
        return
    }

    when {
        estado.carregando && estado.loja == null && estado.prateleira == null -> Column(
            modifier.fillMaxSize().padding(top = Sala.overscanV),
            verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
        ) {
            FileiraFantasma()
            FileiraFantasma()
        }

        estado.erro != null && estado.loja == null && estado.prateleira == null -> Recado(
            titulo = "a locadora não abriu",
            detalhe = estado.erro,
            modifier = modifier,
        ) { BotaoDaSala("tentar de novo", modelo::carregar, principal = true) }

        else -> LazyColumn(
            state = rolagem,
            contentPadding = PaddingValues(vertical = Sala.overscanV),
            verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
            modifier = modifier.fillMaxSize(),
        ) {
            /// ## A fachada — T3
            ///
            /// > «é a única tela que não tem cabeçalho — tem **fachada**. E numa
            /// > TV a fachada pode ser o que ela é de verdade: uma loja que você
            /// > atravessa.» (§5.2)
            ///
            /// ⚠️ As três peças vêm do `:cenario`, e são **as mesmas** do
            /// celular: a arandela com a meia-cúpula de latão e o facho, e as
            /// duas plaquinhas penduradas por pino e fio, tortas pra lados
            /// opostos. O `Cenografia.kt` inteiro atravessou nesta leva — 402
            /// linhas, **um** import de `material3`, medido antes de mover.
            ///
            /// O que muda é a escala e a encenação: no celular a fachada é um
            /// cabeçalho de 46dp que rola pra fora; aqui ela é a **entrada da
            /// loja**, centralizada, e as plaquinhas ficam grandes o bastante pra
            /// se ler o número de longe.
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Sala.overscanH),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Arandela(Modifier.align(Alignment.TopCenter))
                        Column(
                            Modifier.fillMaxWidth().padding(top = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            /// ⚠️ **54sp, e o celular usa 34.** É o mesmo
                            /// letreiro na escala da sala — a mesma conta que o
                            /// `TipoDaSala` faz pra todo corpo desta casa. A
                            /// sombra dourada é copiada dígito por dígito: é ela
                            /// que faz o nome parecer **aceso** e não escrito.
                            Text(
                                text = "locadora",
                                style = TextStyle(
                                    fontFamily = Serifada,
                                    fontSize = 54.sp,
                                    letterSpacing = 0.04.em,
                                    color = Color(0xFFE8CF9A),
                                    shadow = Shadow(
                                        color = Cores.destaque.copy(alpha = 0.45f),
                                        blurRadius = 34f,
                                    ),
                                ),
                            )
                            Text(
                                text = "ACERVO DA CASA",
                                style = TipoDaSala.rotulo.copy(letterSpacing = 0.3.em),
                                color = Cores.destaqueApagado,
                            )
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    /// As duas plaquinhas, penduradas e tortas pra lados opostos
                    /// — o §2.2 da doc, «as plaquinhas penduradas por pino e fio,
                    /// giradas uns graus para lados opostos».
                    estado.loja?.let { loja ->
                        Row(horizontalArrangement = Arrangement.spacedBy(52.dp)) {
                            EtiquetaPendurada(
                                numero = "${estado.naPrateleira}",
                                rotulo = "na prateleira",
                                angulo = -2.5f,
                            )
                            EtiquetaPendurada(
                                numero = "${estado.sorteadas}",
                                rotulo = "nesta semana",
                                angulo = 2f,
                            )
                        }
                        /// ⚠️ 18dp e não 10: a plaquinha é **papel colado**, com a
                    /// fita passando por cima da borda de cima. Com 10 a fita
                    /// encostava no primeiro cartaz da fileira, e um papel colado
                    /// num pôster não é o desenho — ele é colado na **madeira**.
                    Spacer(Modifier.height(18.dp))
                        Text(
                            text = "de ${loja.noAcervo} no acervo",
                            style = MaterialTheme.typography.labelLarge,
                            color = Cores.textoApagado,
                        )
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = Sala.overscanH)) {
                    /// ⚠️ **O título de tela saiu daqui** — a fachada acima o
                    /// substituiu, e é o ponto da §5.2: esta é «a única tela que
                    /// não tem cabeçalho — tem fachada».
                    ///
                    /// A contagem também saiu: ela agora está nas plaquinhas
                    /// penduradas, que é onde uma locadora de verdade a põe. O
                    /// que sobrou aqui é só o prazo da vitrine.
                    viraQuando(estado.loja?.viraEm)?.let {
                        Text(
                            text = "a vitrine vira $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cores.destaqueApagado,
                        )
                    }

                    /// O recado ao vivo do barramento — «fulano pegou X». Some
                    /// sozinho em 6s: é notícia, não estado.
                    ///
                    /// Numa TV ele vale mais que no celular, porque a tela fica
                    /// aberta muito mais tempo — é bem provável que alguém pegue
                    /// uma fita **enquanto** esta tela está no ar.
                    AnimatedVisibility(estado.recado != null) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            Box(
                                Modifier
                                    .background(
                                        Cores.destaque.copy(alpha = 0.14f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = estado.recado.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Cores.destaqueQuente,
                                )
                            }
                        }
                    }
                }
            }

            /// ## As minhas primeiro, e é a ordem certa
            ///
            /// A pergunta de quem abre a locadora é «o que eu peguei e até
            /// quando». A vitrine é passeio; o prazo é obrigação — e numa TV o
            /// que está embaixo custa apertos.
            if (estado.minhas.isNotEmpty()) {
                item {
                    Column {
                        RotuloDeSecao(
                            "comigo",
                            Modifier.padding(horizontal = Sala.overscanH),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
                            contentPadding = PaddingValues(horizontal = Sala.overscanH),
                        ) {
                            items(estado.minhas, key = { it.id }) { fita ->
                                val prazo = prazoDoEmprestimo(fita.venceEm)
                                Column {
                                    Cartaz(
                                        largura = Sala.cartazLdaEstante,
                                        altura = Sala.cartazAdaEstante,
                                        titulo = fita.titulo,
                                        arte = modelo.arte(fita.poster),
                                        detalhe = fita.ano?.toString(),
                                        cor = fita.corDominante,
                                        aoEscolher = { aoAbrirObra(fita.caixaId) },
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    if (prazo != null) {
                                        /// ⚠️ Vermelho a dois dias — é a decisão
                                        /// da web (§6), e `prazoDoEmprestimo`
                                        /// devolve os dias justamente pra a cor
                                        /// ser escolhida por quem desenha.
                                        Pilula(
                                            texto = prazo.first,
                                            cor = if (prazo.second <= 2) {
                                                Cores.perigo
                                            } else {
                                                Cores.linha
                                            },
                                            tinta = if (prazo.second <= 2) {
                                                Cores.perigo
                                            } else {
                                                Cores.textoApagado
                                            },
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    BotaoDaSala(
                                        rotulo = if (estado.devolvendo == fita.id) {
                                            "devolvendo…"
                                        } else {
                                            "devolver"
                                        },
                                        habilitado = estado.devolvendo != fita.id,
                                        aoEscolher = { modelo.devolver(fita.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            /// Com os outros. Aqui só dá pra **pedir**, e a diferença é do
            /// modelo: `minhas` e `dosOutros` existem separadas porque a
            /// prateleira mistura tudo de propósito, e ver quem te barra é parte
            /// da ideia.
            if (estado.dosOutros.isNotEmpty()) {
                item {
                    Column {
                        RotuloDeSecao(
                            "com os outros",
                            Modifier.padding(horizontal = Sala.overscanH),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
                            contentPadding = PaddingValues(horizontal = Sala.overscanH),
                        ) {
                            items(estado.dosOutros, key = { it.id }) { fita ->
                                Column {
                                    Cartaz(
                                        largura = Sala.cartazLdaEstante,
                                        altura = Sala.cartazAdaEstante,
                                        titulo = fita.titulo,
                                        arte = modelo.arte(fita.poster),
                                        detalhe = listOfNotNull(
                                            fita.ano?.toString(),
                                            "com ${fita.quemNome}",
                                        ).joinToString(" · "),
                                        cor = fita.corDominante,
                                        aoEscolher = { aoAbrirObra(fita.caixaId) },
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    BotaoDaSala(
                                        rotulo = if (estado.pedindo == fita.id) {
                                            "pedido"
                                        } else {
                                            "pedir de volta"
                                        },
                                        habilitado = estado.pedindo != fita.id,
                                        aoEscolher = { modelo.pedirDeVolta(fita.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            /// A vitrine, estante por estante.
            itemsIndexed(estado.expostas) { indice, estante ->
                Column {
                    /// ## A estante vira estante — T3
                    ///
                    /// > «**As estantes** viram fileiras horizontais de verdade:
                    /// > prateleira de madeira com veio, lábio iluminado na
                    /// > frente, etiqueta de papel colorido presa com fita girada
                    /// > uns graus, `6 de 145` no canto.» (§5.2)
                    ///
                    /// ⚠️ O rótulo em versalete dourado **saiu**: ele era a
                    /// convenção de fileira do resto do app, e numa locadora a
                    /// seção não se anuncia em maiúsculas — ela é **escrita à
                    /// mão num papel colado na madeira**. A `PlaquinhaDaEstante`
                    /// do `:cenario` é a mesma do celular, com o papel colorido
                    /// por gênero e a fita torta.
                    ///
                    /// A contagem fica no canto direito, longe da etiqueta, que é
                    /// onde uma loja põe o inventário: não na plaquinha bonita.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Sala.overscanH),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        PlaquinhaDaEstante(nome = estante.nome, indice = indice)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${estante.caixas.size} de ${estante.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Cores.textoApagado,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    /// ⚠️ 18dp e não 10: a plaquinha é **papel colado**, com a
                    /// fita passando por cima da borda de cima. Com 10 a fita
                    /// encostava no primeiro cartaz da fileira, e um papel colado
                    /// num pôster não é o desenho — ele é colado na **madeira**.
                    Spacer(Modifier.height(18.dp))

                    /// ## ⚠️ **O fundo de madeira faltava** — «cadê o fundo das
                    /// prateleiras de madeira?»
                    ///
                    /// Eu tinha posto só a **tábua**, que é o lábio da frente, e
                    /// chamei aquilo de estante. Não é: uma prateleira tem
                    /// **fundo**, e sem ele os cartazes flutuam num vão preto com
                    /// um risco de madeira embaixo.
                    ///
                    /// O painel é o mesmo do celular, copiado dígito por dígito:
                    /// os três marrons do degradê vertical e o veio, que são
                    /// linhas pretas a 7% de alfa com passo de 14dp. É o veio que
                    /// faz a superfície ser madeira e não um retângulo marrom.
                    Column(
                        Modifier
                            .padding(horizontal = Sala.overscanH)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF6B4E32),
                                        Color(0xFF5A4028),
                                        Color(0xFF44311E),
                                    ),
                                ),
                            )
                            .drawBehind {
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
                        horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
                    ) {
                        itemsIndexed(estante.caixas, key = { _, c -> c.id }) { posicao, caixa ->
                            Cartaz(
                                /// ⚠️ A estante mede diferente da biblioteca, e
                                /// é pedido do dono — ver `Sala.cartazLdaEstante`.
                                largura = Sala.cartazLdaEstante,
                                altura = Sala.cartazAdaEstante,
                                modifier = if (caixa.id == ultimaPega) {
                                    Modifier.focusRequester(focoDaEstante)
                                } else {
                                    Modifier
                                },
                                titulo = caixa.titulo,
                                arte = modelo.arte(caixa.poster),
                                detalhe = listOfNotNull(
                                    caixa.ano?.toString(),
                                    /// ⚠️ Fita ou disco sai do `ultimo_ano_vhs`
                                    /// do **servidor**, e não de uma constante
                                    /// daqui: é o mesmo número que decide se a
                                    /// caixa rebobina, e tê-lo em dois lugares é
                                    /// como os dois passam a discordar.
                                    if (estado.ehVhs(caixa.ano)) "VHS" else null,
                                ).joinToString(" · ").takeIf { it.isNotEmpty() },
                                /// ⚠️ **Escolher pega a caixa na mão**, e não
                                /// abre a ficha — T3.
                                ///
                                /// É o passo 1 da §5.2, e é o que separa esta
                                /// tela de uma grade de pôsteres: numa locadora
                                /// você **pega** a caixa antes de decidir. A
                                /// ficha continua a um `OK` de distância, pelo
                                /// menu de disco que a caixa aberta entrega.
                                aoEscolher = { modelo.pegarNaMao(caixa) },
                            )
                        }
                    }

                    /// ⚠️ **A tábua vem depois dos cartazes**, e é o que
                    /// transforma uma fileira numa prateleira: sem ela os
                    /// pôsteres flutuam, com ela eles estão **apoiados** em
                    /// alguma coisa.
                    ///
                    /// Ela é a mesma do celular, e o lábio iluminado na frente é
                    /// dela — a linha dourada em degradê que morre nas pontas.
                    ///
                    }

                    /// ⚠️ **22dp de respiro, e os primeiros 6 comeram o ano.** Na
                    /// foto da TCL a tábua passava por cima da segunda linha do
                    /// cartaz — «Sonic 3: O Filme» sobrevivia e «2024» ficava
                    /// metade atrás da madeira.
                    ///
                    /// O erro foi medir o vão contra a arte e não contra o
                    /// **cartão**: o `Cartaz` desta casa tem título e detalhe
                    /// abaixo do pôster, e ainda cresce 12% ao receber foco. A
                    /// tábua tem que ficar abaixo de tudo isso.
                    Spacer(Modifier.height(22.dp))
                    Box(Modifier.padding(horizontal = Sala.overscanH)) { Tabua() }
                }
            }
        }
    }
}
