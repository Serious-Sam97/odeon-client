package dev.odeon.android.aovivo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.odeon.android.AtividadePrincipal
import dev.odeon.android.OdeonApp
import dev.odeon.android.ui.aovivo.emMillis
import java.util.concurrent.TimeUnit

/// Quanto antes o aviso chega.
///
/// ⚠️ Dez minutos, escolhidos pelo dono. É o número que decide **o que a frase
/// pode dizer**: com cinco, «começa em cinco minutos» é um susto; com trinta,
/// vira agenda e a pessoa esquece de novo. Dez dá tempo de sentar.
private const val MINUTOS_DE_ANTECEDENCIA = 10

private const val CANAL_DOS_LEMBRETES = "lembretes-do-ao-vivo"
private const val TRABALHO = "lembretes-do-ao-vivo"

/// O aviso de que um programa marcado está para começar.
///
/// ## ⚠️ Por que isto mora no celular e o lembrete mora na TV
///
/// O lembrete é marcado na sala, na grade do ao vivo, e é **guardado no
/// servidor**. Mas o aviso não serve numa TV: quem marcou «me avise» não está na
/// frente dela — se estivesse, não precisaria de aviso. O celular é o aparelho
/// que anda com a pessoa, e é onde o aviso encontra alguém.
///
/// ## ⚠️ Por que é um trabalho periódico e não só um alarme
///
/// Um `AlarmManager` sabe disparar numa hora marcada, e é o que dispara este
/// aviso. Mas alguém precisa **descobrir** os lembretes: eles nascem na TV, e o
/// celular não fica sabendo de nada. Sem uma pergunta periódica, um lembrete
/// marcado às 18h para as 20h40 só seria visto se o celular fosse aberto no
/// meio-tempo — que é justamente o que a pessoa não vai fazer, porque marcou
/// exatamente pra não ter de lembrar.
///
/// Quinze minutos é o piso do `WorkManager` e serve: o trabalho agenda tudo o que
/// começa na **próxima hora**, então uma janela de quinze minutos nunca perde
/// nada por atraso de descoberta.
class TrabalhoDosLembretes(
    contexto: Context,
    parametros: WorkerParameters,
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OdeonApp ?: return Result.success()

        /// ⚠️ Sem sessão não há pergunta a fazer, e **não é falha**: é alguém que
        /// ainda não entrou, ou que saiu. Devolver `retry` aqui poria o sistema a
        /// tentar de novo pra sempre por uma conta que não existe.
        if (!app.odeon.retomar()) return Result.success()

        val lembretes = app.odeon.lembretes()
        if (lembretes.isEmpty()) return Result.success()

        /// A grade é a única fonte que sabe **quando** cada programa começa — o
        /// lembrete guarda o id, não o horário.
        val guia = app.odeon.guiaAoVivo(horas = 2) ?: return Result.success()
        val porId = guia.programas.associateBy { it.id }

        val agora = System.currentTimeMillis()
        val ate = agora + TimeUnit.HOURS.toMillis(1)

        lembretes.forEach { lembrete ->
            val programa = porId[lembrete.programaId] ?: return@forEach
            val comeca = emMillis(programa.comeca)
            if (comeca <= 0) return@forEach

            val quando = comeca - TimeUnit.MINUTES.toMillis(MINUTOS_DE_ANTECEDENCIA.toLong())
            /// ⚠️ Só o que cabe na próxima hora. O resto será visto por uma
            /// rodada seguinte — e agendar tudo desde já encheria o sistema de
            /// alarmes que ainda podem ser desmarcados na TV.
            if (quando < agora || quando > ate) return@forEach

            agendarOAviso(
                contexto = applicationContext,
                programaId = programa.id,
                titulo = programa.title,
                quando = quando,
            )
        }
        return Result.success()
    }
}

/// Marca a hora exata do aviso.
///
/// ⚠️ `setExactAndAllowWhileIdle` e não `setExact`: com o celular no bolso e a
/// tela apagada há horas, o Doze adiaria um alarme comum — e um aviso de «começa
/// em 10 minutos» que chega às 21h05 não é um aviso atrasado, é lixo.
///
/// ⚠️ Se o sistema **negar** o alarme exato (Android 12+ pode exigir permissão),
/// cai num inexato em vez de estourar. Um aviso com alguns minutos de folga ainda
/// serve; um app que fecha na cara de alguém não.
private fun agendarOAviso(contexto: Context, programaId: Int, titulo: String, quando: Long) {
    val alarmes = contexto.getSystemService(android.app.AlarmManager::class.java) ?: return
    val intencao = PendingIntent.getBroadcast(
        contexto,
        programaId,
        Intent(contexto, ReceptorDoLembrete::class.java)
            .putExtra(EXTRA_TITULO, titulo)
            .putExtra(EXTRA_ID, programaId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val podeExato = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmes.canScheduleExactAlarms()
    runCatching {
        if (podeExato) {
            alarmes.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, quando, intencao)
        } else {
            alarmes.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, quando, intencao)
        }
    }
}

internal const val EXTRA_TITULO = "titulo"
internal const val EXTRA_ID = "id"

/// Quem escreve o aviso quando a hora chega.
class ReceptorDoLembrete : android.content.BroadcastReceiver() {
    override fun onReceive(contexto: Context, intencao: Intent) {
        val titulo = intencao.getStringExtra(EXTRA_TITULO) ?: return
        val id = intencao.getIntExtra(EXTRA_ID, 0)

        /// ⚠️ A permissão é conferida **aqui**, e não só na hora de pedir. Entre
        /// marcar o lembrete e a hora dele a pessoa pode ter desligado as
        /// notificações do app — e postar sem permissão é uma exceção no meio de
        /// um `BroadcastReceiver`, que derruba o processo.
        val permitido = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitido) return

        criarOCanal(contexto)

        val abrir = PendingIntent.getActivity(
            contexto,
            id,
            Intent(contexto, AtividadePrincipal::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val aviso = NotificationCompat.Builder(contexto, CANAL_DOS_LEMBRETES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            /// ⚠️ A frase diz **quanto falta**, não a hora. «Começa às 20:40»
            /// obriga quem lê a olhar o relógio e fazer a conta; «começa em 10
            /// minutos» já é a resposta. É o §8b numa linha de notificação.
            .setContentText("começa em $MINUTOS_DE_ANTECEDENCIA minutos, no ao vivo")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()

        runCatching { NotificationManagerCompat.from(contexto).notify(id, aviso) }
    }
}

private fun criarOCanal(contexto: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val gerente = contexto.getSystemService(NotificationManager::class.java) ?: return
    /// Criar de novo um canal que já existe é barato e não sobrescreve o que a
    /// pessoa ajustou — o sistema garante isso, e é por isso que não se guarda um
    /// «já criei» em lugar nenhum.
    gerente.createNotificationChannel(
        NotificationChannel(
            CANAL_DOS_LEMBRETES,
            "lembretes do ao vivo",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "avisa quando um programa que você marcou está para começar"
        },
    )
}

/// Liga o trabalho periódico. Chamado uma vez, na criação do app.
///
/// ⚠️ `KEEP` e não `REPLACE`: substituir a cada abertura do app reiniciaria a
/// contagem dos quinze minutos, e um app aberto e fechado várias vezes seguidas
/// nunca chegaria a rodar.
fun ligarOsLembretes(contexto: Context) {
    val pedido = PeriodicWorkRequestBuilder<TrabalhoDosLembretes>(15, TimeUnit.MINUTES)
        .setConstraints(
            Constraints.Builder()
                /// Sem rede não há grade a consultar, e o sistema segura o
                /// trabalho até haver — que é melhor que gastar uma rodada pra
                /// descobrir que não dá.
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .build()

    WorkManager.getInstance(contexto)
        .enqueueUniquePeriodicWork(TRABALHO, ExistingPeriodicWorkPolicy.KEEP, pedido)
}
