package dev.odeon.android.dados

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.armazem: DataStore<Preferences> by preferencesDataStore(name = "odeon")

/// Onde ficam o endereço do servidor e os dois tokens.
///
/// ## O que ele protege, e o que não
///
/// **Nada é cifrado.** O arquivo vive na área privada do app
/// (`/data/data/dev.odeon.android/…`), que o Android já isola de outros apps —
/// alcançá-lo exige o aparelho comprometido, e num aparelho comprometido a
/// chave que decifraria também está ao alcance.
///
/// É o mesmo nível da web, que guarda o token no `localStorage`. Dizer isto por
/// extenso é melhor do que uma camada de criptografia que parece proteger mais
/// do que protege.
///
/// ## Os dois tokens são diferentes, e confundi-los quebra o player
///
/// | | vale | vai em | quem usa |
/// |---|---|---|---|
/// | sessão | 90 dias | `Authorization: Bearer` | toda rota de API |
/// | mídia | 8 horas | `?token=` na URL | pôster, stream, legenda |
///
/// ⚠️ **Emitir um token de mídia novo aposenta o anterior** (§43). O anterior é
/// o que está dentro do player que está tocando, e — na fase 4 — dentro do
/// Chromecast, que morreria sem o celular perceber. Por isso `tokenDeMidia` é
/// lido, e só é pedido um novo quando **não há nenhum**.
class Cofre(contexto: Context) {

    private val armazem = contexto.applicationContext.armazem

    /// Cópia em memória do token de sessão.
    ///
    /// O interceptor do OkHttp roda numa thread de rede e é **síncrono** — ele
    /// não pode suspender pra ler o DataStore. Um `runBlocking` ali bloquearia
    /// a chamada em cada requisição.
    ///
    /// `@Volatile` porque quem escreve é a corrotina do login e quem lê é a
    /// thread do OkHttp.
    @Volatile
    var sessaoEmMemoria: String? = null
        private set

    @Volatile
    var midiaEmMemoria: String? = null
        private set

    val servidor: Flow<String?> = armazem.data.map { it[CHAVE_SERVIDOR] }
    val sessao: Flow<String?> = armazem.data.map { it[CHAVE_SESSAO] }

    /// O identificador deste aparelho, pro servidor separar "onde eu parei".
    ///
    /// Nasce uma vez e vive enquanto o app estiver instalado. **Não** é o
    /// `ANDROID_ID` nem nada que identifique o aparelho fora daqui: é um número
    /// aleatório que só faz sentido dentro deste Odeon, e some com o app.
    ///
    /// A tese do projeto depende dele — continuar na TV o que começou no ônibus
    /// só funciona se o servidor souber que foram dois lugares diferentes.
    @Volatile
    var aparelhoEmMemoria: String? = null
        private set

    /// O maior instante já visto, em epoch ms.
    ///
    /// É o que impede atrasar o relógio de estender um empréstimo baixado —
    /// toda a explicação está em `RelogioQueNaoVolta`. Fica em memória **e** em
    /// disco: em memória porque quem pergunta é a tela, e em disco porque um app
    /// reaberto com a hora atrasada precisa lembrar do que já viu.
    @Volatile
    var maiorInstanteVisto: Long = 0L
        private set

    /// Registra um instante — do relógio local ou do cabeçalho `Date` do
    /// servidor. Só grava quando **sobe**, que é a regra inteira.
    suspend fun viuOInstante(quando: Long) {
        if (quando <= maiorInstanteVisto) return
        maiorInstanteVisto = quando
        armazem.edit { it[CHAVE_MAIOR_INSTANTE] = quando }
    }

    /// Chamado uma vez, no arranque, antes de qualquer requisição.
    ///
    /// Sem isto a primeira chamada depois de reabrir o app sai sem `Bearer` e
    /// toma 401 — e o app pareceria ter esquecido o login que não esqueceu.
    suspend fun aquecer() {
        val atual = armazem.data.first()
        sessaoEmMemoria = atual[CHAVE_SESSAO]
        midiaEmMemoria = atual[CHAVE_MIDIA]

        maiorInstanteVisto = maxOf(atual[CHAVE_MAIOR_INSTANTE] ?: 0L, System.currentTimeMillis())

        aparelhoEmMemoria = atual[CHAVE_APARELHO] ?: java.util.UUID.randomUUID().toString().also {
            armazem.edit { prefs -> prefs[CHAVE_APARELHO] = it }
        }
    }

    /// Os canais fixados, na ordem em que foram fixados.
    ///
    /// ⚠️ Guardados **neste aparelho** e não no servidor, e é decisão: «quais são
    /// os meus canais» é pergunta da sala, não da casa. A TV da cozinha pode
    /// querer outros três, e a conta do servidor é a mesma.
    ///
    /// Separados por quebra de linha porque id de canal pode conter vírgula — e
    /// um separador que aparece no dado é um separador que um dia parte o dado ao
    /// meio.
    suspend fun guardarFavoritosDeCanal(ids: List<String>) {
        armazem.edit { it[CHAVE_CANAIS_FIXADOS] = ids.joinToString("\n") }
    }

    suspend fun favoritosDeCanal(): List<String> =
        armazem.data.first()[CHAVE_CANAIS_FIXADOS]
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    suspend fun guardarServidor(url: String) {
        armazem.edit { it[CHAVE_SERVIDOR] = url }
    }

    suspend fun servidorAgora(): String? = armazem.data.first()[CHAVE_SERVIDOR]

    suspend fun guardarSessao(token: String) {
        sessaoEmMemoria = token
        armazem.edit { it[CHAVE_SESSAO] = token }
    }

    suspend fun guardarMidia(token: String) {
        midiaEmMemoria = token
        armazem.edit { it[CHAVE_MIDIA] = token }
    }

    /// Sair.
    ///
    /// O **servidor fica**. Quem sai da conta quase nunca quer redigitar o
    /// endereço da própria casa — e o endereço não é segredo.
    suspend fun esquecerSessao() {
        sessaoEmMemoria = null
        midiaEmMemoria = null
        armazem.edit {
            it.remove(CHAVE_SESSAO)
            it.remove(CHAVE_MIDIA)
        }
    }

    private companion object {
        val CHAVE_SERVIDOR = stringPreferencesKey("servidor")
        val CHAVE_CANAIS_FIXADOS = stringPreferencesKey("canais_fixados")
        val CHAVE_SESSAO = stringPreferencesKey("sessao")
        val CHAVE_MIDIA = stringPreferencesKey("midia")

        /// Fora do `esquecerSessao` de propósito: sair da conta não faz o
        /// aparelho virar outro aparelho.
        val CHAVE_APARELHO = stringPreferencesKey("aparelho")

        /// Fora do `esquecerSessao` também: sair da conta não faz o tempo
        /// voltar.
        val CHAVE_MAIOR_INSTANTE = androidx.datastore.preferences.core.longPreferencesKey("maior_instante")
    }
}
