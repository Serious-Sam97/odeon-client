package dev.odeon.android.ui.obra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.ArquivoDeMidia
import dev.odeon.android.dados.PlanoDeReproducao
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.PilulaDeEtiqueta
import dev.odeon.android.ui.corDeHex

/// A ficha da obra — a terceira tela do app, e a porta do player.
///
/// ## Pela biblioteca é livre, e a escassez fica na locadora
///
/// Esta tela já teve o funil do §66 dentro dela, e ele trancava o acervo
/// inteiro. O alcance da regra foi corrigido pelo dono: **a escassez vale no
/// modo locadora; pela biblioteca se assiste direto.** O porquê e o que os
/// documentos ainda dizem estão em `ModeloDaObra`.
///
/// O `@OptIn` é do `FlowRow`, usado pelas etiquetas: experimental de assinatura,
/// estável de comportamento, e o único jeito de ter `flex-wrap` sem escrever
/// medição de linha à mão.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaDaObra(
    modelo: ModeloDaObra,
    aoVoltar: () -> Unit,
    aoTocar: (arquivoId: String, titulo: String, ondeParou: Double, duracao: Double?) -> Unit,
    aoBaixar: (arquivoId: String) -> Unit = {},
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    if (estado.carregando) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cores.destaque)
        }
        return
    }

    val obra = estado.obra
    if (obra == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = estado.erro ?: "não deu pra abrir a ficha",
                color = Cores.perigo,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = modelo::tentarDeNovo) { Text("tentar de novo") }
            TextButton(onClick = aoVoltar) { Text("voltar") }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = aoVoltar, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("‹ biblioteca", color = Cores.destaque)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            /// O pôster em 2:3, a mesma proporção da grade — a ficha tem que
            /// parecer a continuação do cartão em que se tocou, não outra tela.
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                val poster = modelo.capa(obra.artwork["poster"])
                if (poster != null) {
                    AsyncImage(
                        model = poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = obra.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Cores.texto,
                )
                /// §24: cada linha só existe se tiver o que dizer. Nada de "—".
                obra.tituloOriginal?.takeIf { it != obra.title }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Cores.textoApagado)
                }
                obra.year?.let {
                    Text("$it", style = MaterialTheme.typography.bodyMedium, color = Cores.textoApagado)
                }
                obra.duracaoEmSegundos?.let {
                    Text(duracao(it), style = MaterialTheme.typography.bodySmall, color = Cores.textoApagado)
                }
            }
        }

        Selo(plano = estado.plano, carregando = estado.planoCarregando)

        obra.overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Cores.texto)
        }

        /// As etiquetas — R3.
        ///
        /// ## Elas vêm do servidor desde sempre, e o app descartava
        ///
        /// `tags` está no `WorkDetail` da web e o `ObraDetalhada` não a
        /// declarava: o comentário do modelo dizia que campo sem tela que o leia
        /// é contrato que ninguém confere, e estava certo — até esta tela
        /// existir.
        ///
        /// ⚠️ **Sem etiqueta, nada é desenhado** — nem rótulo de seção, nem
        /// "nenhuma". §24: linha vazia some. A web escreve "nenhuma" aqui, e é
        /// escolha dela: lá a seção é editável por administrador e o vazio é
        /// convite pra preencher. Aqui não há edição, então o vazio não é
        /// convite pra nada.
        if (obra.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                obra.tags.forEach { etiqueta ->
                    PilulaDeEtiqueta(
                        namespace = etiqueta.namespace,
                        valor = etiqueta.value,
                        cor = corDeHex(etiqueta.color),
                    )
                }
            }
        }

        Reproduzir(
            estado = estado,
            /// A duração sai do **arquivo** e só cai pra da obra se ele não
            /// trouxer: a do arquivo é o que o probe mediu naquele rip; a da
            /// obra é o tempo de execução do catálogo, que pode divergir de um
            /// corte pro outro.
            aoTocar = { arquivo ->
                aoTocar(
                    arquivo.id,
                    obra.title,
                    obra.ondeParou,
                    arquivo.duracaoEmSegundos ?: obra.duracaoEmSegundos,
                )
            },
        )

        /// Baixar.
        ///
        /// Fica **abaixo** do assistir e mais discreto: baixar é a exceção, e
        /// quem abriu a ficha quase sempre veio pra ver agora. Dar o mesmo peso
        /// aos dois faria a tela perguntar uma coisa que já estava respondida.
        if (estado.temComoTocar) {
            TextButton(
                onClick = { estado.arquivo?.let { aoBaixar(it.id) } },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text("baixar pra ver sem rede", color = Cores.textoApagado)
            }
        }

        /// Pegar a fita.
        ///
        /// ## Ela existe mesmo com a biblioteca livre, e o motivo é a locadora
        ///
        /// Desde o §71 não é preciso empréstimo pra assistir. Pegar a fita virou
        /// gesto **da locadora** — tirar a caixa da estante, com prazo e com
        /// escassez, que é a parte de jogo do produto. Quem pega quer a fita, não
        /// a permissão.
        TextButton(
            onClick = modelo::pegarAFita,
            enabled = !estado.pegando,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text(
                text = if (estado.pegando) "pegando…" else "pegar a fita na locadora",
                color = Cores.textoApagado,
            )
        }

        estado.recadoDaLocadora?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Cores.destaque)
        }

        Versoes(
            arquivos = obra.files,
            escolhido = estado.arquivo,
            aoEscolher = modelo::escolherArquivo,
        )
    }
}

/// O selo do modo, e o porquê dele.
///
/// A decisão foi de mostrá-lo **aqui e no player**. Aqui ele responde antes do
/// toque: num servidor de casa que atende três pessoas, saber que aquele arquivo
/// vai fazer o servidor re-encodar é informação que muda a escolha da versão.
///
/// O `reasons` vem escrito pelo servidor e vai para a tela como veio. Reescrever
/// aqui seria a terceira redação da mesma frase — a web já resistiu à mesma
/// tentação no `label` das legendas.
@Composable
private fun Selo(plano: PlanoDeReproducao?, carregando: Boolean) {
    /// Enquanto não sei, não escrevo nada. §24 outra vez: o vazio some, não vira
    /// "carregando…" piscando em cima de uma tela que já tem o que mostrar.
    if (plano == null) {
        if (carregando) return
        return
    }

    val rotulo = when (plano.mode) {
        "direct_play" -> "direto"
        "direct_stream" -> "remux"
        "transcode" -> "transcodificando"
        else -> plano.mode
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Cores.fundoElevado)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = rotulo,
            style = MaterialTheme.typography.labelLarge,
            color = if (plano.eDireto) Cores.certo else Cores.destaque,
        )
        plano.reasons.forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Cores.textoApagado)
        }
    }
}

/// O play — ou o silêncio dele.
///
/// ## Sem arquivo não há botão, e a tela diz isso
///
/// Sobrou **um** caso de esconder, e ele não é da locadora: obra sem arquivo no
/// acervo. Ela existe — é linha de catálogo identificada cuja mídia não está
/// aqui — e não toca de jeito nenhum.
///
/// Esconder e calar seria o §8b, o toque que não acontece sem ninguém entender
/// por quê. Então some o botão e fica a frase, que é o §53 e o §8b concordando:
/// não oferecer o que vai ser negado, e não negar em silêncio.
@Composable
private fun Reproduzir(estado: EstadoDaObra, aoTocar: (ArquivoDeMidia) -> Unit) {
    val arquivo = estado.arquivo

    when {
        !estado.temComoTocar -> Text(
            text = "sem arquivo no acervo",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.textoApagado,
        )

        else -> Button(
            onClick = { arquivo?.let(aoTocar) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (estado.obra?.ondeParou?.let { it > 1 } == true) "continuar" else "assistir")
        }
    }
}

/// As versões do mesmo filme.
///
/// ## Elas aparecem todas, e a decisão é essa
///
/// A grade já mostra `007 Contra Goldfinger (1964)` duas vezes, e a razão pode
/// ser dublagem ou legenda diferente — que é exatamente o tipo de coisa que se
/// escolhe na hora de assistir, não algo que o app deva decidir sozinho.
///
/// Some quando há uma só: uma lista de um item é ruído, e o §24 manda a linha
/// vazia sumir.
@Composable
private fun Versoes(
    arquivos: List<ArquivoDeMidia>,
    escolhido: ArquivoDeMidia?,
    aoEscolher: (ArquivoDeMidia) -> Unit,
) {
    if (arquivos.size < 2) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("versões", style = MaterialTheme.typography.labelLarge, color = Cores.textoApagado)

        arquivos.forEach { arquivo ->
            val eEste = arquivo.id == escolhido?.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (eEste) Cores.fundoElevado else Cores.fundoAfundado)
                    .clickable { aoEscolher(arquivo) }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = arquivo.filename,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (eEste) Cores.texto else Cores.textoApagado,
                )
                /// A segunda linha é montada só com o que existe, e some inteira
                /// quando não existe nada — o mesmo corolário do §24 que a grade
                /// já segue na linha do ano.
                ficha(arquivo)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Cores.textoApagado)
                }
            }
        }
    }
}

/// O tamanho do arquivo, e por que ele mora **aqui** e não na grade.
///
/// A R4 do redesenho pedia `1969 · 816p · 2h22 · 2,3 GB` no cartaz da grade, e
/// foi o que a primeira versão fez. O screenshot mostrou a linha truncando num
/// cartaz de 108dp — `1969 · 816p · 2h22 · …` —, e a reticência era o defeito:
/// ela promete um dado que nenhum gesto daquela tela alcança.
///
/// Esta é a tela certa pra ele de qualquer forma. O tamanho não ajuda a escolher
/// o que assistir; ele importa antes de **baixar**, e o botão de baixar está a
/// dois dedos daqui.
///
/// Vírgula decimal porque o app é em português. **Base 1000 e não 1024**, que é
/// a mesma escolha da web: este número é o que a pessoa compara com o espaço
/// livre que o Android mostra, e o Android usa base 1000 desde o Oreo. Com 1024
/// o mesmo arquivo apareceria como "2,1 GB" aqui e "2,3 GB" nos ajustes — e o
/// §18 vale também pra unidade.
private fun tamanho(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1) return "%.1f GB".format(gb).replace('.', ',')
    val mb = bytes / 1_000_000.0
    return "${mb.toLong()} MB"
}

private fun ficha(a: ArquivoDeMidia): String? = listOfNotNull(
    a.height?.let { "${it}p" },
    a.tamanhoEmBytes?.let { tamanho(it) },
    a.codecDeVideo,
    a.codecDeAudio,
    a.canaisDeAudio?.let { "${it}ch" },
    a.idiomasDeLegenda.takeIf { it.isNotEmpty() }?.joinToString("/"),
).joinToString(" · ").takeIf { it.isNotBlank() }

private fun duracao(segundos: Double): String {
    val total = segundos.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "${h}h${m.toString().padStart(2, '0')}" else "${m}min"
}
