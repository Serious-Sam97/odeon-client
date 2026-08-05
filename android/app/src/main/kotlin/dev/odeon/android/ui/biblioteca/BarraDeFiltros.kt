package dev.odeon.android.ui.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.odeon.android.dados.DURACOES
import dev.odeon.android.dados.ESTADOS_DE_IDENTIFICACAO
import dev.odeon.android.dados.EspacoDeEtiqueta
import dev.odeon.android.dados.EtiquetaDoAcervo
import dev.odeon.android.dados.Filtros
import dev.odeon.android.dados.ORDENS
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Tipo
import dev.odeon.android.ui.corDeHex

/// A barra de filtros da biblioteca — o `FilterBar.tsx` da web, de 220 linhas.
///
/// ## Ela é uma linha de chips, e o painel abre embaixo
///
/// A web tem espaço pra deixar tudo à mostra numa faixa; aqui não. A linha de
/// cima é o que se usa toda hora — abrir o painel, trocar a ordem, limpar — e o
/// resto mora atrás do `filtros ▾`, que é o mesmo gesto da web.
///
/// ## ⚠️ A pílula com o número é o que impede o filtro invisível
///
/// Um filtro ligado e escondido dentro de um painel fechado é a pior forma de
/// mentira de tela: a grade mostra 40 obras de 17.930 e nada explica por quê.
/// A pílula no botão é o que diz «há três filtros valendo aqui» sem precisar
/// abrir nada — e ela não conta a busca nem a ordem, que têm controle próprio à
/// vista (ver `Filtros.quantosLigados`).
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BarraDeFiltros(
    filtros: Filtros,
    etiquetasPorEspaco: List<Pair<EspacoDeEtiqueta, List<EtiquetaDoAcervo>>>,
    aberto: Boolean,
    aoAlternarPainel: () -> Unit,
    aoMudar: (Filtros) -> Unit,
    /// Quantos filmes estão no aparelho. **Zero esconde a pastilha** (§24).
    quantosBaixados: Int = 0,
    aoAbrirBaixados: () -> Unit = {},
) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(
                rotulo = if (aberto) "filtros ▴" else "filtros ▾",
                ligado = aberto,
                distintivo = filtros.quantosLigados.takeIf { it > 0 },
                aoTocar = aoAlternarPainel,
            )

            Ordem(atual = filtros.ordem, aoEscolher = { aoMudar(filtros.copy(ordem = it)) })

            /// O modo das tags só nasce com **duas** ligadas, e é a web quem
            /// decide isso: com uma tag só, «todas» e «qualquer» devolvem a
            /// mesma lista, e um botão que não muda nada é o §8b.
            if (filtros.etiquetas.size > 1) {
                Chip(
                    rotulo = if (filtros.modoDasEtiquetas == "any") "qualquer tag" else "todas as tags",
                    ligado = true,
                    aoTocar = {
                        aoMudar(
                            filtros.copy(
                                modoDasEtiquetas = if (filtros.modoDasEtiquetas == "any") "all" else "any",
                            ),
                        )
                    },
                )
            }

            if (filtros.algumLigado) {
                Chip(rotulo = "limpar ✕", ligado = false, aoTocar = { aoMudar(filtros.limpo()) })
            }

            /// ## A pastilha dos baixados — **acesa**, e é decisão do dono
            ///
            /// Ela veio de uma linha própria no cabeçalho, onde era «no aparelho
            /// ›» em texto dourado e passava despercebida. Aqui ela é o **único
            /// elemento preenchido** da fileira, e provavelmente da tela toda
            /// acima do herói.
            ///
            /// ⚠️ **Isso gasta a cor que o app reserva pro que está aceso** — o
            /// facho, a aba selecionada, os números das placas. O que paga a
            /// conta é o §24: ela **só existe quando há download**. Não é cromo
            /// permanente; aparece porque há algo no aparelho e some quando não
            /// há. Um dourado que aparece por um motivo não gasta a cor — gasta
            /// quem fica aceso à toa.
            ///
            /// E some junto a porta pra uma sala vazia: sem download, não há o
            /// que a tela de baixados mostre além de «nada baixado ainda».
            /// ⚠️ **`↓` e não `⤓`**, e foi a foto que decidiu. O `⤓` (seta pra
            /// barra) é o desenho certo pra «baixado», e **não existe na fonte
            /// do aparelho**: o Android substituiu por uma seta comum sem avisar.
            /// Um glifo que depende de substituição desenha coisa diferente em
            /// cada aparelho — melhor escolher o que está garantido do que
            /// torcer.
            if (quantosBaixados > 0) {
                Text(
                    text = "↓ $quantosBaixados no aparelho",
                    style = Tipo.pilula,
                    /// Texto **escuro** sobre o dourado. `Cores.fundo` e não
                    /// preto: o contraste é o da casa, e preto puro sobre âmbar
                    /// vibra na borda.
                    color = Cores.fundo,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Cores.destaque)
                        .clickable(onClick = aoAbrirBaixados)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        if (!aberto) return@Column

        /// ## O painel
        ///
        /// Os grupos vêm do servidor (`/api/tag-namespaces`) e a ordem é dele.
        /// Duração e identificação são escritas aqui porque não são tags: são
        /// colunas do banco, e a web também as tem chumbadas.
        etiquetasPorEspaco.forEach { (espaco, etiquetas) ->
            Grupo(rotulo = espaco.label, cor = corDeHex(espaco.color)) {
                etiquetas.forEach { etiqueta ->
                    Chip(
                        rotulo = etiqueta.value,
                        /// A contagem vai junto, e é ela que faz o chip valer:
                        /// «Terror 214» diz se vale a pena tocar. Ver o aviso do
                        /// `EtiquetaDoAcervo` — ela conta só o identificado.
                        distintivo = etiqueta.quantasObras,
                        ligado = etiqueta.chave in filtros.etiquetas,
                        cor = corDeHex(etiqueta.color) ?: corDeHex(espaco.color),
                        aoTocar = { aoMudar(filtros.comEtiqueta(etiqueta.chave)) },
                    )
                }
            }
        }

        Grupo(rotulo = "Duração") {
            DURACOES.forEach { (rotulo, de, ate) ->
                Chip(
                    rotulo = rotulo,
                    ligado = filtros.minutosDe == de && filtros.minutosAte == ate,
                    aoTocar = { aoMudar(filtros.comDuracao(de, ate)) },
                )
            }
        }

        Grupo(rotulo = "Identificação") {
            ESTADOS_DE_IDENTIFICACAO.forEach { (valor, rotulo) ->
                Chip(
                    rotulo = rotulo,
                    ligado = filtros.estado == valor,
                    aoTocar = { aoMudar(filtros.comEstado(valor)) },
                )
            }
        }
    }
}

/// Um grupo do painel: o rótulo pequeno e os chips embaixo.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Grupo(rotulo: String, cor: Color? = null, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = rotulo.uppercase(),
            style = Tipo.rotulo,
            color = cor ?: Cores.textoApagado,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            conteudo()
        }
    }
}

/// O chip.
///
/// Desenhado à mão, e não `FilterChip` do Material, pelo mesmo motivo do campo
/// de busca: o `FilterChip` traz um esquema de cores inteiro atrás, e o que não
/// estiver definido no `EsquemaEscuro` cai no padrão de fábrica. Foi assim que a
/// cápsula da barra de navegação virou lilás.
@Composable
private fun Chip(
    rotulo: String,
    ligado: Boolean,
    aoTocar: () -> Unit,
    distintivo: Int? = null,
    cor: Color? = null,
) {
    val tinta = if (ligado) (cor ?: Cores.destaque) else Cores.textoApagado

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (ligado) Cores.fundoElevado else Color.Transparent)
            .border(1.dp, if (ligado) tinta else Cores.linha, RoundedCornerShape(percent = 50))
            .clickable(onClick = aoTocar)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = rotulo, style = Tipo.pilula, color = tinta)
        /// O número fica **mais apagado que o rótulo**, sempre: ele é o
        /// argumento pra tocar no chip, não o nome dele.
        distintivo?.let {
            Text(text = "$it", style = Tipo.pilula, color = Cores.textoApagado)
        }
    }
}

/// A ordem, num menu — o `<select>` da web.
///
/// Chips não servem aqui: são seis opções mutuamente exclusivas e só uma vale
/// por vez. Seis chips ocupariam duas linhas permanentes pra mostrar cinco
/// escolhas que não foram feitas.
@Composable
private fun Ordem(atual: String, aoEscolher: (String) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    val rotulo = ORDENS.firstOrNull { it.first == atual }?.second ?: atual

    Row {
        Chip(rotulo = "$rotulo ▾", ligado = atual != "featured", aoTocar = { aberto = true })
        DropdownMenu(
            expanded = aberto,
            onDismissRequest = { aberto = false },
            containerColor = Cores.fundoElevado,
        ) {
            ORDENS.forEach { (valor, nome) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = nome,
                            style = Tipo.pilula,
                            color = if (valor == atual) Cores.destaque else Cores.texto,
                        )
                    },
                    onClick = {
                        aberto = false
                        aoEscolher(valor)
                    },
                )
            }
        }
    }
}
