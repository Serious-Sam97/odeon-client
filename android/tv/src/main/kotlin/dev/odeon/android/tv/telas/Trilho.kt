package dev.odeon.android.tv.telas

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.odeon.android.tv.R
import dev.odeon.android.tv.ui.Focavel
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.tv.ui.TipoDaSala
import dev.odeon.android.ui.Cores
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import dev.odeon.android.ui.Insignia
import dev.odeon.android.ui.brilhoDoArco
import dev.odeon.android.ui.desenhaALente
import dev.odeon.android.ui.desenhaOCone

/// Os destinos da sala.
///
/// ## São os mesmos seis do celular, e a ordem também é a mesma
///
/// O `:app` tem cinco abas (`biblioteca · locadora · mural · guia · para você`)
/// mais o perfil na gaveta do canto. Aqui os seis viram uma coluna só, porque
/// numa TV não há canto superior direito alcançável — chegar lá com o D-pad
/// custaria atravessar a tela inteira.
///
/// ⚠️ **Baixados não entra**, e não é esquecimento: a TV não baixa (ver
/// `OdeonTv`). Uma aba que abre sempre vazia é pior do que aba nenhuma (§24).
///
/// ## ⚠️ Os símbolos eram glifos, e na TCL saíram errados — 12/08/2026
///
/// A primeira versão usava caracteres (`▤ ▣ ◈ ✦ ◉ ☺`) com o argumento de que
/// evitavam seis arquivos novos. Na TV eles saíram como outra coisa: o `◈` virou
/// um triângulo, o `✦` virou outro, e o `☺` virou um arco amarelo cortado. A
/// fonte do sistema da TCL simplesmente não tem esses glifos, e o Android caiu
/// no que tinha mais perto.
///
/// É um defeito de uma família que só aparece em aparelho: **o texto que o app
/// desenha não é escolha só do app** — depende de qual fonte o fabricante
/// embarcou. Um emulador com as fontes do Google teria mostrado os seis
/// certinhos.
///
/// Agora são vetores, e cinco deles são **cópia dos do `:app`** — os mesmos
/// `ic_aba_*.xml` da barra do celular. Isso resolve o defeito e paga um bônus: o
/// Odeon passa a ter a mesma iconografia nos dois aparelhos, que era o argumento
/// da `Cores` aplicado a desenho em vez de a cor.
enum class Destino(val rotulo: String, @DrawableRes val icone: Int) {
    BIBLIOTECA("biblioteca", R.drawable.ic_aba_biblioteca),

    /// ⚠️ **O ao vivo é o sétimo destino, e o único que a TV tem e o celular
    /// não.** Ele fica logo abaixo da biblioteca porque é o segundo lugar onde
    /// se pergunta «o que eu vejo agora?» — e acima da locadora, que é onde se
    /// pergunta «o que eu escolho?».
    AO_VIVO("ao vivo", R.drawable.ic_aba_aovivo),
    LOCADORA("locadora", R.drawable.ic_aba_locadora),
    MURAL("mural", R.drawable.ic_aba_mural),
    GUIA("guia", R.drawable.ic_aba_guia),
    PARA_VOCE("para você", R.drawable.ic_aba_paravoce),
    PERFIL("perfil", R.drawable.ic_aba_perfil),
}

/// O trilho lateral, que abre quando o foco entra nele.
///
/// ## Por que ele encolhe, e por que não some
///
/// Um menu fixo e aberto rouba 240dp de uma tela de 960dp úteis — um quarto da
/// largura, permanentemente, pra uma coisa que se usa uma vez por sessão. Um
/// menu que **some** resolve isso e cria outro problema: sem nada na borda, não
/// há pra onde apontar a seta esquerda, e o menu vira um segredo.
///
/// Encolhido ele continua sendo um alvo de D-pad — a seta esquerda a partir da
/// primeira coluna de cartazes cai nele —, e a coluna de símbolos diz que há
/// algo ali. Focado, ele abre e escreve as palavras.
///
/// É o mesmo desenho do `NavigationDrawer` do `androidx.tv.material3`, escrito à
/// mão pelo mesmo motivo dos outros componentes deste módulo: o do artefato traz
/// o estado de foco dele, as medidas dele e a animação dele, e aqui os três já
/// existem em `Foco.kt` e em `Sala`.
///
/// ## ⚠️ Ele não é um menu. Ele é a **cabine** — T1, 12/08/2026
///
/// Esta é a leva 1 do `docs/REDESENHO-TV.md`, e a ideia da §4 é geométrica:
///
/// > «Na barra do celular o facho nasce **abaixo** e sobe. Numa TV a única borda
/// > que sobra é a **esquerda** — e ali está exatamente onde, numa sala, fica a
/// > cabine de projeção.»
///
/// Então a lâmpada mora aqui, e o feixe abre pra direita sobre a tela toda. O
/// que este arquivo desenha é a **lente** — o ponto quente no destino escolhido,
/// com o halo. O feixe em si é grande demais pra caber num trilho de 96dp: ele é
/// desenhado atrás do conteúdo pela `TelaInicialDaTv`, que recebe daqui a altura
/// da lente.
///
/// É a terceira aparição do mesmo projetor (§2.1): a lente da barra do celular,
/// a lente que corre sobre a película do player, e esta.
///
/// ## O que a T1 acrescentou, em ordem de cima pra baixo
///
/// | | |
/// |---|---|
/// | **o perfil** | avatar, anel de progresso e selo do nível — a `Insignia` de verdade, do `:cenario`, e não uma refeita |
/// | **a busca** | um ícone, que abre a busca **do sistema** (§5.1: digitar com D-pad é soletrar) |
/// | os cinco destinos | com a lente no escolhido |
@Composable
fun Trilho(
    atual: Destino,
    aoTrocar: (Destino) -> Unit,
    /// O perfil, pro retrato do topo. `null` enquanto não chegou — e aí a
    /// `Insignia` desenha a marca derivada do nome, que é o padrão de quem não
    /// escolheu e não um buraco (§10.6).
    nome: String,
    rosto: String?,
    nivel: Int?,
    fatiaDoNivel: Float,
    aoBuscar: () -> Unit,
    /// Onde a lente está, em pixels a partir do topo do trilho. É o que a
    /// `TelaInicialDaTv` precisa pra ancorar o feixe — ver o cabeçalho.
    aoMoverALente: (Float) -> Unit,
    /// ⚠️ **Na locadora o trilho apaga a luz e vira silhueta** — §5.2.
    ///
    /// > «O conflito da borda esquerda. A fachada quer o centro; o trilho ocupa a
    /// > esquerda. Proposta: na locadora, o trilho **apaga a luz e vira
    /// > silhueta** — fechado, escuro, quase parte da parede — e a marquise da
    /// > loja assume como fonte de luz da tela. É coerente: entrou-se na loja, e
    /// > a luz agora é dela. Sair pro trilho reacende o facho.»
    ///
    /// «Sair pro trilho reacende» sai de graça: o trilho **abre ao receber
    /// foco**, e a cabine só se apaga fechada. Quem vai escolher outro destino
    /// vê a luz voltar no caminho.
    cabineApagada: Boolean = false,
    modifier: Modifier = Modifier,
    /// Onde a seta esquerda do conteúdo aterrissa. Ele fica no destino **atual**
    /// e não no primeiro: sair da biblioteca e voltar tem que cair na
    /// biblioteca, senão o menu esquece onde a pessoa está toda vez.
    foco: FocusRequester? = null,
) {
    var aberto by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxHeight()
            .onFocusChanged { aberto = it.hasFocus }
            /// ## ⚠️ Sem animação de largura, e é otimização medida
            ///
            /// O `animateContentSize(tween(160))` daqui parecia barato: 160ms de
            /// uma barra crescendo. Mas o trilho é o **primeiro filho de uma
            /// `Row`** — mudar a largura dele a cada quadro obriga a grade
            /// inteira ao lado a se remedir e a se reposicionar, sessenta vezes,
            /// por uma animação que ninguém pediu.
            ///
            /// Medido na TCL enquanto se abre e fecha o menu: **86% de quadros
            /// travados, mediana de 150ms**, com a GPU em 21ms — ou seja, o custo
            /// não estava no desenho, estava na medida.
            ///
            /// E o dono já tinha dito o que queria em outra tela: «tire ele,
            /// quero só ir percorrendo os filmes sem mt estresse ou movimentos».
            /// Um menu que aparece é mais rápido e mais calmo que um menu que
            /// cresce.
            .width(if (aberto) LARGURA_ABERTO else LARGURA_FECHADO)
            .background(
                /// O degrau de fundo só aparece aberto. Fechado, o trilho é
                /// parte do fundo — o que evita uma tarja escura permanente na
                /// borda esquerda de todo filme e todo pôster.
                if (aberto) {
                    /// ⚠️ **Opaco na maior parte, e só depois esmaece.**
                    ///
                    /// O degradê era `fundoAfundado → transparente` ao longo dos
                    /// 240dp inteiros, e na TCL isso não é um painel: é um véu de
                    /// gaze. Os rótulos caíam em cima dos cartazes e os dois
                    /// tinham o mesmo peso.
                    ///
                    /// Um menu aberto precisa ser **página**, não filtro. Opaco
                    /// até 80% e o esmaecimento só na borda, que é o que evita a
                    /// aresta reta sem entregar a legibilidade.
                    Brush.horizontalGradient(
                        0f to Cores.fundoAfundado,
                        0.8f to Cores.fundoAfundado,
                        1f to Cores.fundo.copy(alpha = 0f),
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .padding(vertical = Sala.overscanV),
        verticalArrangement = Arrangement.Center,
    ) {
        /// ## O perfil no topo, e por que ele sai da fileira
        ///
        /// No celular ele mora na gaveta do canto superior direito. Aqui esse
        /// canto não existe como lugar alcançável — chegar lá custaria
        /// atravessar a tela inteira de D-pad.
        ///
        /// E ele **não é um ícone como os outros**: é um retrato. A §4.1 pede
        /// «avatar, anel de progresso e o selo do nível — o mesmo desenho da
        /// insígnia do celular», e agora isso é literal: a `Insignia` vem do
        /// `:cenario`, a mesma que o celular desenha.
        ///
        /// ⚠️ O `:tv` tinha uma **refeita à mão** no `TelaDoPerfilDaTv`, com o
        /// comentário «é a `Insignia` do `:app` refeita, e não a mesma». Aquela
        /// continua lá por ora; esta é a de verdade, e a T5 junta as duas.
        RetratoDoTrilho(
            nome = nome,
            rosto = rosto,
            nivel = nivel,
            fatia = fatiaDoNivel,
            aberto = aberto,
            escolhido = atual == Destino.PERFIL,
            aoEscolher = { aoTrocar(Destino.PERFIL) },
            /// ⚠️ **O retrato também carrega a lente** — e a primeira versão
            /// desta leva esqueceu, o que só apareceu abrindo o perfil na TCL.
            ///
            /// Com o perfil escolhido, nenhum item da fileira era o «atual», e
            /// aí não havia lente em lugar nenhum **e o feixe continuava saindo
            /// da última posição conhecida** — luz sem lâmpada, que é a coisa que
            /// este trilho inteiro existe pra não fazer.
            aoMoverALente = if (atual == Destino.PERFIL) aoMoverALente else null,
        )

        Spacer(Modifier.height(10.dp))

        /// ## A busca é um ícone, e o que ela abre não é desta tela
        ///
        /// A §5.1 é categórica: «Não há campo de texto nesta tela. Digitar com
        /// D-pad é soletrar.» O que este item abre é a busca **do sistema**, por
        /// voz — que já está implementada no `busca/ProvedorDeBusca.kt` e nunca
        /// foi exercitada.
        ///
        /// > «Um campo de texto aqui seria oferecer o pior caminho como se fosse
        /// > o principal.»
        BotaoDaBusca(aberto = aberto, aoEscolher = aoBuscar)

        Spacer(Modifier.height(10.dp))
        Divisoria(aberto)
        Spacer(Modifier.height(10.dp))

        /// ⚠️ O perfil sai da fileira porque já está no topo. Filtrar em vez de
        /// tirar do `enum` é de propósito: ele **continua** sendo um destino de
        /// navegação, e o `when` da `TelaInicialDaTv` continua exaustivo.
        Destino.entries.filter { it != Destino.PERFIL }.forEach { destino ->
            ItemDoTrilho(
                destino = destino,
                escolhido = destino == atual,
                apagada = cabineApagada && !aberto,
                aberto = aberto,
                aoEscolher = { aoTrocar(destino) },
                aoMoverALente = if (destino == atual) aoMoverALente else null,
                modifier = if (foco != null && destino == atual) {
                    Modifier.focusRequester(foco)
                } else {
                    Modifier
                },
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

/// A régua fina que separa o perfil e a busca dos destinos.
///
/// Ela existe porque os dois de cima **não são destinos do mesmo tipo**: um é
/// quem você é, o outro é uma ação do sistema, e os cinco de baixo são lugares.
/// Sem a régua a coluna lê como sete abas, e duas delas se comportam diferente.
@Composable
private fun Divisoria(aberto: Boolean) {
    Box(
        Modifier
            .padding(horizontal = if (aberto) 30.dp else 10.dp)
            .height(1.dp)
            .width(if (aberto) 172.dp else 20.dp)
            .background(Cores.linha),
    )
}

/// O retrato do topo — a `Insignia` do `:cenario`, do tamanho da sala.
@Composable
private fun RetratoDoTrilho(
    nome: String,
    rosto: String?,
    nivel: Int?,
    fatia: Float,
    aberto: Boolean,
    escolhido: Boolean,
    aoEscolher: () -> Unit,
    aoMoverALente: ((Float) -> Unit)?,
) {
    val forma = RoundedCornerShape(10.dp)
    val brilho = brilhoDoArco(escolhido)
    Focavel(
        aoEscolher = aoEscolher,
        modifier = Modifier.padding(horizontal = 2.dp),
        forma = forma,
        anel = false,
    ) { focado ->
        /// ⚠️ **Sem caixa por trás do item focado.**
        ///
        /// Ele ganhava `fundoElevado` arredondado, e numa coluna isso lê como
        /// grade de botões, não como trilho. Quem marca o **escolhido** é o
        /// facho — a luz do projetor, um item por vez; quem marca o **focado** é
        /// o ícone dourado. Duas coisas diferentes, dois sinais diferentes.
        Row(
            Modifier.padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            /// A mesma lente dos destinos, no mesmo lugar da fileira — o retrato
            /// tem 3dp a menos de ícone, então ela alinha com as cinco de baixo.
            Box(
                Modifier
                    .size(width = 3.dp, height = 20.dp)
                    .onGloballyPositioned { coords ->
                        aoMoverALente?.invoke(
                            coords.positionInWindow().y + coords.size.height / 2f,
                        )
                    },
            ) {
                if (escolhido) {
                    Canvas(Modifier.matchParentSize()) {
                        val centro = Offset(size.width / 2f, size.height / 2f)
                        /// ⚠️ **5,5× de volta, e o 12× foi um erro de leitura meu.**
                        ///
                        /// Eu subi pra 12× querendo que a luz «saísse do item». Mas
                        /// `desenhaOCone` pinta um `drawRect` no `DrawScope` **desta
                        /// caixa**, que tem 3×20dp — o radial nunca vaza pra fora
                        /// dela. Com raio de 36dp a caixa inteira fica no topo do
                        /// gradiente e vira uma **pastilha branca chapada**, que foi
                        /// o «a luz tá estranha» do dono.
                        ///
                        /// Quem ilumina a sala é o `FeixeDaCabine`, em tela cheia.
                        /// Aqui é só o ponto quente da fonte, e ele precisa de queda
                        /// dentro dos próprios 20dp pra parecer um ponto.
                        desenhaOCone(centro = centro, raio = size.width * 5.5f, forca = brilho)
                        desenhaALente(
                            centro = centro,
                            forca = brilho,
                            largura = size.width,
                            altura = size.height,
                        )
                    }
                }
            }
            Spacer(Modifier.width(3.dp))
            /// ⚠️ **44dp, e o celular usa 36.** É o mesmo argumento do
            /// `TipoDaSala`: o desenho é o mesmo, a escala é a daqui. Um retrato
            /// de 36dp a três metros é uma mancha colorida.
            /// ⚠️ **`requiredSize`, e não `size`** — a trava contra o defeito que
            /// o dono viu: «a foto de perfil tá casada».
            ///
            /// A conta do trilho fechado é apertada por construção (2 + 3 + 3 +
            /// 26 + 2 = 36 dentro de 40), e quando ela estoura o que acontece não
            /// é corte: `size` é **preferência**, o pai reduz a largura, mantém a
            /// altura, e o rosto redondo vira elipse — em silêncio.
            ///
            /// Com `requiredSize` o erro passa a **transbordar**, que é feio e é
            /// visível. Defeito visível se conserta; defeito silencioso vira
            /// característica.
            Insignia(
                modifier = Modifier.requiredSize(26.dp),
                nome = nome,
                rosto = rosto,
                nivel = nivel,
                fatia = fatia,
                /// ⚠️ 26dp, e não 44: a insígnia grande não cabe num vão de
                /// 40dp. O nível deixa de ser um número dentro do selo e passa a
                /// ser **só a cor do anel** — o número volta quando o painel
                /// abre, que é onde há espaço pra ele significar algo.
                tamanho = 26.dp,
                cor = if (escolhido || focado) Cores.destaqueQuente else Cores.destaque,
            )
            if (aberto) {
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = nome.ifBlank { "perfil" },
                        style = TipoDaSala.rotulo,
                        color = if (focado) Cores.texto else Cores.textoApagado,
                        maxLines = 1,
                    )
                    /// `nv 2` — a §4.1 escreve assim, curto. «nível» inteiro
                    /// numa coluna de 172dp empurra o número pra segunda linha.
                    Text(
                        text = if (nivel != null) "nv $nivel" else "—",
                        style = TipoDaSala.pilula,
                        color = Cores.destaque,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/// A busca, que é a do sistema.
@Composable
private fun BotaoDaBusca(aberto: Boolean, aoEscolher: () -> Unit) {
    val forma = RoundedCornerShape(10.dp)
    Focavel(
        aoEscolher = aoEscolher,
        modifier = Modifier.padding(horizontal = 2.dp),
        forma = forma,
        anel = false,
    ) { focado ->
        Row(
            Modifier
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            /// O vão de 3dp da barra de "você está aqui" dos destinos, vazio:
            /// sem ele o ícone da busca não alinharia com os cinco de baixo.
            Spacer(Modifier.width(3.dp))
            Spacer(Modifier.width(12.dp))
            Icon(
                painter = painterResource(R.drawable.ic_buscar),
                contentDescription = "buscar",
                tint = if (focado) Cores.destaqueQuente else Cores.textoApagado,
                modifier = Modifier.size(20.dp),
            )
            if (aberto) {
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "BUSCAR",
                    style = TipoDaSala.rotulo,
                    color = if (focado) Cores.texto else Cores.textoApagado,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ItemDoTrilho(
    destino: Destino,
    escolhido: Boolean,
    apagada: Boolean,
    aberto: Boolean,
    aoEscolher: () -> Unit,
    /// Só o item escolhido reporta, e por isso é anulável: o feixe tem uma
    /// origem só.
    aoMoverALente: ((Float) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val forma = RoundedCornerShape(10.dp)
    /// ⚠️ Os dez quadros do arco, do `:cenario`. A chave é `escolhido`, então a
    /// lâmpada **reacende ao trocar de destino** e não ao mover o foco — que é a
    /// mesma separação que o parágrafo abaixo defende, agora em forma de luz.
    val brilho = brilhoDoArco(escolhido)
    Focavel(
        aoEscolher = aoEscolher,
        modifier = modifier.padding(horizontal = 2.dp),
        forma = forma,
        anel = false,
    ) { focado ->
        Row(
            Modifier
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            /// ## A barra de "você está aqui" virou **a lente** — T1
            ///
            /// Foco e destino atual são **duas** coisas, e num trilho elas se
            /// separam o tempo todo: percorrer o menu move o foco por cima de
            /// cinco destinos sem trocar de tela nenhuma vez. Se o mesmo desenho
            /// dissesse as duas, o menu mentiria sobre onde a pessoa está a cada
            /// aperto de seta.
            ///
            /// Isso continua verdade; o que mudou é **o que** o escolhido
            /// desenha. Era uma barrinha dourada de 3dp — a marca de aba de
            /// qualquer app. Agora é a lente do projetor, com o halo, e é ela
            /// que a §4.1 pede: «no escolhido **a lente**: um disco de luz com
            /// halo, o mesmo objeto do player».
            ///
            /// ⚠️ Ela ocupa a mesma largura de 3dp de antes, mais o halo, que
            /// sangra por fora sem empurrar nada — o `Canvas` desenha além da
            /// caixa dele. Os ícones não se moveram um pixel.
            Box(
                Modifier
                    .size(width = 3.dp, height = 20.dp)
                    .onGloballyPositioned { coords ->
                        /// O centro da lente, em coordenadas da **janela** — é o
                        /// que a `TelaInicialDaTv` precisa, porque o feixe é
                        /// desenhado por ela e não aqui.
                        aoMoverALente?.invoke(
                            coords.positionInWindow().y + coords.size.height / 2f,
                        )
                    },
            ) {
                if (escolhido && !apagada) {
                    Canvas(Modifier.matchParentSize()) {
                        val centro = Offset(size.width / 2f, size.height / 2f)
                        /// O halo é o mesmo cone do facho, pequeno: 5,5× a
                        /// largura da lente. Fora daqui, na tela toda, ele vira
                        /// o feixe — mas o que sai **da** lente é sempre isto.
                        desenhaOCone(
                            centro = centro,
                            raio = size.width * 5.5f,
                            forca = brilho,
                        )
                        desenhaALente(
                            centro = centro,
                            forca = brilho,
                            /// Em pé, e não deitada: na barra do celular a lente
                            /// é uma oval de 26x5 vista de frente numa fileira
                            /// horizontal. Aqui a fileira é vertical, então a
                            /// mesma lente vista de frente tem os eixos
                            /// trocados.
                            largura = size.width,
                            altura = size.height,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                painter = painterResource(destino.icone),
                contentDescription = destino.rotulo,
                tint = when {
                    focado -> Cores.destaqueQuente
                    escolhido -> Cores.destaque
                    else -> Cores.textoApagado
                },
                modifier = Modifier.size(20.dp),
            )
            if (aberto) {
                Spacer(Modifier.width(16.dp))
                Text(
                    text = destino.rotulo.uppercase(),
                    style = TipoDaSala.rotulo,
                    color = when {
                        focado -> Cores.texto
                        escolhido -> Cores.destaque
                        else -> Cores.textoApagado
                    },
                    maxLines = 1,
                )
            }
        }
    }
}


/// ## ⚠️ A grossura do trilho, medida contra a reclamação do dono
///
/// «Ele ocupa um puta espaço que a maior parte é inútil, principalmente de
/// grossura.» Estava certo, e a conta é curta: os 96dp de antes eram **44dp de
/// insígnia e 52dp de padding** — `horizontal = 20dp` no `Focavel` mais `11dp`
/// no `Row`, dos dois lados.
///
/// 40dp é o ícone de 20dp com 10dp de folga de cada lado, que é o mínimo pra
/// ele não encostar na borda da tela num aparelho com overscan.
///
/// ⚠️ E o ganho real é maior que a diferença: o conteúdo pedia `overscanH` por
/// cima dos 96dp, então nada aparecia antes de **144dp**. Com o trilho por cima
/// do conteúdo (ver `TelaInicialDaTv`), o vão dele **é** a margem.
val LARGURA_FECHADO = 40.dp

/// Aberto ele é uma **sobreposição**, e por isso pode ser generoso sem custar
/// nada: nada reflui atrás dele.
val LARGURA_ABERTO = 220.dp
