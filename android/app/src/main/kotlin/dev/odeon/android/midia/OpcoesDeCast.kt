package dev.odeon.android.midia

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/// A configuração do Cast, que o SDK do Google exige existir.
///
/// ## Ela é apontada pelo manifesto, e não construída por nós
///
/// O `play-services-cast-framework` procura esta classe pelo nome, declarado num
/// `<meta-data>` do manifesto, e a instancia sozinho. É por isso que ela não
/// aparece chamada em lugar nenhum do código — quem chama é o SDK.
///
/// ## O receptor é o padrão, e é decisão
///
/// `DEFAULT_MEDIA_RECEIVER_APPLICATION_ID` é o app de recepção que o Google
/// publica: ele toca mídia com legenda e controle, e **não exige registro**.
///
/// Um receptor próprio (com a cara do Odeon na TV) exigiria conta de
/// desenvolvedor no Cast, um app web hospedado em algum lugar e revisão do
/// Google — três coisas que a §7 da espec ainda lista como em aberto pra
/// publicação. Ele entra quando a identidade visual entrar; até lá, o padrão
/// toca o filme, que é o que a fase 4 promete.
@Suppress("unused") // instanciada por nome pelo SDK, via manifesto
class OpcoesDeCast : OptionsProvider {

    override fun getCastOptions(contexto: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(
                com.google.android.gms.cast.CastMediaControlIntent
                    .DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
            )
            /// Não sequestra o volume do celular quando não há sessão.
            .setEnableReconnectionService(true)
            .build()

    /// Sem provedores de sessão próprios: o de mídia já vem do SDK.
    override fun getAdditionalSessionProviders(contexto: Context): MutableList<SessionProvider>? = null
}
