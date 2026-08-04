package dev.odeon.android.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/// O quanto o aparelho está inclinado, de −1 a 1 nos dois eixos — R8.
///
/// É o que dá **volume** ao pôster da ficha sem 3D: a arte se move alguns dp
/// dentro da própria moldura conforme o aparelho gira, e o olho lê profundidade.
/// A §1.3 do redesenho pede «a caixa tem volume»; isto é o mesmo efeito por
/// paralaxe em vez de perspectiva.
///
/// ## ⚠️ A chave, que a R8 exige e não é opcional
///
/// > «Movimento constante na tela é o oposto de acessível pra quem tem
/// > sensibilidade a movimento, e o Android tem a preferência do sistema pra
/// > isso (`ANIMATOR_DURATION_SCALE` = 0). Respeitá-la não é opcional.»
///
/// **E aqui ela precisa ser lida à mão.** Todo o resto do redesenho — o afundar
/// do cartaz, a transição compartilhada, a luz da marquise — passa por
/// `animate*AsState` ou `AnimatedContent`, que leem o `MotionDurationScale` do
/// contexto e já obedecem de graça. Este não passa: ele lê **sensor**, e sensor
/// não sabe de escala de animação.
///
/// Com a preferência em zero, o listener **nem é registrado** — não é o valor
/// que vira zero, é o sensor que não liga. A diferença importa: um listener de
/// acelerômetro a 50Hz custa bateria mesmo quando o resultado é descartado.
///
/// ## O acelerômetro, e não o giroscópio
///
/// A R8 diz "giroscópio", e o sensor certo é o outro. O giroscópio mede
/// **velocidade angular** — quanto se está girando agora —, e integrar isso pra
/// achar a posição acumula deriva em segundos. O acelerômetro em repouso mede a
/// **gravidade**, que aponta pra baixo sempre, e dela sai a inclinação absoluta
/// sem integrar nada.
///
/// ## O repouso é onde o aparelho estava, não onde "deveria" estar
///
/// A primeira leitura vira o zero. Sem isso, quem lê deitado na cama veria o
/// pôster encostado no canto o tempo todo — porque a posição "natural" seria a
/// vertical, que não é como aquela pessoa está segurando o telefone.
///
/// O filtro passa-baixa de 0,15 é o que separa inclinar de tremer: sem ele, o
/// ruído do sensor faz a arte vibrar parada na mão.
@Composable
fun inclinacao(): State<Offset> {
    val contexto = LocalContext.current
    val valor = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(contexto) {
        /// A preferência do sistema, lida na hora de registrar.
        ///
        /// `1f` como padrão porque ausência de valor é "animação normal" — e
        /// falhar pro lado de **ligado** aqui seria o erro errado: quem desligou
        /// animação pediu explicitamente.
        val escala = runCatching {
            Settings.Global.getFloat(
                contexto.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)

        val gerente = contexto.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = gerente?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (escala == 0f || sensor == null) {
            /// Sem sensor **ou** com animação desligada, o pôster fica parado e
            /// nada é registrado. Um aparelho sem acelerômetro é raro e existe;
            /// um emulador sem ele, menos raro.
            return@DisposableEffect onDispose { }
        }

        var repouso: Offset? = null

        val ouvinte = object : SensorEventListener {
            override fun onSensorChanged(evento: SensorEvent) {
                /// `x` cresce pra direita e `y` pra cima, os dois em m/s².
                /// Dividir por 9,81 põe a coisa em "frações de gravidade", que
                /// é o mesmo que o seno do ângulo pra inclinações pequenas.
                val bruto = Offset(
                    evento.values[0] / SensorManager.GRAVITY_EARTH,
                    evento.values[1] / SensorManager.GRAVITY_EARTH,
                )
                val base = repouso ?: bruto.also { repouso = it }

                /// O sinal de `y` é invertido: inclinar o topo do aparelho **pra
                /// longe** tem que empurrar a arte pra **baixo**, que é o que
                /// uma janela faz quando se olha por ela de outro ângulo.
                val alvo = Offset(
                    (bruto.x - base.x).coerceIn(-1f, 1f),
                    -(bruto.y - base.y).coerceIn(-1f, 1f),
                )

                val anterior = valor.value
                val suave = Offset(
                    anterior.x + (alvo.x - anterior.x) * 0.15f,
                    anterior.y + (alvo.y - anterior.y) * 0.15f,
                )

                /// Só escreve o estado quando mudou o suficiente pra aparecer.
                ///
                /// O sensor entrega ~50 eventos por segundo e o estado é lido
                /// por um `graphicsLayer`; escrever a cada evento recomporia a
                /// ficha 50 vezes por segundo pra mover a arte por frações de
                /// pixel. 0,004 de gravidade sobre 4dp de deslocamento dá menos
                /// de um centésimo de dp — abaixo disso não há o que desenhar.
                if (abs(suave.x - anterior.x) > 0.004f || abs(suave.y - anterior.y) > 0.004f) {
                    valor.value = suave
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, precisao: Int) = Unit
        }

        /// `SENSOR_DELAY_GAME` — ~50Hz. `_FASTEST` entregaria o dobro pra mover
        /// 4dp, e `_UI` (~15Hz) faria a paralaxe andar aos saltos.
        gerente.registerListener(ouvinte, sensor, SensorManager.SENSOR_DELAY_GAME)

        onDispose { gerente.unregisterListener(ouvinte) }
    }

    return valor
}
