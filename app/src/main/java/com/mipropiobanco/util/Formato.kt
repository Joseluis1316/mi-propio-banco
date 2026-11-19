package com.mipropiobanco.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Utilidades de formato para pesos colombianos (sin decimales), porcentajes y fechas.
 * Las fechas se guardan como milisegundos de la medianoche UTC del día calendario,
 * que es el mismo formato que entrega el selector de fecha de Material 3.
 */
object Formato {
    val localeCO: Locale = Locale("es", "CO")

    private val simbolos = DecimalFormatSymbols(localeCO).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val formatoMiles = DecimalFormat("#,##0", simbolos)
    private val formatoDecimal = DecimalFormat("0.####", simbolos)
    private val formatoFecha = DateTimeFormatter.ofPattern("dd MMM yyyy", localeCO)

    fun miles(valor: Long): String = synchronized(formatoMiles) { formatoMiles.format(valor) }

    /** Modo privado: los montos se muestran como "$ •••••" (se activa con el ojo en Inicio). */
    var ocultar by androidx.compose.runtime.mutableStateOf(false)

    /** 1234567 → "$1.234.567"; negativos → "-$1.234.567". */
    fun cop(valor: Long): String = when {
        ocultar -> "$ •••••"
        valor < 0L -> "-$" + miles(-valor)
        else -> "$" + miles(valor)
    }

    /** Igual que [cop] pero nunca oculta (para archivos exportados y mensajes de error). */
    fun copReal(valor: Long): String =
        if (valor < 0L) "-$" + miles(-valor) else "$" + miles(valor)

    /** 28.34 → "28,34" (para precargar campos editables). */
    fun decimal(valor: Double): String = synchronized(formatoDecimal) { formatoDecimal.format(valor) }

    /** 1.5 → "1,5%" */
    fun tasa(valor: Double): String = decimal(Math.round(valor * 10000.0) / 10000.0) + "%"

    fun porcentaje(valor: Double, decimales: Int = 2): String =
        String.format(localeCO, "%.${decimales}f%%", valor)

    fun aLocalDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun aMillis(fecha: LocalDate): Long = fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fecha(millis: Long): String = aLocalDate(millis).format(formatoFecha)

    fun fecha(fecha: LocalDate): String = fecha.format(formatoFecha)

    fun fechaIso(millis: Long): String = aLocalDate(millis).toString()

    fun hoyMillis(): Long = aMillis(LocalDate.now())

    fun hoyIso(): String = LocalDate.now().toString()

    /** 273_600_000 ms → "3d 04:00:00" */
    fun duracion(ms: Long): String {
        val s = (if (ms < 0L) -ms else ms) / 1000L
        val dias = s / 86_400L
        val horas = (s % 86_400L) / 3_600L
        val minutos = (s % 3_600L) / 60L
        val segundos = s % 60L
        return if (dias > 0L) {
            String.format(localeCO, "%dd %02d:%02d:%02d", dias, horas, minutos, segundos)
        } else {
            String.format(localeCO, "%02d:%02d:%02d", horas, minutos, segundos)
        }
    }

    /** Acepta "1,5" o "1.5". */
    fun parsearDecimal(texto: String): Double? = texto.trim().replace(',', '.').toDoubleOrNull()
}
