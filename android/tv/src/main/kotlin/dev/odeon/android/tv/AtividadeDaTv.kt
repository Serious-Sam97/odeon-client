package dev.odeon.android.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.odeon.android.tv.home.CanalDaHome
import dev.odeon.android.tv.player.TelaDoPlayerDaTv
import dev.odeon.android.tv.telas.TelaDeLoginDaTv
import dev.odeon.android.tv.telas.TelaDaObraDaTv
import dev.odeon.android.tv.telas.Destino
import dev.odeon.android.tv.telas.TelaDoCanalAoVivoDaTv
import dev.odeon.android.tv.telas.TelaInicialDaTv
import dev.odeon.android.tv.ui.TemaDaSala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.login.ModeloDeLogin
import dev.odeon.android.ui.obra.ModeloDaObra
import dev.odeon.android.ui.player.ModeloDoPlayer
import dev.odeon.android.ui.aovivo.oQueEstaNoArAgora
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// Onde se está.
///
/// ## Uma `sealed interface` e um `when`, e não o `navigation-compose`
///
/// É a mesma decisão do `:app`, com o mesmo argumento e um a mais. Lá: «a pilha
/// tem dois níveis e um `sealed` resolve». Aqui a pilha tem três, e ainda
/// resolve — mas o argumento extra é que numa TV **não há gesto de voltar**. O
/// que existe é uma tecla, e ela é tratada por `BackHandler` em cada tela, uma
/// por uma. Uma biblioteca de navegação daria uma pilha de verdade e continuaria
/// não sabendo o que a tecla `voltar` significa dentro do player — onde ela
/// fecha o cromo antes de sair.
///
/// ⚠️ **Este comentário já foi mentira.** Ele dizia que a tecla «é tratada por
/// `BackHandler` em cada tela, uma por uma» quando só o player tinha um — e o
/// efeito, relatado pelo dono em 12/08/2026, era «voltar» na ficha saindo pro
/// launcher da TV. Hoje são quatro, e a pilha é:
///
///     episódios ▸ biblioteca ▸ ficha ▸ casa ▸ (aba ≠ biblioteca) ▸ sair
///
/// A regra que fecha o desenho: quem **não** liga um `BackHandler` está dizendo
/// «aqui voltar sai do app». Na raiz isso é certo; em qualquer outro lugar é o
/// defeito da ficha se repetindo.
/// O que a bancada pediu: um arquivo, num ponto, sem ninguém apertar nada.
private data class PedidoDaBancada(
    val busca: String?,
    val obra: String?,
    val indice: Int,
    val em: Double,
)

/// A ação que abre a bancada. Só existe em `debug` — ver o `when` de `lerIntencao`.
private const val ACAO_DA_BANCADA = "dev.odeon.android.tv.BANCADA"

private sealed interface Onde {
    /// Enquanto se pergunta ao `Cofre` se há sessão guardada. Dura um piscar, e
    /// existe pra a TV não mostrar a tela de login pra quem já entrou.
    data object Perguntando : Onde
    data object Porta : Onde
    /// `noAoVivo` é o único jeito de a casa abrir fora da biblioteca, e existe
    /// pro caso do vão entre dois programas — ver o `aoAcabar` lá embaixo.
    data class Casa(val noAoVivo: Boolean = false) : Onde

    /// Um canal **de fora**, tocando a playlist do servidor.
    ///
    /// ⚠️ Ele não é um `Filme` com outros campos: não tem obra, arquivo, duração
    /// nem onde parar. Fingir que é obrigaria o player de filme a aceitar nulos
    /// em tudo que ele usa pra existir.
    data class CanalDeFora(val canalId: String, val nome: String) : Onde
    /// [tocarEm] só é preenchido pela bancada: quando vem, a ficha toca sozinha
    /// naquele segundo em vez de esperar alguém apertar.
    data class Ficha(val obraId: String, val tocarEm: Double? = null) : Onde
    data class Filme(
        val obraId: String,
        val arquivoId: String,
        val titulo: String,
        val comecarEm: Double,
        val capaUrl: String?,
        /// ⚠️ **De que canal este filme veio**, ou `null` se veio do acervo.
        ///
        /// É o campo que separa «estou vendo um filme» de «estou num canal», e
        /// sem ele o fim do arquivo não tem a quem perguntar o que vem depois.
        val canalId: String? = null,
    ) : Onde
}

class AtividadeDaTv : ComponentActivity() {

    private val app: OdeonTv get() = application as OdeonTv

    /// A obra que um cartão da home pediu. Chega por `Intent`, e não por estado
    /// da tela — daí ela morar aqui e não dentro do Compose.
    private var pedidoDeFora by mutableStateOf<String?>(null)

    /// O pedido da bancada de medição. Ver [ACAO_DA_BANCADA].
    private var pedidoDaBancada by mutableStateOf<PedidoDaBancada?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /// ⚠️ Sem esta linha o `imePadding()` da tela de login mede **zero**.
        ///
        /// Por padrão o Android encaixa o conteúdo dentro das barras do sistema
        /// e **consome** os insets antes de o Compose vê-los — inclusive o do
        /// teclado. O `imePadding` continua compilando, continua rodando, e não
        /// faz nada: o campo de senha volta pra debaixo do IME, calado.
        ///
        /// Ela anda em par com o `windowSoftInputMode="adjustResize"` do
        /// manifesto. Uma sem a outra não resolve.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        lerIntencao(intent)

        /// Republicar a cada abertura é o que mantém as artes da home
        /// funcionando: as URLs carregam `?token=`, e o token roda. Ver o
        /// comentário longo em `CanalDaHome`.
        lifecycleScope.launch { CanalDaHome.publicar(applicationContext) }

        setContent { TemaDaSala { Raiz() } }
    }

    /// ⚠️ Sem `launchMode="singleTop"` no manifesto, isto **nunca é chamado**.
    ///
    /// Com o modo padrão, tocar num cartão da home com o app já aberto não
    /// entrega o `Intent` novo à instância que está rodando — o sistema traz a
    /// tarefa pra frente e pronto. É o mesmo defeito que os atalhos do celular
    /// tiveram, anotado no `AndroidManifest.xml` do `:app`: silencioso, e o
    /// sintoma é «funciona do zero e não faz nada com o app aberto».
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lerIntencao(intent)
    }

    private fun lerIntencao(intent: Intent?) {
        val i = intent ?: return
        when (i.action) {
            /// `odeon-tv://obra/<id>` — o cartão da fileira do app e o do
            /// «continuar assistindo» do sistema.
            /// ## ⚠️ A porta da bancada, trancada fora do debug
            ///
            /// Medir descarte de quadro exige repetir **a mesma coisa**: mesmo
            /// arquivo, mesmo ponto, sem salto de retomada. Fazer isso pelo
            /// controle custou meia sessão e errou de tela na metade das vezes —
            /// e uma medida que depende de acertar a navegação não é medida, é
            /// sorte.
            ///
            /// ⚠️ `BuildConfig.DEBUG` e não uma flag qualquer: isto **começa a
            /// tocar um arquivo** sem ninguém pedir. Numa versão de verdade seria
            /// uma porta pra qualquer app do aparelho empurrar vídeo na tela da
            /// sala.
            ACAO_DA_BANCADA -> if (BuildConfig.DEBUG) {
                pedidoDaBancada = PedidoDaBancada(
                    busca = i.getStringExtra("busca"),
                    obra = i.getStringExtra("obra"),
                    indice = i.getIntExtra("indice", 0),
                    em = i.getDoubleExtra("em", 0.0),
                )
            }

            Intent.ACTION_VIEW -> i.data?.lastPathSegment?.let { pedidoDeFora = it }

            /// A busca do sistema devolve o id na `Uri` da sugestão escolhida —
            /// ver `busca/ProvedorDeBusca.kt`.
            Intent.ACTION_SEARCH -> i.getStringExtra(android.app.SearchManager.EXTRA_DATA_KEY)
                ?.let { pedidoDeFora = it }
        }
    }

    @Composable
    private fun Raiz() {
        var onde by remember { mutableStateOf<Onde>(Onde.Perguntando) }

        LaunchedEffect(Unit) {
            onde = if (app.odeon.retomar()) Onde.Casa() else Onde.Porta
        }

        /// Um cartão da home abre a ficha por cima de onde quer que se esteja —
        /// mas só depois de haver sessão. Sem isto, quem toca no cartão com a TV
        /// deslogada cairia numa ficha que não carrega.
        /// ⚠️ Espera a casa existir, como o [pedidoDeFora]: pedir filme antes de
        /// haver sessão cairia na porta de entrada e o pedido se perderia.
        LaunchedEffect(pedidoDaBancada, onde) {
            val pedido = pedidoDaBancada ?: return@LaunchedEffect
            if (onde is Onde.Perguntando || onde is Onde.Porta) return@LaunchedEffect
            pedidoDaBancada = null

            val obraId = pedido.obra ?: runCatching {
                app.odeon
                    .biblioteca(filtros = dev.odeon.android.dados.Filtros(busca = pedido.busca.orEmpty()))
                    .getOrNull(pedido.indice)
                    ?.id
            }.getOrNull()

            if (obraId == null) {
                android.util.Log.w("Odeon", "bancada: nada achado pra ${pedido.busca}[${pedido.indice}]")
                return@LaunchedEffect
            }
            /// ⚠️ O que foi resolvido vai pro log **antes** de tocar: sem isso a
            /// medida não sabe dizer qual arquivo mediu, e uma medida sem sujeito
            /// não vale nada.
            android.util.Log.i("Odeon", "bancada: obra=$obraId em=${pedido.em}")
            onde = Onde.Ficha(obraId, tocarEm = pedido.em)
        }

        LaunchedEffect(pedidoDeFora, onde) {
            val pedido = pedidoDeFora ?: return@LaunchedEffect
            if (onde is Onde.Perguntando || onde is Onde.Porta) return@LaunchedEffect
            pedidoDeFora = null
            onde = Onde.Ficha(pedido)
        }

        Box(Modifier.fillMaxSize().background(Cores.fundo)) {
            when (val agora = onde) {
                /// Nada. É um piscar, e um spinner que aparece por 80ms é
                /// pior que a tela ficar no fundo da casa por 80ms — na TV, um
                /// giro no meio da tela é a coisa mais visível do cômodo.
                Onde.Perguntando -> Unit

                Onde.Porta -> {
                    val modelo = lembrarModelo("login") { ModeloDeLogin(app.odeon) }
                    val entrou by modelo.entrou.collectAsStateWithLifecycle()
                    LaunchedEffect(entrou) {
                        if (entrou) {
                            onde = Onde.Casa()
                            /// Assim que há sessão, a home da TV pode ser
                            /// preenchida — e essa é a primeira vez em que ela
                            /// tem o que mostrar.
                            CanalDaHome.publicar(applicationContext)
                        }
                    }
                    TelaDeLoginDaTv(modelo)
                }

                is Onde.Casa -> TelaInicialDaTv(
                    odeon = app.odeon,
                    barramento = app.barramento,
                    /// ⚠️ O ao vivo toca **sem passar pela ficha**, e é a única
                    /// tela que faz isso. Sintonizar é entrar no que já está
                    /// acontecendo — parar numa ficha no caminho seria o mesmo
                    /// que a TV perguntar «tem certeza?» ao trocar de canal.
                    aoTocar = { obraId, arquivoId, titulo, comecarEm, capa, canalId ->
                        onde = Onde.Filme(obraId, arquivoId, titulo, comecarEm, capa, canalId)
                    },
                    aoSintonizarDeFora = { canalId, nome ->
                        onde = Onde.CanalDeFora(canalId, nome)
                    },
                    aoAbrirObra = { onde = Onde.Ficha(it) },
                    destinoInicial = if (agora.noAoVivo) Destino.AO_VIVO else Destino.BIBLIOTECA,
                )

                is Onde.Ficha -> {
                    /// ⚠️ A chave é o id da obra, e não uma constante: sem ela,
                    /// abrir uma segunda ficha reaproveitaria o `ModeloDaObra`
                    /// da primeira — e a tela abriria com o filme errado.
                    val modelo = lembrarModelo("obra:${agora.obraId}") {
                        ModeloDaObra(app.odeon, agora.obraId)
                    }
                    TelaDaObraDaTv(
                        modelo = modelo,
                        tocarSozinhoEm = agora.tocarEm,
                        aoVoltar = { onde = Onde.Casa() },
                        aoTocar = { obraId, arquivoId, titulo, comecarEm, capa ->
                            onde = Onde.Filme(obraId, arquivoId, titulo, comecarEm, capa)
                        },
                    )
                }

                is Onde.CanalDeFora -> TelaDoCanalAoVivoDaTv(
                    canalId = agora.canalId,
                    nome = agora.nome,
                    modelo = lembrarModelo("aovivo") { dev.odeon.android.ui.aovivo.ModeloAoVivo(app.odeon) },
                    /// Sair de um canal devolve à sintonia, não à biblioteca:
                    /// quem estava num canal ainda está no ao vivo.
                    aoSair = { onde = Onde.Casa(noAoVivo = true) },
                )

                is Onde.Filme -> {
                    val modelo = lembrarModelo("filme:${agora.arquivoId}:${agora.comecarEm}") {
                        ModeloDoPlayer(
                            odeon = app.odeon,
                            obraId = agora.obraId,
                            arquivoId = agora.arquivoId,
                            titulo = agora.titulo,
                            ondeParou = agora.comecarEm,
                            duracaoEmSegundos = null,
                            capaUrl = agora.capaUrl,
                            barramento = app.barramento,
                        )
                    }
                    TelaDoPlayerDaTv(
                        modelo = modelo,
                        ondeParou = agora.comecarEm,
                        aoSair = {
                            /// ## ⚠️ Sair de um **canal** volta pra sintonia
                            ///
                            /// Relatado pelo dono: «quando entro pra assistir um
                            /// canal, ao voltar ele vai pra tela de descrição do
                            /// filme e depois pra home».
                            ///
                            /// O canal do Odeon abre o **player de filme** — é um
                            /// arquivo do acervo, afinal —, e o `voltar` dele leva
                            /// à ficha da obra, que é o certo pra quem escolheu um
                            /// filme. Não é o certo pra quem escolheu um canal:
                            /// esse não pediu o filme, pediu o canal, e o lugar de
                            /// onde ele veio é a sintonia.
                            ///
                            /// O `canalId` já viajava com o filme desde o conserto
                            /// da virada de programa; aqui ele responde a segunda
                            /// pergunta: **de onde essa pessoa veio**.
                            onde = if (agora.canalId != null) {
                                Onde.Casa(noAoVivo = true)
                            } else {
                                Onde.Ficha(agora.obraId)
                            }
                            /// Sair do filme é o momento em que a home da TV
                            /// ficou desatualizada: a posição mudou, e talvez o
                            /// filme tenha acabado. Republicar aqui é o que faz
                            /// a primeira tela refletir o que se acabou de
                            /// assistir.
                            lifecycleScope.launch {
                                CanalDaHome.publicar(applicationContext)
                            }
                        },
                        /// ## ⚠️ O fim do arquivo, num canal, é o começo do
                        /// próximo programa — e não o fim de nada
                        ///
                        /// Relatado pelo dono na TCL: «após um tempo rodando ao
                        /// vivo o app morreu, mesmo filme não mudou». Reproduzido
                        /// aqui: o filme termina, a tela fica preta com `faltam
                        /// 0:00`, e apertar play **recomeça o mesmo filme do
                        /// zero**. Nada disso é travamento; é um player que
                        /// recebeu um arquivo e nunca soube que estava num canal.
                        ///
                        /// ⚠️ **Pergunta-se ao servidor o que está no ar agora**,
                        /// em vez de avançar para «o próximo da grade que eu
                        /// tinha». O app pode ter passado horas no mesmo filme, e
                        /// a grade carregada lá atrás é história. Além disso o
                        /// arquivo e a faixa da grade quase nunca acabam no mesmo
                        /// segundo — quem manda é o relógio, não a fila.
                        ///
                        /// ⚠️ **Fora de um canal isto não faz nada, de
                        /// propósito.** O mesmo fim de arquivo acontece num filme
                        /// do acervo, e lá a tela preta também é feia — mas o que
                        /// fazer ali (voltar pra ficha? marcar como visto?) é
                        /// outra decisão, e não a que o dono pediu. Está anotado
                        /// no README em vez de resolvido às escondidas.
                        /// ## ⚠️ Acordar num canal é re-sintonizar
                        ///
                        /// Medido na TCL: o `A Hora do Rush` entrou às 11:34:43
                        /// em 43s, tocou **sete segundos**, e o painel apagou.
                        /// Às 12:14 a TV acordou mostrando 0:50 — a reprodução
                        /// pausa junto com a tela e continua de onde parou.
                        ///
                        /// Num filme do acervo isso é o certo. Num canal é o
                        /// contrário: quarenta minutos atrás da transmissão não é
                        /// ao vivo, e quem liga a TV num canal recebe o que está
                        /// passando, não o que estava.
                        ///
                        /// ⚠️ **A folga de dois minutos é o que separa isto de
                        /// brigar com o botão de pausa.** Pausar um canal e voltar
                        /// logo em seguida é uma coisa que se faz de verdade;
                        /// pular pra frente ali seria desfazer o que a pessoa
                        /// acabou de pedir. Dois minutos de atraso já não são uma
                        /// pausa — são um cochilo da TV.
                        aoVoltarAoFrente = { posicaoMs ->
                            val canal = agora.canalId
                            /// ⚠️ **O log entra antes das saídas, e isso foi uma
                            /// correção.** Na primeira versão ele ficava depois
                            /// dos `return`, então «não disparou» e «disparou e
                            /// desistiu» produziam o mesmo silêncio — e num
                            /// caminho que só acontece com a TV dormindo, esse
                            /// silêncio custa um ciclo inteiro de teste.
                            android.util.Log.i(
                                "odeon-aovivo",
                                "voltou ao frente: canal=$canal pos=${posicaoMs / 1000}s",
                            )
                            if (canal != null) {
                                lifecycleScope.launch {
                                    val noAr = oQueEstaNoArAgora(app.odeon, canal)
                                    android.util.Log.i(
                                        "odeon-aovivo",
                                        "no ar agora: ${noAr?.quadro?.titulo ?: "nada"} " +
                                            "em ${noAr?.comecarEm?.toLong() ?: -1}s",
                                    )
                                    val quadro = noAr?.quadro
                                    val obra = quadro?.obraId
                                    val arquivo = quadro?.arquivoId
                                    if (noAr == null || quadro == null || obra == null || arquivo == null) {
                                        return@launch
                                    }

                                    /// ⚠️ Programa diferente **sempre** troca; o
                                    /// mesmo programa só se a transmissão já foi
                                    /// longe. Sem essa distinção, dormir três
                                    /// segundos remontaria o player à toa.
                                    val trocouDeFilme = arquivo != agora.arquivoId
                                    val atrasoMs = (noAr.comecarEm * 1000).toLong() - posicaoMs
                                    if (!trocouDeFilme && atrasoMs < 120_000) return@launch

                                    android.util.Log.i(
                                        "odeon-aovivo",
                                        "acordou no canal $canal: pos=${posicaoMs / 1000}s " +
                                            "aovivo=${noAr.comecarEm.toLong()}s trocou=$trocouDeFilme " +
                                            "-> ${quadro.titulo}",
                                    )
                                    onde = Onde.Filme(
                                        obraId = obra,
                                        arquivoId = arquivo,
                                        titulo = quadro.titulo,
                                        comecarEm = noAr.comecarEm,
                                        capaUrl = app.odeon.urlDaArte(quadro.arte),
                                        canalId = canal,
                                    )
                                }
                            }
                        },
                        aoAcabar = {
                            val canal = agora.canalId
                            val acabou = agora.arquivoId
                            if (canal != null) {
                                lifecycleScope.launch {
                                    /// ## ⚠️ Esperar o vão é o que faz disto um canal
                                    ///
                                    /// Medido na grade da casa: as faixas batem
                                    /// com a duração dos filmes (o `Batman` ocupa
                                    /// 165 minutos para 2:44:32 de arquivo), e
                                    /// entre uma faixa e a seguinte há sempre um
                                    /// **vão de uns quatro minutos** — o `Planeta
                                    /// do Tesouro` acaba 10:55 e o `Sr. Ninguém`
                                    /// só começa 10:59.
                                    ///
                                    /// Isso quer dizer que o fim de **todo** filme
                                    /// cai num vão. Desistir no primeiro «nada no
                                    /// ar» quebraria o canal a cada programa, que
                                    /// é o defeito que o dono relatou com outras
                                    /// palavras.
                                    ///
                                    /// ⚠️ **Enquanto espera, a tela é a do ao
                                    /// vivo, não uma tela preta.** O vão é curto
                                    /// mas não é instantâneo, e quatro minutos de
                                    /// preto são exatamente o «o app morreu» de
                                    /// novo. Lá se vê o canal, a agulha e a hora
                                    /// em que o próximo começa.
                                    onde = Onde.Casa(noAoVivo = true)

                                    val ateQuando = 12
                                    repeat(ateQuando) { volta ->
                                        /// ⚠️ **Se saiu da espera, desiste.** O
                                        /// laço roda por minutos; se nesse tempo
                                        /// alguém abriu outra coisa, sintonizar
                                        /// por cima seria arrancar a pessoa da
                                        /// tela em que ela está.
                                        val esperando = onde
                                        if (esperando !is Onde.Casa || !esperando.noAoVivo) return@launch

                                        val proximo = oQueEstaNoArAgora(app.odeon, canal)
                                        val quadro = proximo?.quadro
                                        val obra = quadro?.obraId
                                        val arquivo = quadro?.arquivoId

                                        /// ⚠️ **O mesmo arquivo de novo é laço,
                                        /// não continuidade.** Se a faixa da grade
                                        /// for maior que o arquivo, perguntar «o
                                        /// que está passando?» devolve o filme que
                                        /// acabou de terminar — e re-sintonizá-lo
                                        /// num ponto além do fim o faria terminar
                                        /// na hora, girando sozinho. Trocar uma
                                        /// tela preta parada por uma piscando não
                                        /// é conserto.
                                        val mesmoDeNovo = arquivo != null && arquivo == acabou

                                        android.util.Log.i(
                                            "odeon-aovivo",
                                            "volta $volta no canal $canal -> " +
                                                (quadro?.let { "${it.titulo} mesmo=$mesmoDeNovo" } ?: "vão"),
                                        )

                                        if (!mesmoDeNovo && quadro != null && obra != null && arquivo != null) {
                                            onde = Onde.Filme(
                                                obraId = obra,
                                                arquivoId = arquivo,
                                                titulo = quadro.titulo,
                                                comecarEm = proximo.comecarEm,
                                                capaUrl = app.odeon.urlDaArte(quadro.arte),
                                                canalId = canal,
                                            )
                                            return@launch
                                        }
                                        delay(30_000)
                                    }
                                    /// Passou de seis minutos sem nada entrar no
                                    /// ar: fica na tela do ao vivo, que é onde a
                                    /// pessoa já está. Nada a fazer — e nada é
                                    /// melhor que sintonizar às cegas.
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
