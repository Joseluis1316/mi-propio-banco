package com.mipropiobanco.avisos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mipropiobanco.MainActivity
import com.mipropiobanco.R
import com.mipropiobanco.domain.Diagnostico
import com.mipropiobanco.util.Formato

/** Widget de la pantalla de inicio del celular: "Hoy puedes gastar" + puntaje + consejo principal. */
class WidgetHoy : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { pintar(context, manager, it) }
    }

    companion object {
        private const val PREFS = "widget_hoy"

        /** Guarda el resumen y repinta todos los widgets. */
        fun guardar(context: Context, d: Diagnostico, ocultar: Boolean) {
            val principal = d.consejos.firstOrNull()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("monto", if (ocultar) "$ ••••" else Formato.copReal(d.hoyPuedes.coerceAtLeast(0L)))
                .putString(
                    "detalle",
                    if (d.hoyPuedes < 0L) "Hoy te pasaste ${if (ocultar) "" else Formato.copReal(-d.hoyPuedes)}"
                    else "Quedan ${d.diasRestantes} día(s) de la quincena"
                )
                .putString("puntaje", "${d.puntaje} · ${d.etiquetaPuntaje}")
                .putString("consejo", principal?.let { "${it.icono} ${it.titulo}" } ?: "")
                .apply()
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, WidgetHoy::class.java)).forEach { pintar(context, manager, it) }
        }

        private fun pintar(context: Context, manager: AppWidgetManager, id: Int) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val vistas = RemoteViews(context.packageName, R.layout.widget_hoy)
            vistas.setTextViewText(R.id.widget_monto, p.getString("monto", "Abre la app"))
            vistas.setTextViewText(R.id.widget_detalle, p.getString("detalle", ""))
            vistas.setTextViewText(R.id.widget_puntaje, p.getString("puntaje", ""))
            vistas.setTextViewText(R.id.widget_consejo, p.getString("consejo", ""))
            val abrir = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            vistas.setOnClickPendingIntent(R.id.widget_raiz, abrir)
            manager.updateAppWidget(id, vistas)
        }
    }
}
