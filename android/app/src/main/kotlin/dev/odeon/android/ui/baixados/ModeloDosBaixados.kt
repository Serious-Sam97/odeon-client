package dev.odeon.android.ui.baixados

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dev.odeon.android.dados.Baixado
import dev.odeon.android.dados.Baixados
import dev.odeon.android.dados.Cofre
import dev.odeon.android.dados.RelogioQueNaoVolta
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/// `@UnstableApi` porque ela **carrega** `Baixado`, que lê as constantes de
/// estado do Media3. O opt-in acompanha o tipo, não a intenção.
@UnstableApi
data class EstadoDosBaixados(val itens: List<Baixado> = emptyList())

/// Os downloads, e o relógio que decide quais ainda valem.
@UnstableApi
class ModeloDosBaixados(
    private val baixados: Baixados,
    private val cofre: Cofre,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDosBaixados())
    val estado: StateFlow<EstadoDosBaixados> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            /// Relê enquanto a tela está aberta.
            ///
            /// O `DownloadManager` tem ouvinte, mas ele fala de **um** download
            /// por vez e não do conjunto. Um segundo de intervalo desenha a fila
            /// inteira andando sem o app ficar reconstruindo lista a cada bloco
            /// de bytes que desce.
            while (true) {
                _estado.value = EstadoDosBaixados(baixados.lista())
                delay(1_000)
            }
        }
    }

    /// A fita venceu?
    ///
    /// A hora que vale é a do `RelogioQueNaoVolta` — a do aparelho, ou o maior
    /// instante já visto, o que for maior. Atrasar o relógio não ressuscita
    /// nada.
    fun venceu(item: Baixado): Boolean = RelogioQueNaoVolta.venceu(
        venceEm = prazoDe(item),
        doAparelho = System.currentTimeMillis(),
        maiorJaVisto = cofre.maiorInstanteVisto,
    )

    /// O prazo que vale pra este download.
    ///
    /// ⚠️ **Só o que veio da locadora expira.** É a decisão tomada depois do
    /// §71: a biblioteca é modo livre, e um arquivo baixado por lá que travasse
    /// deixaria o app mais restrito offline do que online. Ver
    /// `OrigemDoDownload`.
    private fun prazoDe(item: Baixado): Long? =
        if (item.ficha.origemTipada.expira) item.ficha.venceEm else null

    fun quandoVence(item: Baixado): String? = prazoDe(item)?.let {
        java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
            .format(java.util.Date(it))
    }

    fun apagar(id: String) {
        baixados.apagar(id)
        _estado.value = EstadoDosBaixados(baixados.lista())
    }
}
