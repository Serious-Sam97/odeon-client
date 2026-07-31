package dev.odeon.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.odeon.shared.OdeonRepository
import dev.odeon.shared.WorkListItem

/** Telas do app. Navegação minúscula e explícita — sem lib de rota pra três telas. */
sealed interface Screen {
    data object Library : Screen
    data object Server : Screen
    data class Playing(val work: WorkListItem) : Screen
}

@Composable
fun App(repository: OdeonRepository) {
    val state by repository.state.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }

    LaunchedEffect(Unit) { repository.checkAuth() }

    OdeonTheme {
        Box(Modifier.fillMaxSize().background(OdeonColors.bg)) {
            // Nada aparece antes de saber quem é. `null` = ainda perguntando.
            if (state.authenticated == null) return@Box

            if (state.authenticated == false) {
                LoginScreen(repository)
                return@Box
            }

            when (val current = screen) {
                is Screen.Server -> ServerScreen(
                    currentUrl = repository.baseUrl,
                    error = state.error,
                    onSave = {
                        repository.setBaseUrl(it)
                        screen = Screen.Library
                    },
                    onBack = { screen = Screen.Library },
                )

                is Screen.Library -> LibraryScreen(
                    repository = repository,
                    onPlay = { screen = Screen.Playing(it) },
                    onServer = { screen = Screen.Server },
                )

                is Screen.Playing -> PlayerScreen(
                    repository = repository,
                    work = current.work,
                    onClose = {
                        screen = Screen.Library
                        repository.refresh()
                    },
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(repository: OdeonRepository) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(repository.baseUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(26.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ODEON", color = OdeonColors.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.needsSetup) "Primeira execução" else "Entrar",
            color = OdeonColors.fg,
            fontSize = 24.sp,
        )

        Spacer(Modifier.height(18.dp))

        // O endereço fica junto do login de propósito: num app de celular, "não
        // conecta" quase sempre é o IP errado, não a senha.
        //
        // Basta o host: o app tenta https e cai pra http sozinho.
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("servidor  (ex: rog  ou  https://rog:8443)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("usuário") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("senha") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        (failure ?: state.error)?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = OdeonColors.danger, fontSize = 12.sp)
        }

        Spacer(Modifier.height(18.dp))
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                failure = null
                scope.launch {
                    // Sonda o endereço primeiro. Mandar senha pro lugar errado
                    // só produz "usuário ou senha incorretos" enganoso.
                    failure = repository.connect(url)
                        ?: repository.signIn(username, password, state.needsSetup)
                    busy = false
                }
            },
        ) {
            Text(if (state.needsSetup) "criar administrador" else "entrar")
        }
    }
}

@Composable
private fun ServerScreen(
    currentUrl: String,
    error: String?,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf(currentUrl) }

    Column(
        Modifier.fillMaxSize().padding(26.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ODEON", color = OdeonColors.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Endereço do servidor", color = OdeonColors.fg, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Na sua tailnet, algo como http://rog:8080. No emulador, 10.0.2.2 é o Mac.",
            color = OdeonColors.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = OdeonColors.danger, fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onSave(url) }) { Text("conectar") }
            Button(onClick = onBack) { Text("voltar") }
        }
    }
}

@Composable
private fun LibraryScreen(
    repository: OdeonRepository,
    onPlay: (WorkListItem) -> Unit,
    onServer: () -> Unit,
) {
    val state by repository.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◉", color = OdeonColors.accent, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "ODEON",
                color = OdeonColors.fg,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            state.user?.let {
                Text(it.displayName, color = OdeonColors.muted, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
            }
            Button(onClick = { repository.signOut() }) { Text("sair") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onServer) { Text("servidor") }
        }

        if (state.loading && state.works.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OdeonColors.accent)
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, end = 18.dp, bottom = 40.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (state.continueWatching.isNotEmpty()) {
                item(span = { GridItemSpanFull() }) {
                    Column {
                        SectionTitle("Continuar assistindo")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.continueWatching, key = { it.id }) { work ->
                                Box(Modifier.width(130.dp)) {
                                    PosterCard(repository, work, onPlay)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            item(span = { GridItemSpanFull() }) {
                SectionTitle("Biblioteca · ${state.works.size}")
            }

            items(state.works, key = { it.id }) { work ->
                PosterCard(repository, work, onPlay)
            }
        }
    }
}

/** `maxLineSpan` é o jeito de dizer "ocupe a linha toda" numa LazyVerticalGrid. */
private fun androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.GridItemSpanFull() =
    androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = OdeonColors.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
    )
}

@Composable
private fun PosterCard(
    repository: OdeonRepository,
    work: WorkListItem,
    onPlay: (WorkListItem) -> Unit,
) {
    val accent = parseHexColor(work.dominantColor) ?: OdeonColors.accent

    Column(
        Modifier.clickable(enabled = work.mediaFileId != null) { onPlay(work) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(OdeonColors.raised),
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
                // Sem artwork ainda: o card vira um bloco tingido, não um buraco.
                Box(
                    Modifier.fillMaxSize().background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        work.seriesTitle ?: work.title,
                        color = OdeonColors.fg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            if (work.progress > 0f) {
                LinearProgressIndicator(
                    progress = { work.progress },
                    color = accent,
                    trackColor = Color.Transparent,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        work.seriesTitle?.let {
            Text(it.uppercase(), color = accent, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            work.title,
            color = OdeonColors.fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(
                work.episodeLabel,
                work.year?.toString(),
                work.durationSeconds?.let { "${(it / 60).toInt()}min" },
            ).joinToString(" · "),
            color = OdeonColors.muted,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayerScreen(
    repository: OdeonRepository,
    work: WorkListItem,
    onClose: () -> Unit,
) {
    val mediaFileId = work.mediaFileId
    if (mediaFileId == null) {
        onClose()
        return
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                work.seriesTitle?.let {
                    Text(it.uppercase(), color = OdeonColors.accent, fontSize = 10.sp)
                }
                Text(
                    work.title,
                    color = OdeonColors.fg,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onClose) { Text("fechar") }
        }

        VideoPlayer(
            url = repository.streamUrl(mediaFileId),
            startPositionSeconds = work.positionSeconds ?: 0.0,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onProgress = { position, duration, eventType ->
                repository.reportProgress(work.id, position, duration, mediaFileId, eventType)
            },
        )
    }
}
