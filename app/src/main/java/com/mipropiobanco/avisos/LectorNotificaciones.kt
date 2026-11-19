package com.mipropiobanco.avisos

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mipropiobanco.MiPropioBancoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lee las notificaciones de Nequi y Daviplata ("Enviaste $20.000 a…", "Recibiste $…")
 * y las deja en la bandeja; la app las anota sola igual que los correos de Bancolombia.
 * Bancolombia no se lee aquí porque ya llega por correo (se contaría dos veces).
 */
class LectorNotificaciones : NotificationListenerService() {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val paquete = n.packageName.lowercase()
        val origen = when {
            paquete.contains("nequi") -> "nequi"
            paquete.contains("daviplata") || paquete.contains("davivienda") -> "daviplata"
            else -> return
        }
        val app = application as? MiPropioBancoApp ?: return
        if (!app.preferencias.leerNotificaciones.value) return

        val extras = n.notification?.extras ?: return
        val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val texto = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val completo = "$titulo. $texto".trim()
        // Solo movimientos de plata: debe traer un monto.
        if (!Regex("\\$\\s?\\d").containsMatchIn(completo)) return

        // Mismo texto en el mismo minuto = misma notificación (Android a veces la repite).
        val id = "${origen}_${(completo + (n.postTime / 60_000L)).hashCode().toUInt()}"
        alcance.launch {
            runCatching { app.nube.agregarABandeja(id, completo, n.postTime, origen) }
        }
    }

    override fun onDestroy() {
        alcance.cancel()
        super.onDestroy()
    }
}
