package com.mipropiobanco.util

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
    private val formatoTasa = DecimalFormat("0.##", simbolos)
    private val formatoFecha = DateTimeFormatter.ofPattern("dd MMM yyyy", localeCO)

    fun miles(valor: Long): String = synchronized(formatoMiles) { formatoMiles.format(valor) }

    /** $ 1.234.567 → "$1.234.567"; negativos → "-$1.234.567". */
    fun cop(valor: Long): String =
        if (valor < 0L) "-$" + miles(-valor) else "$" + miles(valor)

    /** 1.5 → "1,5%" */
    fun tasa(valor: Double): String = synchronized(formatoTasa) { formatoTasa.format(valor) } + "%"

    fun porcentaje(valor: Double, decimales: Int = 2): String =
        String.format(localeCO, "%.${decimales}f%%", valor)

    fun fecha(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(formatoFecha)

    fun fechaIso(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

    fun hoyMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun hoyIso(): String = LocalDate.now().toString()

    /** Acepta "1,5" o "1.5". */
    fun parsearTasa(texto: String): Double? = texto.trim().replace(',', '.').toDoubleOrNull()
}
