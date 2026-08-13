package dev.odeon.android.tv.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/// Quem atende quando a **TV** pede as fileiras.
///
/// ## O gancho é o certo, e o óbvio seria o errado
///
/// A alternativa é criar o canal no `onCreate` da Activity. Funciona — e só
/// depois de alguém **abrir** o app pela primeira vez. Mas a graça de uma
/// fileira na home é justamente não precisar abrir o app: quem instalou o Odeon
/// ontem e vê "continuar assistindo" hoje na primeira tela nunca navegou até a
/// gaveta de apps.
///
/// O `INITIALIZE_PROGRAMS` é a Google TV dizendo «publique agora». Ela o manda:
///
///  - logo depois da instalação
///  - depois de um restauro de backup, quando as linhas antigas se foram e o app
///    continua instalado — o caso em que criar-no-`onCreate` deixaria a home
///    vazia até a pessoa reabrir o app por conta própria
///
/// ## O `goAsync`, e por que ele não é opcional aqui
///
/// Um `BroadcastReceiver` é morto assim que o `onReceive` retorna — e o processo
/// junto, se não houver mais nada de pé. Publicar as fileiras é rede: retomar a
/// sessão, pedir `/api/continue`, escrever no `TvProvider`. Sem o `goAsync`, a
/// corrotina começaria e o sistema mataria o processo no meio, deixando a home
/// com as linhas pela metade.
///
/// `PendingResult.finish()` é o contrato: enquanto ele não é chamado, o sistema
/// segura o processo. É por isso que ele está num `finally` — esquecê-lo é
/// segurar o processo pra sempre, que é o defeito oposto e pior.
class ReceptorDaHome : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intencao: Intent) {
        val pendente = goAsync()
        val app = contexto.applicationContext

        CoroutineScope(SupervisorJob()).launch {
            try {
                CanalDaHome.publicar(app)
            } finally {
                pendente.finish()
            }
        }
    }
}
