package com.mipropiobanco.avisos

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mipropiobanco.MainActivity
import com.mipropiobanco.MiPropioBancoApp
import com.mipropiobanco.R
import com.mipropiobanco.domain.ResumenMes
import com.mipropiobanco.domain.TipoMovimiento
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Revisa dos veces al día qué pagos vencen pronto y si te pasaste del presupuesto. */
class Recordatorios(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MiPropioBancoApp ?: return Result.success()
        if (!app.preferencias.avisos.value) return Result.success()
        val ahora = System.currentTimeMillis()
        val hoy = LocalDate.now()
        val avisos = mutableListOf<String>()

        runCatching {
            app.repositorio.deudas.first().forEach { d ->
                val e = d.estado(ahora) ?: return@forEach
                val dias = ChronoUnit.DAYS.between(hoy, e.vence)
                when {
                    e.atrasado -> avisos += "⚠️ ${d.deuda.acreedor}: pago atrasado ${e.diasAtraso} días"
                    dias in 0L..2L -> avisos += "📅 ${d.deuda.acreedor}: mínimo ${Formato.copReal(d.deuda.pagoMinimo)} " +
                        if (dias == 0L) "vence hoy" else "vence en $dias días"
                }
            }
        }
        runCatching {
            app.repositorio.prestamosConPagos.first().filter { !it.prestamo.pagado }.forEach { p ->
                val e = p.estado(ahora) ?: return@forEach
                val dias = ChronoUnit.DAYS.between(hoy, e.vence)
                when {
                    e.diasAtraso > 0L -> avisos += "⚠️ Cuota de ${p.prestamo.concepto} atrasada ${e.diasAtraso} días"
                    dias in 0L..2L -> avisos += "🏦 Cuota de ${p.prestamo.concepto}: ${Formato.copReal(e.totalAPagar)} " +
                        if (dias == 0L) "vence hoy" else "vence en $dias días"
                }
            }
        }
        runCatching {
            val categorias = app.finanzas.categorias.first()
            val mes = ResumenMes.de(app.finanzas.movimientos.first(), YearMonth.now())
            categorias.filter {
                it.tipo == TipoMovimiento.GASTO.name && it.presupuestoMensual > 0L &&
                    (mes.gastoPorCategoria[it.id] ?: 0L) > it.presupuestoMensual
            }.forEach { c ->
                avisos += "💸 Te pasaste en ${c.icono} ${c.nombre}: ${Formato.copReal(mes.gastoPorCategoria[c.id] ?: 0L)} " +
                    "de ${Formato.copReal(c.presupuestoMensual)}"
            }
        }

        if (avisos.isNotEmpty()) notificar(applicationContext, avisos)
        return Result.success()
    }

    companion object {
        private const val CANAL = "recordatorios"

        fun programar(contexto: Context) {
            val pedido = PeriodicWorkRequestBuilder<Recordatorios>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(contexto)
                .enqueueUniquePeriodicWork("recordatorios", ExistingPeriodicWorkPolicy.KEEP, pedido)
        }

        fun notificar(contexto: Context, lineas: List<String>) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            val gestor = contexto.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                gestor.createNotificationChannel(
                    NotificationChannel(CANAL, "Recordatorios de pagos", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val abrir = PendingIntent.getActivity(
                contexto, 0, Intent(contexto, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val estilo = NotificationCompat.InboxStyle()
            lineas.take(6).forEach { estilo.addLine(it) }
            val aviso = NotificationCompat.Builder(contexto, CANAL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Mi Propio Banco · ${lineas.size} recordatorios")
                .setContentText(lineas.first())
                .setStyle(estilo)
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(contexto).notify(1001, aviso) }
        }
    }
}
