package dev.odeon.android.tv.home

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import dev.odeon.android.dados.ItemPraContinuar
import dev.odeon.android.tv.OdeonTv
import dev.odeon.android.tv.R

/// O Odeon **na primeira tela da TV**, e não só na gaveta de apps.
///
/// ## O que a home de uma Google TV é, por baixo
///
/// Ela parece um app, e não é: é uma pilha de fileiras montada a partir de uma
/// base de dados do sistema — a mesma tabela que guarda a grade de canais da TV
/// aberta, o `TvProvider`. Um app não desenha nada lá. Ele **escreve linhas**, e
/// o launcher da TCL as lê e desenha do jeito dele.
///
/// Isso muda o modelo mental inteiro. Não há "atualizar a tela": há inserir,
/// atualizar e apagar linhas, e elas continuam existindo com o app fechado,
/// desinstalado pela metade, ou depois de um restauro de backup. É mais parecido
/// com o widget da R9 do que com qualquer tela deste app.
///
/// ## São **duas** fileiras diferentes, e confundi-las é o erro comum
///
/// | | de quem é | quem escolhe o que entra |
/// |---|---|---|
/// | **canal do app** — a fileira "Odeon" | do Odeon | o Odeon |
/// | **Watch Next** — "Continue assistindo" | do **sistema** | o Odeon põe, a TV ordena |
///
/// A segunda é a que importa. Ela é a fileira onde a TCL junta o que está pela
/// metade em **todos** os apps — Netflix, YouTube, Odeon, na ordem de quem foi
/// visto por último. É a primeira fileira da home na maioria dos aparelhos, e
/// entrar nela é a diferença entre "o Odeon é um app que eu abro" e "o filme que
/// eu estava vendo está ali".
///
/// A primeira é uma vitrine: ela só aparece se a pessoa **escolher** fixá-la, e
/// por isso vale menos. Ela entra porque é barata — as mesmas obras, o mesmo
/// `Intent` — e porque é o lugar de mostrar o que não está pela metade.
///
/// ## O token nas artes, que é a dívida honesta deste arquivo
///
/// ⚠️ As URLs de pôster carregam `?token=` (§43), e quem baixa a imagem aqui
/// **não é este app** — é o processo do launcher, dias depois, possivelmente com
/// o Odeon nunca mais aberto. Quando o token de mídia rodar, as artes já
/// publicadas passam a devolver 401 e a fileira fica com os retângulos vazios.
///
/// Não há conserto limpo do lado do cliente: o `TvProvider` guarda uma `Uri`, e
/// não um jeito de pedir a imagem de novo com credencial nova. O que dá pra
/// fazer é o que está feito — **republicar a cada abertura do app**, que reescreve
/// as URLs com o token da vez. Quem abre o Odeon uma vez por semana tem a
/// fileira certa; quem passou um mês sem abrir pode ver arte faltando até abrir.
///
/// O conserto de verdade é do servidor: uma rota de arte que aceite um token
/// longo, de leitura só. Está anotado em `docs/PEDIDOS-AO-SERVIDOR.md`.
object CanalDaHome {

    private const val ETIQUETA = "OdeonTv/home"

    /// Onde fica o id do canal que este app criou.
    ///
    /// Ele **tem** que ser guardado: criar o canal devolve um id, e sem ele a
    /// segunda publicação criaria uma segunda fileira "Odeon" em vez de
    /// atualizar a primeira. Duas fileiras idênticas na home é o defeito clássico
    /// de app de TV, e ele só aparece na segunda execução.
    ///
    /// `SharedPreferences` e não o `Cofre` do `:core`, de propósito: isto não é
    /// segredo nem é do servidor — é um id local, e o `ReceptorDaHome` precisa
    /// lê-lo de forma síncrona, num `BroadcastReceiver`, onde abrir um DataStore
    /// seria trazer corrotina pra guardar um `Long`.
    private const val PREFS = "odeon-tv-home"
    private const val CHAVE_DO_CANAL = "id_do_canal"

    /// O esquema que o `AndroidManifest` registra. Um cartão da home dispara
    /// isto, e a `AtividadeDaTv` o recebe em `onNewIntent`.
    fun intencaoDaObra(obraId: String): Uri = "odeon-tv://obra/$obraId".toUri()

    /// Publica (ou atualiza) a fileira do app e a linha de "continuar" do
    /// sistema.
    ///
    /// Chamada de dois lugares, e os dois importam:
    ///
    ///  - do `ReceptorDaHome`, quando o **sistema** pede — na instalação e depois
    ///    de um restauro, ou seja antes de alguém abrir o app pela primeira vez
    ///  - da `AtividadeDaTv`, a cada abertura — que é o que reescreve as artes
    ///    com o token da vez, e o que reflete o que se assistiu no celular
    ///
    /// **Nunca lança.** Ela roda em caminho que ninguém está olhando, e uma
    /// exceção aqui derrubaria o app por causa de uma fileira decorativa. Cada
    /// falha vira uma linha no log com o que falhou — o §8b pede que o erro
    /// apareça, e aqui o lugar onde ele aparece é o `logcat`, porque não há tela.
    suspend fun publicar(contexto: Context) {
        val app = contexto.applicationContext as OdeonTv
        runCatching {
            /// Sem sessão não há o que publicar, e não é erro: é uma TV recém
            /// instalada, onde ninguém entrou ainda.
            if (!app.odeon.retomar()) {
                Log.i(ETIQUETA, "sem sessão — a home fica como está")
                return
            }
            app.odeon.garantirTokenDeMidia()

            val continuar = app.odeon.paraContinuar()
            publicarNoCanal(contexto, app, continuar)
            publicarNoWatchNext(contexto, app, continuar)
        }.onFailure { Log.w(ETIQUETA, "a home não foi publicada: $it") }
    }

    /// A fileira "Odeon" — a vitrine do app.
    ///
    /// ## ⚠️ O `@SuppressLint("RestrictedApi")` é obrigatório, e não é desleixo
    ///
    /// O lint reprova **oito** chamadas deste bloco:
    ///
    ///     Builder.setTitle can only be called from within the same library
    ///     (androidx.tvprovider:tvprovider)  [RestrictedApi]
    ///
    /// E é um defeito do artefato, não do uso. `PreviewProgram.Builder` herda os
    /// setters de `BasePreviewProgram.Builder`, que está anotada
    /// `@RestrictTo(LIBRARY_GROUP_PREFIX)` — mas ela é a **única** forma de
    /// montar um programa, e é exatamente o que a documentação do Android TV
    /// manda escrever. A anotação alcançou a classe-base junto com o resto.
    ///
    /// Ou seja: obedecer ao aviso é não publicar fileira nenhuma. Ele está
    /// silenciado **por função**, e não no `lint.xml` do módulo, pra que uma
    /// chamada restrita de verdade em qualquer outro arquivo continue reprovando
    /// o build.
    @SuppressLint("RestrictedApi")
    private fun publicarNoCanal(contexto: Context, app: OdeonTv, itens: List<ItemPraContinuar>) {
        val ajudante = PreviewChannelHelper(contexto)
        val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val canal = PreviewChannel.Builder()
            .setDisplayName(contexto.getString(R.string.canal_da_home))
            .setAppLinkIntentUri("odeon-tv://obra/".toUri())
            .setLogo(logo(contexto))
            .build()

        val guardado = prefs.getLong(CHAVE_DO_CANAL, -1L)
        val id = if (guardado > 0 && ajudante.getPreviewChannel(guardado) != null) {
            ajudante.updatePreviewChannel(guardado, canal)
            guardado
        } else {
            /// `publishDefaultChannel` e não `publishChannel`: o "default" pede
            /// ao launcher que a fileira já nasça **visível**, em vez de ficar
            /// esperando a pessoa achá-la na lista de canais disponíveis. É uma
            /// dica, não uma ordem — a TCL respeita pro primeiro canal de um app.
            val novo = ajudante.publishDefaultChannel(canal)
            prefs.edit { putLong(CHAVE_DO_CANAL, novo) }
            TvContractCompat.requestChannelBrowsable(contexto, novo)
            novo
        }

        /// Apaga os programas antigos antes de escrever os novos.
        ///
        /// ⚠️ Sem isto a fileira **cresce pra sempre**: `publishPreviewProgram`
        /// insere, não substitui, e um filme que saiu do "continuar" ficaria lá
        /// para sempre. Apagar e reescrever é mais simples do que reconciliar, e
        /// a lista tem dezenas de itens, não milhares.
        ///
        /// ## ⚠️ O `delete` vai numa URI **do canal**, e não com um `WHERE`
        ///
        /// A primeira versão passava a URI geral com
        /// `"channel_id = ?"` na seleção, que é como se apaga em qualquer outro
        /// `ContentProvider` do Android. O `TvProvider` recusa:
        ///
        ///     java.lang.SecurityException: Selection not allowed for
        ///     content://android.media.tv/preview_program
        ///
        /// Ele não aceita cláusula de seleção nenhuma nessas tabelas — a
        /// restrição existe justamente pra um app não conseguir escrever uma
        /// seleção que alcance as linhas **de outro** app. O caminho suportado é
        /// dizer o alvo na própria URI: `buildPreviewProgramsUriForChannel` já é
        /// «os programas deste canal», então a seleção vai nula.
        ///
        /// Medido na TCL em 12/08/2026: com o `WHERE`, a home ficava
        /// **completamente vazia** — nem canal, nem fileira —, e a única pista
        /// era esta linha no `logcat`, que só existe porque o `publicar` embrulha
        /// tudo num `runCatching` que loga (§8b).
        contexto.contentResolver.delete(
            TvContractCompat.buildPreviewProgramsUriForChannel(id),
            null,
            null,
        )

        itens.forEach { item ->
            runCatching {
                ajudante.publishPreviewProgram(
                    PreviewProgram.Builder()
                        .setChannelId(id)
                        .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                        .setTitle(item.title)
                        .setPosterArtUri(app.odeon.urlDoPoster(item.poster ?: item.arte)?.toUri())
                        .setPosterArtAspectRatio(
                            TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER,
                        )
                        .setIntentUri(intencaoDaObra(item.id))
                        /// A chave de quem é quem. É por ela que uma
                        /// republicação sabe que este cartão é o mesmo filme.
                        .setInternalProviderId(item.id)
                        .build(),
                )
            }.onFailure { Log.w(ETIQUETA, "«${item.title}» não entrou no canal: $it") }
        }
    }

    /// A fileira do **sistema** — "Continue assistindo".
    ///
    /// ## O `WATCH_NEXT_TYPE_CONTINUE` não é enfeite de enum
    ///
    /// Ele é o que diz à TV que isto está **pela metade**, e é o único tipo que
    /// a home ordena por quando-foi-visto. Os outros três dizem outra coisa:
    /// `NEXT` é o próximo episódio de quem terminou um, `NEW` é episódio que
    /// acabou de sair, `WATCHLIST` é o que a pessoa marcou pra depois. Usar o
    /// tipo errado põe o filme na fileira certa com a ordem errada.
    ///
    /// ## Terminar **tira** da fileira, e isso é obrigação e não gentileza
    ///
    /// Uma linha de Watch Next que sobrevive ao fim do filme é o app dizendo
    /// «você parou no meio disto» sobre algo que a pessoa terminou. O `finished`
    /// do `/api/continue` é a resposta, e ele já chega no modelo.
    @SuppressLint("RestrictedApi")
    private fun publicarNoWatchNext(
        contexto: Context,
        app: OdeonTv,
        itens: List<ItemPraContinuar>,
    ) {
        itens.forEach { item ->
            runCatching {
                val posicaoMs = ((item.ondeParou ?: 0.0) * 1000).toLong()
                val duracaoMs = (item.duracaoEmSegundos ?: 0.0).times(1000).toLong()

                if (item.finished == true || duracaoMs <= 0L || posicaoMs <= 0L) {
                    tirarDoWatchNext(contexto, item.id)
                    return@runCatching
                }

                tirarDoWatchNext(contexto, item.id)

                PreviewChannelHelper(contexto).publishWatchNextProgram(
                    WatchNextProgram.Builder()
                        .setType(TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                        .setWatchNextType(
                            TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE,
                        )
                        .setTitle(item.title)
                        .setPosterArtUri(app.odeon.urlDoPoster(item.arte)?.toUri())
                        .setPosterArtAspectRatio(
                            TvContractCompat.WatchNextPrograms.ASPECT_RATIO_16_9,
                        )
                        .setLastPlaybackPositionMillis(posicaoMs.toInt())
                        .setDurationMillis(duracaoMs.toInt())
                        /// ⚠️ **Obrigatório**, e é o campo por onde a home
                        /// ordena. Sem ele o `insert` até passa, e o cartão
                        /// aparece no fim da fileira pra sempre — que é o mesmo
                        /// que não aparecer.
                        .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                        .setIntentUri(intencaoDaObra(item.id))
                        .setInternalProviderId(item.id)
                        .build(),
                )
            }.onFailure { Log.w(ETIQUETA, "«${item.title}» não entrou no continuar: $it") }
        }
    }

    /// Tira uma obra da fileira do sistema.
    ///
    /// ## ⚠️ Procura e apaga **linha por linha**, e não por seleção
    ///
    /// Mesma restrição do `publicarNoCanal` acima: o `TvProvider` recusa
    /// cláusula de seleção. E aqui não há uma URI «do meu app» pra pedir de uma
    /// vez — o Watch Next é a fileira **do sistema**, dividida com todo mundo.
    ///
    /// Então o caminho é: ler a tabela inteira (o provedor já devolve só as
    /// linhas deste app), achar as do `internalProviderId` pedido **em código**,
    /// e apagar cada uma pela URI dela. É mais verboso e é o que funciona.
    fun tirarDoWatchNext(contexto: Context, obraId: String) {
        runCatching {
            contexto.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(
                    TvContractCompat.WatchNextPrograms._ID,
                    TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == obraId) {
                        contexto.contentResolver.delete(
                            TvContractCompat.buildWatchNextProgramUri(cursor.getLong(0)),
                            null,
                            null,
                        )
                    }
                }
            }
        }.onFailure { Log.w(ETIQUETA, "$obraId não saiu do continuar: $it") }
    }

    /// O logotipo da fileira, do vetor pra um `Bitmap`.
    ///
    /// O `PreviewChannel` aceita `Uri` ou `Bitmap`, e aqui só o segundo serve: a
    /// `Uri` teria de apontar pra um arquivo que o processo do launcher consiga
    /// abrir, e um `res/drawable` do nosso APK não é isso. Rasterizar na hora
    /// custa um bitmap de 80x80 uma vez por publicação.
    private fun logo(contexto: Context) =
        requireNotNull(contexto.getDrawable(R.mipmap.ic_launcher))
            .toBitmap(width = 80, height = 80)
}
