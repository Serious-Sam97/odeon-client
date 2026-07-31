package dev.odeon.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.odeon.shared.OdeonRepository
import dev.odeon.shared.WorkListItem

/**
 * Margem de overscan. Em TV, a borda física costuma comer alguns por cento da
 * imagem — conteúdo colado na borda some. 48dp é a recomendação do Android TV.
 */
private val OVERSCAN_H = 48.dp
private val OVERSCAN_V = 27.dp

internal val Bg = Color(0xFF0A0A0C)
internal val Raised = Color(0xFF131318)
internal val Fg = Color(0xFFECEEF4)
internal val Muted = Color(0xFF8B8D9A)
internal val Accent = Color(0xFFE0B062)

@Composable
fun TvApp(repository: OdeonRepository) {
    val state by repository.state.collectAsState()
    var playing by remember { mutableStateOf<WorkListItem?>(null) }

    LaunchedEffect(Unit) { repository.checkAuth() }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            onBackground = Fg,
            surface = Raised,
            onSurface = Fg,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            val current = playing
            when {
                state.authenticated == null -> Unit

                state.authenticated == false -> TvLogin(repository)

                current != null -> TvPlayer(
                    repository = repository,
                    work = current,
                    onExit = {
                        playing = null
                        repository.refresh()
                    },
                )

                state.loading && state.works.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }

                state.error != null && state.works.isEmpty() ->
                    ConnectionError(repository.baseUrl, state.error!!)

                else -> TvHome(repository, onPlay = { playing = it })
            }
        }
    }
}

/**
 * Login na TV.
 *
 * Digitar senha com D-pad é ruim em qualquer app de TV — não há como resolver
 * isso aqui. O que dá pra fazer é o que está feito: campos grandes, foco
 * evidente, e o endereço do servidor junto, porque na TV "não conecta" quase
 * sempre é o IP errado. Configurar pelo celular primeiro continua sendo o
 * caminho mais confortável.
 */
@Composable
private fun TvLogin(repository: OdeonRepository) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val firstField = remember { FocusRequester() }

    var url by remember { mutableStateOf(repository.baseUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 120.dp, vertical = OVERSCAN_V),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ODEON", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            if (state.needsSetup) "Primeira execução" else "Entrar",
            color = Fg,
            fontSize = 34.sp,
        )
        Spacer(Modifier.height(26.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("servidor  (ex: rog)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(firstField),
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("usuário") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("senha") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        (failure ?: state.error)?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = Color(0xFFFF6B6B), fontSize = 15.sp)
        }

        Spacer(Modifier.height(26.dp))
        Button(onClick = {
            failure = null
            scope.launch {
                failure = repository.connect(url)
                    ?: repository.signIn(username, password, state.needsSetup)
            }
        }) {
            Text(if (state.needsSetup) "criar administrador" else "entrar", fontSize = 17.sp)
        }
    }
}

@Composable
private fun ConnectionError(baseUrl: String, error: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = OVERSCAN_H, vertical = OVERSCAN_V),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Não consegui falar com o servidor", color = Fg, fontSize = 30.sp)
        Spacer(Modifier.height(10.dp))
        Text(baseUrl, color = Accent, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Text(error, color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            "A TV não tem teclado bom pra digitar URL. Ajuste o endereço pelo celular " +
                "ou deixe o servidor com um nome fixo na tailnet.",
            color = Muted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun TvHome(repository: OdeonRepository, onPlay: (WorkListItem) -> Unit) {
    val state by repository.state.collectAsState()
    val firstItem = remember { FocusRequester() }

    // Sem foco inicial explícito, o D-pad não tem de onde partir e a primeira
    // seta do controle não faz nada — clássico de app de TV mal feito.
    LaunchedEffect(state.works.isNotEmpty(), state.continueWatching.isNotEmpty()) {
        runCatching { firstItem.requestFocus() }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = OVERSCAN_H, end = OVERSCAN_H,
            top = OVERSCAN_V, bottom = OVERSCAN_V,
        ),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◉", color = Accent, fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Text("ODEON", color = Fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${state.works.size} obras", color = Muted, fontSize = 15.sp)
            }
        }

        if (state.continueWatching.isNotEmpty()) {
            item {
                TvRow(
                    title = "Continuar assistindo",
                    works = state.continueWatching,
                    repository = repository,
                    onPlay = onPlay,
                    firstFocus = firstItem,
                )
            }
        }

        item {
            TvRow(
                title = "Biblioteca",
                works = state.works,
                repository = repository,
                onPlay = onPlay,
                firstFocus = if (state.continueWatching.isEmpty()) firstItem else null,
            )
        }
    }
}

@Composable
private fun TvRow(
    title: String,
    works: List<WorkListItem>,
    repository: OdeonRepository,
    onPlay: (WorkListItem) -> Unit,
    firstFocus: FocusRequester?,
) {
    Column {
        Text(
            title.uppercase(),
            color = Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(works, key = { it.id }) { work ->
                TvCard(
                    work = work,
                    repository = repository,
                    onPlay = onPlay,
                    modifier = if (firstFocus != null && work.id == works.first().id) {
                        Modifier.focusRequester(firstFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun TvCard(
    work: WorkListItem,
    repository: OdeonRepository,
    onPlay: (WorkListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    // Escala no foco é como o usuário sabe onde está — em TV não há cursor.
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "focus")

    Column(
        modifier
            .width(150.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (isSelect && event.type == KeyEventType.KeyUp && work.mediaFileId != null) {
                    onPlay(work)
                    true
                } else {
                    false
                }
            },
    ) {
        Box(
            Modifier
                .size(width = 150.dp, height = 225.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Raised)
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) Accent else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
        ) {
            val poster = work.poster
            if (poster != null) {
                AsyncImage(
                    model = repository.artworkUrl(poster),
                    contentDescription = work.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().padding(10.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        work.seriesTitle ?: work.title,
                        color = Fg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (work.progress > 0f) {
                LinearProgressIndicator(
                    progress = { work.progress },
                    color = Accent,
                    trackColor = Color.Transparent,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            work.title,
            color = if (focused) Fg else Muted,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
