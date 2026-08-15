package dev.odeon.android.tv.busca

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import dev.odeon.android.dados.Filtros
import dev.odeon.android.tv.OdeonTv
import kotlinx.coroutines.runBlocking

/// O Odeon dentro da busca **do sistema**.
///
/// ## Por que isto existe, e por que numa TV vale mais que uma tela de busca
///
/// Digitar numa TV é soletrar com setas — o `CampoDaSala` diz isso de frente. A
/// consequência é que **a busca dentro do app é a coisa menos usada de um app de
/// TV**, e uma biblioteca de 17.930 obras sem busca é uma grade em que só se
/// acha o que está nas primeiras fileiras.
///
/// A saída não é uma caixa de busca melhor: é não digitar. Segurar o botão do
/// microfone do controle da TCL e falar «Alien» abre a busca do sistema, e ela
/// pergunta aos apps instalados. Este provedor é a resposta do Odeon.
///
/// ## ⚠️ Ele roda numa thread de binder, e por isso o `runBlocking`
///
/// `query` é síncrono por contrato: quem chama é o processo da busca do sistema,
/// e ele espera o `Cursor` de volta. Não há como devolver «depois» — devolver
/// `null` é dizer «não tenho nada», que é diferente de «estou buscando».
///
/// `runBlocking` aqui é correto e não é gambiarra: a thread já é do pool de
/// binder, feita pra bloquear, e não é a principal. O que **seria** errado é
/// chamar isto de dentro do app.
///
/// ## O que ele não faz, e é de propósito
///
/// Não indexa nada. Não há cópia local do acervo, não há banco, não há
/// sincronização — cada busca é um `GET /api/works?search=` no servidor de casa.
/// Um índice local seria uma segunda cópia do acervo pra manter atualizada, e a
/// rede aqui é a LAN: o servidor responde antes de a pessoa terminar de falar.
///
/// ⚠️ E ele **não responde nada** sem sessão. Uma TV recém instalada devolve
/// zero resultados em vez de erro: a busca do sistema não tem onde mostrar «faça
/// login», e uma linha de erro no meio dos resultados do YouTube seria ruído.
class ProvedorDeBusca : ContentProvider() {

    private companion object {
        const val ETIQUETA = "OdeonTv/busca"
        const val AUTORIDADE = "dev.odeon.android.tv.busca"
        const val SUGESTOES = 1

        /// Quantas voltam. A busca da TV mostra uma fileira; passar de doze é
        /// mandar resultado que ninguém vai rolar até ver.
        const val QUANTAS = 12

        /// As colunas que a busca da Google TV lê.
        ///
        /// ⚠️ `SUGGEST_COLUMN_INTENT_DATA_ID` **não** serve aqui, e é a
        /// pegadinha: ele concatena o valor ao `searchSuggestIntentData` do
        /// `searchable.xml`, que este app não declara. O que funciona é
        /// `INTENT_DATA` com a `Uri` inteira — a mesma `odeon-tv://obra/<id>`
        /// dos cartões da home, que a Activity já sabe abrir.
        val COLUNAS = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
            SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,
            SearchManager.SUGGEST_COLUMN_DURATION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA,
        )
    }

    private val rotas = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTORIDADE, SearchManager.SUGGEST_URI_PATH_QUERY, SUGESTOES)
        addURI(AUTORIDADE, "${SearchManager.SUGGEST_URI_PATH_QUERY}/*", SUGESTOES)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projecao: Array<out String>?,
        selecao: String?,
        argumentos: Array<out String>?,
        ordem: String?,
    ): Cursor? {
        if (rotas.match(uri) != SUGESTOES) return null

        /// O termo chega de dois jeitos conforme quem pergunta: no último
        /// segmento do caminho, ou nos argumentos de seleção (é o que o
        /// `searchSuggestSelection=" ?"` do `busca.xml` liga).
        val termo = (argumentos?.firstOrNull() ?: uri.lastPathSegment)
            ?.trim()
            .orEmpty()
        if (termo.isEmpty() || termo == SearchManager.SUGGEST_URI_PATH_QUERY) return null

        val app = context?.applicationContext as? OdeonTv ?: return null
        val cursor = MatrixCursor(COLUNAS)

        runCatching {
            runBlocking {
                if (!app.odeon.retomar()) {
                    Log.i(ETIQUETA, "busca sem sessão — nada a responder")
                    return@runBlocking
                }
                app.odeon.garantirTokenDeMidia()

                val achadas = app.odeon.obras(
                    limite = QUANTAS,
                    filtros = Filtros(busca = termo),
                )

                achadas.forEach { obra ->
                    cursor.addRow(
                        arrayOf<Any?>(
                            obra.id,
                            obra.title,
                            /// A segunda linha do cartão. Só o que se sabe —
                            /// omitir é melhor que escrever «— · —» (§24).
                            listOfNotNull(
                                obra.year?.toString(),
                                obra.height?.let { "${it}p" },
                            ).joinToString(" · ").ifEmpty { null },
                            app.odeon.urlDoPoster(obra.poster ?: obra.arte),
                            /// O tipo MIME é o que faz a busca desenhar um
                            /// cartão de vídeo em vez de uma linha de texto.
                            "video/mp4",
                            obra.year,
                            null,
                            "odeon-tv://obra/${obra.id}",
                            obra.id,
                        ),
                    )
                }
            }
        }.onFailure {
            /// Falha em silêncio, e o silêncio é o certo: a busca do sistema não
            /// tem onde mostrar erro nosso. O `logcat` é o lugar — quem for
            /// investigar «o Odeon não aparece na busca» começa por aqui.
            Log.w(ETIQUETA, "a busca por «$termo» falhou: $it")
        }

        return cursor
    }

    override fun getType(uri: Uri): String =
        SearchManager.SUGGEST_MIME_TYPE

    /// Um provedor **só de leitura**. As três abaixo não existem de propósito —
    /// a busca do sistema não escreve no acervo de ninguém, e um `insert` aqui
    /// seria superfície exposta a todo app da TV sem uso nenhum.
    override fun insert(uri: Uri, valores: ContentValues?): Uri? =
        throw UnsupportedOperationException("o provedor de busca é só de leitura")

    override fun update(
        uri: Uri,
        valores: ContentValues?,
        selecao: String?,
        argumentos: Array<out String>?,
    ): Int = throw UnsupportedOperationException("o provedor de busca é só de leitura")

    override fun delete(uri: Uri, selecao: String?, argumentos: Array<out String>?): Int =
        throw UnsupportedOperationException("o provedor de busca é só de leitura")
}
