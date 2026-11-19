package com.mipropiobanco.domain

import java.time.LocalDate

/** Un movimiento leído de una captura del extracto del banco (app Bancolombia, Nequi…). */
data class FilaExtracto(
    val fecha: LocalDate,
    val descripcion: String,
    val monto: Long,
    /** true = salió plata (el extracto lo muestra con "-"). */
    val salida: Boolean
)

/** Qué hacer con cada fila del extracto. */
enum class DestinoFila(val etiqueta: String) {
    GASTO("− Gasto"),
    INGRESO("+ Ingreso"),
    PRESTAMO("🤝 Me prestaron"),
    OMITIR("Omitir")
}

/**
 * Lee capturas de la lista de movimientos del banco:
 *   18 NOV 2026
 *   TRANSFERENCIA DESDE NEQUI
 *   COP $ 50.000,00          (salidas: COP -$ 150.000,00)
 * Devuelve una fila por movimiento. Las que quedaron cortadas arriba (sin fecha) se ignoran.
 */
object InterpreteExtracto {

    private val MESES = mapOf(
        "ENE" to 1, "FEB" to 2, "MAR" to 3, "ABR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AGO" to 8, "SEP" to 9, "SET" to 9, "OCT" to 10, "NOV" to 11, "DIC" to 12
    )
    private val FECHA = Regex("(\\d{1,2})\\s+(ENE|FEB|MAR|ABR|MAY|JUN|JUL|AGO|SEPT?|SET|OCT|NOV|DIC)[A-Z]*\\.?\\s+(\\d{4})", RegexOption.IGNORE_CASE)
    private val MONTO = Regex("(-\\s*)?(?:COP\\s*)?(-\\s*)?\\$\\s*(\\d{1,3}(?:[.,]\\d{3})+|\\d+)(?:[.,](\\d{1,2}))?")

    private data class EnCurso(val fecha: LocalDate, val desc: StringBuilder = StringBuilder(), var monto: Long? = null, var salida: Boolean = false)

    fun interpretar(texto: String): List<FilaExtracto> {
        val filas = mutableListOf<FilaExtracto>()
        var actual: EnCurso? = null

        fun cerrar() {
            val a = actual ?: return
            val m = a.monto
            if (m != null && m > 0L) {
                filas += FilaExtracto(a.fecha, a.desc.toString().trim().ifBlank { "Movimiento" }, m, a.salida)
            }
            actual = null
        }

        texto.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { linea ->
            val f = FECHA.find(linea)
            if (f != null) {
                cerrar()
                val mes = MESES[f.groupValues[2].uppercase().take(3)] ?: return@forEach
                val fecha = runCatching {
                    LocalDate.of(f.groupValues[3].toInt(), mes, f.groupValues[1].toInt())
                }.getOrNull() ?: return@forEach
                actual = EnCurso(fecha)
                // A veces la descripción viene en la misma línea de la fecha.
                val resto = linea.substring(f.range.last + 1).trim()
                if (resto.isNotEmpty() && !resto.contains("$")) actual?.desc?.append(resto)
                return@forEach
            }
            val a = actual ?: return@forEach
            if (a.monto != null) return@forEach
            val m = MONTO.find(linea)
            if (m != null) {
                val entero = m.groupValues[3].replace(".", "").replace(",", "").toLongOrNull()
                if (entero != null) {
                    a.monto = entero
                    a.salida = m.groupValues[1].isNotEmpty() || m.groupValues[2].isNotEmpty() ||
                        linea.contains("-$") || linea.contains("- $")
                }
            } else {
                if (a.desc.isNotEmpty()) a.desc.append(' ')
                a.desc.append(linea)
            }
        }
        cerrar()
        return filas
    }

    /** Sugerencia inicial para cada fila. */
    fun destinoSugerido(f: FilaExtracto): DestinoFila {
        val t = Interprete.normalizar(f.descripcion)
        return when {
            t.contains("abono intereses") || t.contains("ajuste interes") || f.monto < 100L -> DestinoFila.OMITIR
            f.salida -> DestinoFila.GASTO
            else -> DestinoFila.INGRESO
        }
    }
}
