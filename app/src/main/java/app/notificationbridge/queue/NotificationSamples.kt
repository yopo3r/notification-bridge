/**
 * Fixed sample [NotificationData] values behind the Receiver screen's per-kind send buttons.
 *
 * Why fixed samples instead of free text input: the point of this screen is a fast,
 * one-tap sanity check of the Bluetooth/OBEX path (encoding, MTU chunking, the receiver's own
 * text rendering), not a general-purpose message composer.
 */
package app.notificationbridge.queue

import android.content.Context
import app.notificationbridge.R
import app.notificationbridge.model.NotificationData
import app.notificationbridge.model.TestSampleKind

object NotificationSamples {

    fun forKind(kind: TestSampleKind): NotificationData = when (kind) {
        TestSampleKind.SHORT -> genericSample("Prueba breve", "Prueba.")
        TestSampleKind.LONG -> genericSample(
            "Prueba extensa",
            "Este es un texto de prueba para comprobar la transferencia de mensajes largos por Bluetooth. " +
                "Incluye varias frases y caracteres comunes. El contenido no corresponde a una conversación real. " +
                "Su propósito es verificar el envío, la codificación y la visualización del texto en el dispositivo " +
                "receptor. Fin del texto de prueba."
        )
        TestSampleKind.SPECIAL_CHARS -> genericSample(
            "Acentos y símbolos",
            "Caracteres de prueba: á, é, í, ó, ú, ñ, ¿?, ¡!, €, —, n.º 42."
        )
        TestSampleKind.EMOJI -> genericSample(
            "Prueba de emojis",
            "Emojis de prueba: 😀 🎉 🌍 🚀 🍕."
        )
    }

    /** Uses the application's active per-app locale for samples sent over Bluetooth. */
    fun forKind(context: Context, kind: TestSampleKind): NotificationData = when (kind) {
        TestSampleKind.SHORT -> localized(context, R.string.sample_short_title, R.string.sample_short_text)
        TestSampleKind.LONG -> localized(context, R.string.sample_long_title, R.string.sample_long_text)
        TestSampleKind.SPECIAL_CHARS -> localized(context, R.string.sample_special_title, R.string.sample_special_text)
        TestSampleKind.EMOJI -> localized(context, R.string.sample_emoji_title, R.string.sample_emoji_text)
    }

    private fun localized(context: Context, title: Int, text: Int) =
        sample(context.getString(R.string.sample_source_name), context.getString(title), context.getString(text))

    private fun genericSample(title: String, text: String) = sample("Muestra de prueba", title, text)

    private fun sample(appName: String, title: String, text: String) =
        NotificationData(
            packageName = "app.notificationbridge.sample",
            appName = appName,
            title = title,
            text = text,
            timestamp = System.currentTimeMillis(),
            category = null,
            notificationKey = "manual-${System.nanoTime()}",
            isOngoing = false,
            isSilent = false
        )
}
