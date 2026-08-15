package dev.odeon.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/// Segura a tela acesa enquanto algo está tocando.
///
/// ## ⚠️ O sistema não sabe que você está assistindo
///
/// Relatado pelo dono: «quando fica assistindo filme, a TV acaba entrando em modo
/// screensaver». O sistema conta **interações** — tecla, toque, controle — e um
/// filme de duas horas não tem nenhuma. Do ponto de vista do Android, alguém que
/// assiste em silêncio é alguém que saiu da sala.
///
/// Nenhum dos três players desta casa dizia o contrário. É o tipo de defeito que
/// não aparece em teste nem em captura de tela: ele precisa de **tempo passando**
/// pra existir, e todo teste é apressado.
///
/// ## ⚠️ Só enquanto **toca**, e não enquanto a tela existe
///
/// A tentação é acender no `PlayerView` e esquecer. Mas um filme pausado é um
/// filme que alguém deixou pra lá — e segurar a tela de uma TV acesa a noite
/// inteira porque um player está aberto e parado é trocar um incômodo por um pior.
///
/// Pausou, o sistema volta a mandar. Voltou a tocar, a tela volta a ser nossa.
///
/// ## Por que `keepScreenOn` da `View` e não a flag da janela
///
/// `FLAG_KEEP_SCREEN_ON` mora na janela, e quem a liga precisa lembrar de
/// desligá-la — inclusive quando a tela morre por um caminho que ninguém previu.
/// O `keepScreenOn` da `View` morre **com a `View`**: o `onDispose` aqui é rede,
/// não é a única defesa.
@Composable
fun ManterATelaAcesa(enquanto: Boolean) {
    val vista = LocalView.current
    DisposableEffect(vista, enquanto) {
        vista.keepScreenOn = enquanto
        onDispose { vista.keepScreenOn = false }
    }
}
