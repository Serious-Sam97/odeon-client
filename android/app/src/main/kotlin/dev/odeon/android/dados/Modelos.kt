package dev.odeon.android.dados

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/// O contrato com o servidor, escrito à mão.
///
/// ## Por que à mão, e não gerado
///
/// Porque é assim que as outras duas cópias são. `web/src/api.ts` descreve as
/// mesmas respostas em TypeScript escrito à mão, e o `clients/shared` em Kotlin.
/// Não há tipo compartilhado, não há código gerado, não há import cruzado — a
/// espec registrou isso como a dívida que a separação dos repositórios comprou
/// (§67 do DESIGN.md).
///
/// ## Todo campo opcional é opcional aqui também
///
/// Os `?` abaixo não são cautela: são o que o servidor devolve. Um `poster` nulo
/// é uma obra sem arte baixada — e são **8.598 de 17.930**, ou seja **48% do
/// acervo** (medido no banco em 04/08/2026). Declarar não-nulo faria a
/// biblioteca falhar em quase metade do que ela lista.
///
/// E o §18 manda o corolário: quando o dado não existe, a tela **omite**. Não
/// inventa, não escreve "—".

/// `POST /api/auth/login`
@Serializable
data class Credenciais(
    val username: String,
    val password: String,
    /// O nome do aparelho, e ele importa mais do que parece.
    ///
    /// A tela de aparelhos do admin lista as sessões por este rótulo. Sessão
    /// sem rótulo aparece anônima — e, pelo que o projeto já observou, sessão
    /// com `device_label` nulo é sinal de linha inserida à mão, não de login de
    /// verdade. Mandar o modelo do aparelho mantém essa distinção útil.
    @SerialName("device_label") val deviceLabel: String? = null,
)

/// A resposta do login. O `token` é o de **sessão**: 90 dias, `Bearer`.
@Serializable
data class RespostaDeLogin(
    val token: String,
    val user: Usuario,
)

@Serializable
data class Usuario(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    /// `"admin"` ou `"user"`. Não vira booleano aqui: o servidor manda o papel,
    /// e reduzir a "é admin?" perderia o dia em que houver um terceiro.
    val role: String,
    @SerialName("is_active") val ativo: Boolean = true,
) {
    val eAdmin: Boolean get() = role == "admin"
}

/// `GET /api/auth/status` — a única rota que responde sem sessão.
@Serializable
data class StatusDoServidor(
    @SerialName("needs_setup") val precisaConfigurar: Boolean,
)

/// `POST /api/auth/media-token`
///
/// Token curto (8h) que **só abre bytes**: pôster, stream, legenda. Ele existe
/// porque `<img src>` e o player não mandam header, então precisa viajar na
/// query — e URL vaza pra log de acesso.
///
/// ⚠️ **Emitir um novo aposenta o anterior** (§43). Renovar no meio de um filme
/// derruba o próprio player. Ver `Cofre` e `RepositorioOdeon.garantirTokenDeMidia`.
@Serializable
data class TokenDeMidia(val token: String)

/// `GET /api/library` — uma linha por **série** ou obra avulsa.
///
/// ## Não é `/api/works`, e a diferença é a tela inteira
///
/// `/api/works` devolve obra por obra: os 14.657 episódios do acervo viram
/// 14.657 cartões iguais. A web já passou por isso e a conclusão está escrita
/// no `api.ts`: «é listagem de arquivo e não biblioteca».
///
/// `/api/library` agrupa. Uma série é uma linha, com quantos episódios tem e
/// quantos foram vistos.
///
/// ## Conferido contra o servidor em 04/08/2026
///
/// Campo a campo contra `LibraryEntry` de `routes/works.rs:443`. Duas
/// diferenças, as duas de propósito:
///
/// **1. `height` e `size_bytes` não estão aqui.** O servidor manda; a grade não
/// usa. O `ignoreUnknownKeys` do `Rede` os descarta sem reclamar — que é o que
/// permite o servidor ganhar campo novo sem quebrar o app, e o que obriga esta
/// nota a existir, porque a omissão é silenciosa.
///
/// **2. As contagens são `Int` aqui e `i64` lá.** Estreitamento deliberado:
/// `work_count`, `season_count`, `finished_count` e `total` contam obras, e o
/// acervo tem **17.930** contra os 2.147.483.647 que um `Int` aguenta. Se um dia
/// estourar, o kotlinx-serialization lança em vez de truncar — falha barulhenta,
/// não número errado.
@Serializable
data class ItemDaBiblioteca(
    val id: String,
    @SerialName("is_series") val eSerie: Boolean,
    val title: String,
    val year: Int? = null,
    /// Caminho relativo, servido em `/artwork/…`. Nulo enquanto não identificado.
    val poster: String? = null,
    /// A cor extraída do pôster pelo servidor — **9.332 obras já têm**. É o que
    /// deixa a interface se tingir com a obra sem custar requisição nenhuma.
    @SerialName("dominant_color") val corDominante: String? = null,
    @SerialName("work_count") val quantasObras: Int = 0,
    @SerialName("season_count") val quantasTemporadas: Int = 0,
    @SerialName("finished_count") val quantasVistas: Int = 0,
    @SerialName("media_file_id") val arquivoId: String? = null,
    @SerialName("duration_seconds") val duracaoEmSegundos: Double? = null,
    val kind: String? = null,
    @SerialName("match_state") val estadoDaIdentificacao: String? = null,
    @SerialName("position_seconds") val ondeParou: Double? = null,
    /// Repetido em toda linha: é o total de entradas do filtro atual.
    ///
    /// Vem de um `count(*) OVER ()` no servidor, então "300 de 17.498" não custa
    /// uma segunda requisição.
    val total: Int = 0,
)
