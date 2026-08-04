package dev.odeon.android.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.odeon.android.OdeonApp
import dev.odeon.android.dados.ItemPraContinuar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/// O widget de "continuar assistindo" — o último item da R9.
///
/// > «Widget de "continuar assistindo" — a mesma rota `/api/continue`, sem
/// > tela. A §4b da espec já lista isso como coisa que o servidor dá de graça.»
///
/// ## Ele não compartilha nada com o app, e isso não é desleixo
///
/// Um widget é desenhado **pelo launcher**, a partir de `RemoteViews` — não pelo
/// processo do Odeon. O Glance dá a sintaxe do Compose e gera `RemoteViews` por
/// baixo, e é por isso que nada de `ui/` entra aqui: `Cores`, `Tipo`,
/// `RotuloDeSecao` e a serifada embutida são de `androidx.compose`, e o
/// `RemoteViews` não sabe representar nenhum deles.
///
/// As cores estão repetidas literalmente logo abaixo. É duplicação, é chata, e é
/// a única forma — a alternativa seria um módulo de constantes compartilhado
/// entre dois mundos que não se encontram em tempo de execução.
///
/// ## O dado vem direto do repositório, sem `ViewModel`
///
/// `provideGlance` é `suspend`, então a busca acontece aqui mesmo. Não há ciclo
/// de vida de tela pra respeitar: o launcher pede o conteúdo, o widget devolve
/// uma vez, e fica parado até o próximo pedido.
///
/// ⚠️ **É por isso que ele não se atualiza sozinho ao assistir algo.** O
/// launcher repede a cada `updatePeriodMillis` (ver `res/xml/widget_continuar.xml`),
/// e nada empurra do app pra cá. Empurrar exigiria chamar
/// `WidgetDeContinuar().updateAll(contexto)` ao voltar do player — uma linha, e
/// está anotada como pendência porque muda o `AppOdeon`, que é a raiz.
class WidgetDeContinuar : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as OdeonApp

        /// `retomar()` antes de perguntar: o widget pode ser desenhado com o app
        /// nunca aberto desde o boot, e aí não há sessão em memória — só no
        /// `Cofre`. Sem isto, o widget de quem reiniciou o celular diria "entre
        /// no Odeon" mesmo com a sessão guardada.
        val temSessao = runCatching { app.odeon.retomar() }.getOrDefault(false)
        val itens = if (temSessao) {
            runCatching { app.odeon.paraContinuar() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        /// As capas são baixadas **aqui**, como bytes, e viram `Bitmap`.
        ///
        /// O Glance não aceita URL: `ImageProvider` quer um recurso ou um
        /// bitmap, porque quem desenha é outro processo e ele não vai buscar
        /// nada na rede por conta. O Coil tem um `ImageProvider` próprio pra
        /// Glance, e ele é outro artefato — pra três miniaturas, o OkHttp que já
        /// está montado resolve.
        ///
        /// **Três, e não todas**: cada bitmap atravessa a fronteira de processo
        /// dentro do `RemoteViews`, e o `Binder` tem um limite de ~1 MB por
        /// transação. Estourá-lo derruba o widget inteiro com
        /// `TransactionTooLargeException` — e o sintoma é um retângulo cinza no
        /// launcher, sem erro em lugar nenhum.
        val comCapa = itens.take(3).map { item ->
            item to baixarCapa(app, item)
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(FUNDO)
                    .padding(12.dp)
                    /// Tocar em qualquer lugar do widget abre o app.
                    ///
                    /// A intenção é montada à mão em vez de
                    /// `actionStartActivity<AtividadePrincipal>()` por um motivo
                    /// concreto: a versão com reificação resolve a classe, mas
                    /// **não** o pacote — e o debug instala como
                    /// `dev.odeon.android.debug`. Com o `ComponentName` montado a
                    /// partir do `context.packageName`, o widget do debug abre o
                    /// debug e o de produção abre a produção. É a mesma armadilha
                    /// do `targetPackage` dos atalhos, do outro lado.
                    .clickable(
                        androidx.glance.appwidget.action.actionStartActivity(
                            android.content.Intent().setComponent(
                                android.content.ComponentName(
                                    context.packageName,
                                    "dev.odeon.android.AtividadePrincipal",
                                ),
                            ),
                        ),
                    ),
            ) {
                Text(
                    text = "CONTINUAR",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(DESTAQUE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )

                androidx.glance.layout.Spacer(GlanceModifier.height(8.dp))

                /// §24 fora do app: sem nada pra continuar, o widget **diz** isso
                /// em vez de ficar em branco. Um widget vazio no launcher lê como
                /// widget quebrado, e a pessoa o remove.
                if (comCapa.isEmpty()) {
                    Text(
                        text = if (temSessao) "nada pela metade" else "entre no Odeon",
                        style = TextStyle(
                            color = androidx.glance.unit.ColorProvider(APAGADO),
                            fontSize = 13.sp,
                        ),
                    )
                } else {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        comCapa.forEach { (item, capa) ->
                            Column(
                                modifier = GlanceModifier.padding(end = 8.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                if (capa != null) {
                                    Image(
                                        provider = ImageProvider(capa),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = GlanceModifier.size(width = 64.dp, height = 96.dp),
                                    )
                                } else {
                                    /// Sem capa, um retângulo da cor elevada com
                                    /// o título dentro — o mesmo recurso da
                                    /// grade, que 48% do acervo exige.
                                    Column(
                                        modifier = GlanceModifier
                                            .size(width = 64.dp, height = 96.dp)
                                            .background(ELEVADO)
                                            .padding(4.dp),
                                    ) {
                                        Text(
                                            text = item.tituloDaSerie ?: item.title,
                                            maxLines = 4,
                                            style = TextStyle(
                                                color = androidx.glance.unit.ColorProvider(TEXTO),
                                                fontSize = 9.sp,
                                            ),
                                        )
                                    }
                                }

                                Text(
                                    text = quantoFalta(item),
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = androidx.glance.unit.ColorProvider(APAGADO),
                                        fontSize = 10.sp,
                                    ),
                                    modifier = GlanceModifier.width(64.dp).padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /// A capa, em bytes, pela **mesma** instância de OkHttp do resto do app.
    ///
    /// É a quarta coisa a usar aquela instância — Retrofit, Coil, Media3 e agora
    /// isto —, e pelo mesmo motivo de sempre: um pool, um cache, um lugar onde o
    /// token é posto. Um cliente próprio aqui abriria conexão nova pra buscar uma
    /// imagem que o Coil provavelmente já tem em disco.
    ///
    /// Falha de rede devolve `null` e o widget desenha o cartão sem capa. Um
    /// widget que some porque a foto não baixou seria pior que um sem foto.
    /// ⚠️ `withContext(Dispatchers.IO)`, e **sem isso as capas não aparecem**.
    ///
    /// O `provideGlance` é `suspend`, mas não é `suspend` **na IO** — ele roda no
    /// despachante que o Glance escolher, e `execute()` do OkHttp é bloqueante.
    /// Fora da IO isso vira `NetworkOnMainThreadException`, que o `runCatching`
    /// engole e devolve `null`.
    ///
    /// O sintoma foi exatamente esse, visto no launcher: o widget desenhou os
    /// três títulos e os três "faltam Nmin" — ou seja, a **rota** funcionou —
    /// e as três capas caíram no cartão sem arte, como se o acervo inteiro não
    /// tivesse pôster. Um erro de despachante disfarçado de dado ausente, que é
    /// o pior tipo: nada no log, e a tela parecendo certa.
    private suspend fun baixarCapa(app: OdeonApp, item: ItemPraContinuar): android.graphics.Bitmap? =
        withContext(Dispatchers.IO) {
            val url = app.odeon.urlDaArte(item.arte) ?: return@withContext null
            runCatching {
                app.odeon.clienteHttp().newCall(Request.Builder().url(url).build())
                    .execute()
                    .use { resposta -> resposta.body?.byteStream()?.let(BitmapFactory::decodeStream) }
            }.getOrNull()
        }

    /// ⚠️ **Sem o "faltam", e o screenshot é que mandou.**
    ///
    /// A primeira versão escrevia "faltam 141min", como a fileira de continuar
    /// dentro do app. No launcher ela saiu `faltam 141m…`: a coluna tem os
    /// mesmos 64dp da capa, e a frase inteira não cabe em 10sp.
    ///
    /// Cortar a palavra e não o número é o certo — o número é o dado, e o
    /// rótulo "CONTINUAR" logo acima já diz que se trata do que falta. Alargar a
    /// coluna seria o outro caminho, e não cabe: três colunas de 76dp mais os
    /// vãos passam dos 250dp que o widget declara como largura mínima.
    private fun quantoFalta(item: ItemPraContinuar): String {
        val total = item.duracaoEmSegundos ?: return item.tituloDaSerie ?: item.title
        val onde = item.ondeParou ?: 0.0
        val faltam = ((total - onde) / 60).toInt()
        return if (faltam > 0) "${faltam} min" else "no fim"
    }

    private companion object {
        /// ⚠️ A paleta, repetida à mão.
        ///
        /// O `ui/Tema.kt` é `androidx.compose.ui.graphics.Color` dentro do
        /// processo do app; aqui é `ColorProvider` dentro do `RemoteViews` do
        /// launcher. São os mesmos valores hexadecimais e **não** o mesmo objeto,
        /// e é o preço de o widget morar do outro lado da fronteira.
        ///
        /// Se a paleta mudar no `Tema.kt`, muda aqui também. Está escrito porque
        /// duplicação que ninguém sabe que existe é a que envelhece torta.
        val FUNDO = Color(0xFF0A0A0C)
        val ELEVADO = Color(0xFF131318)
        val TEXTO = Color(0xFFECEEF4)
        val APAGADO = Color(0xFF8B8D9A)
        val DESTAQUE = Color(0xFFE0B062)
    }
}

/// O receptor, que é o que o sistema instancia. O widget em si não é um
/// componente do Android — este é.
class ReceptorDoWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetDeContinuar()
}
