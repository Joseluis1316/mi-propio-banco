package com.mipropiobanco.domain

import java.time.LocalDate

/** Lo que dice un correo de alerta del banco, antes de clasificarlo. */
data class MovimientoCorreo(
    val id: String,
    val tipo: TipoMovimiento,
    val monto: Long,
    /** A quién se pagó o de quién se recibió (comercio, persona, empresa). */
    val contraparte: String,
    /** Cuenta o tarjeta de la otra parte, si el correo la trae (ej. "3012316181"). */
    val cuentaDestino: String?,
    /** "debito", "credito" o null: con qué se pagó. */
    val tarjeta: String?,
    val fecha: LocalDate,
    val esNomina: Boolean,
    val esRetiro: Boolean,
    val resumen: String,
    val texto: String
) {
    /** Clave para recordar la clasificación: la cuenta destino, o el nombre de la contraparte. */
    val patron: String
        get() = cuentaDestino?.let { "cuenta:$it" } ?: "nombre:${Interprete.normalizar(contraparte).trim()}"
}

/**
 * Entiende las alertas de Bancolombia que llegan al correo:
 * Pagaste, Compraste, Transferiste, Recibiste (transferencia o nómina) y Retiraste.
 */
object InterpreteCorreo {

    private val DINERO = "\\$\\s?([\\d.,]+)"

    private fun valor(texto: String): Long? {
        var t = texto.trim().trimEnd('.', ',')
        if (Regex(".*[.,]\\d{2}$").matches(t) && Regex("[.,]").findAll(t).count() >= 1 &&
            !Regex("^\\d{1,3}([.,]\\d{3})+$").matches(t)
        ) {
            t = t.substring(0, t.length - 3)
        }
        return t.replace(".", "").replace(",", "").toLongOrNull()
    }

    private fun fecha(texto: String, porDefecto: LocalDate): LocalDate {
        Regex("(\\d{1,2})/(\\d{1,2})/(\\d{2,4})").find(texto)?.let { m ->
            val a = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            runCatching { return LocalDate.of(a, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }
        }
        return porDefecto
    }

    private fun limpiar(nombre: String): String =
        nombre.trim().trimEnd('.', ',').replace(Regex("\\s+"), " ")

    private val VERBOS = Regex(
        "(recibiste|retiraste|compraste|pagaste|transferiste|enviaste|realizaste)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Toma solo la frase de la alerta del cuerpo del correo. El texto plano de Gmail trae
     * primero los logos como [http://...]: se quitan los enlaces y se arranca en el verbo.
     */
    private fun frase(cuerpo: String): String {
        val plano = cuerpo
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("https?://\\S+"), " ")
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
        val inicio = VERBOS.find(plano)?.range?.first
            ?: plano.indexOf("Bancolombia:", ignoreCase = true).takeIf { it >= 0 }
            ?: 0
        val desde = plano.substring(inicio)
        val fin = Regex("(\\?Dudas|¿Dudas|Si tienes dudas|Llamanos|Llámanos|hablemos|Si no reconoces)", RegexOption.IGNORE_CASE).find(desde)
        return (if (fin != null) desde.substring(0, fin.range.first) else desde).take(400).trim()
    }

    fun interpretar(id: String, cuerpo: String, fechaCorreo: LocalDate): MovimientoCorreo? {
        val f = frase(cuerpo)
        val op = setOf(RegexOption.IGNORE_CASE)

        fun crear(
            tipo: TipoMovimiento, montoTxt: String, contraparte: String, resumen: String,
            cuenta: String? = null, tarjeta: String? = null, nomina: Boolean = false, retiro: Boolean = false
        ): MovimientoCorreo? {
            val monto = valor(montoTxt) ?: return null
            if (monto <= 0L) return null
            return MovimientoCorreo(
                id, tipo, monto, limpiar(contraparte), cuenta, tarjeta,
                fecha(f, fechaCorreo), nomina, retiro, resumen, f
            )
        }

        // Recibiste un pago de Nomina de EMPRESA por $X en tu cuenta...
        Regex("recibiste un pago de n[oó]mina de (.+?) por $DINERO", op).find(f)?.let {
            return crear(TipoMovimiento.INGRESO, it.groupValues[2], it.groupValues[1], "Nómina de ${limpiar(it.groupValues[1])}", nomina = true)
        }
        // Recibiste una transferencia por $X de NOMBRE en tu cuenta...
        Regex("recibiste (?:una transferencia|un pago|un abono|dinero) (?:por|de) $DINERO de (.+?)(?: en tu| a tu|,| el |$)", op).find(f)?.let {
            return crear(TipoMovimiento.INGRESO, it.groupValues[1], it.groupValues[2], "Transferencia de ${limpiar(it.groupValues[2])}")
        }
        // Recibiste un pago de NOMBRE por $X
        Regex("recibiste (?:un pago|un abono|dinero) de (.+?) por $DINERO", op).find(f)?.let {
            return crear(TipoMovimiento.INGRESO, it.groupValues[2], it.groupValues[1], "Pago de ${limpiar(it.groupValues[1])}")
        }
        // Retiraste $X en ... (el efectivo se da por gastado al sacarlo)
        Regex("retiraste $DINERO (?:en )?(.+?)(?:,? el |$)", op).find(f)?.let {
            return crear(TipoMovimiento.GASTO, it.groupValues[1], "Retiro", "Retiro en ${limpiar(it.groupValues[2]).take(40)}", retiro = true)
        }
        // Compraste $X en COMERCIO con tu T.Deb *1234
        Regex("compraste $DINERO en (.+?) con tu (t\\.?\\s?deb|t\\.?\\s?cred|tarjeta (?:de )?(?:debito|d[eé]bito|credito|cr[eé]dito))", op).find(f)?.let {
            val tarjeta = if (Interprete.normalizar(it.groupValues[3]).contains("cred")) "credito" else "debito"
            return crear(TipoMovimiento.GASTO, it.groupValues[1], it.groupValues[2], "Compra en ${limpiar(it.groupValues[2])}", tarjeta = tarjeta)
        }
        Regex("compraste $DINERO en (.+?)(?: con | el |,|$)", op).find(f)?.let {
            return crear(TipoMovimiento.GASTO, it.groupValues[1], it.groupValues[2], "Compra en ${limpiar(it.groupValues[2])}")
        }
        // Pagaste $X a NOMBRE desde tu producto ...
        Regex("pagaste $DINERO (?:por .+? )?(?:a|en) (.+?)(?: desde | con | el |,|$)", op).find(f)?.let {
            return crear(TipoMovimiento.GASTO, it.groupValues[1], it.groupValues[2], "Pago a ${limpiar(it.groupValues[2])}")
        }
        // Transferiste $X desde tu cuenta *3888 a la cuenta *3012316181
        Regex("transferiste $DINERO .*?a la cuenta \\*?(\\d+)", op).find(f)?.let {
            val cuenta = it.groupValues[2]
            return crear(TipoMovimiento.GASTO, it.groupValues[1], "Cuenta *$cuenta", "Transferencia a la cuenta *$cuenta", cuenta = cuenta)
        }
        Regex("transferiste $DINERO (?:por .+? )?(?:a|para) (.+?)(?: desde | el |,|$)", op).find(f)?.let {
            return crear(TipoMovimiento.GASTO, it.groupValues[1], it.groupValues[2], "Transferencia a ${limpiar(it.groupValues[2])}")
        }
        return null
    }

    /** ¿Ya hay un movimiento igual (mismo monto, ±3 días)? Para no contar dos veces el mismo dinero. */
    fun buscarDuplicado(
        m: MovimientoCorreo,
        movimientos: List<com.mipropiobanco.data.local.MovimientoEntity>,
        dias: Long = 3
    ): String? {
        fun cerca(fecha: Long, dias: Long) = kotlin.math.abs(
            java.time.temporal.ChronoUnit.DAYS.between(com.mipropiobanco.util.Formato.aLocalDate(fecha), m.fecha)
        ) <= dias
        // Un retiro pudo quedar guardado antes como traslado a efectivo.
        val tipos = if (m.esRetiro) setOf(TipoMovimiento.GASTO.name, TipoMovimiento.TRASLADO.name) else setOf(m.tipo.name)
        movimientos.firstOrNull { it.monto == m.monto && it.tipo in tipos && cerca(it.fecha, dias) }?.let {
            return "Ya registraste ${com.mipropiobanco.util.Formato.cop(it.monto)} el ${com.mipropiobanco.util.Formato.fecha(it.fecha)}" +
                if (it.nota.isNotBlank()) " («${it.nota.take(30)}»)" else ""
        }
        if (m.esNomina) {
            // Desprendible ya cargado: devengos − deducciones de esos días = lo que llegó al banco.
            val deNomina = movimientos.filter { it.nota.contains("nómina") && cerca(it.fecha, dias * 2) }
            val neto = deNomina.sumOf { if (it.tipo == TipoMovimiento.INGRESO.name) it.monto else -it.monto }
            if (deNomina.isNotEmpty() && kotlin.math.abs(neto - m.monto) <= 1_000L) {
                return "Ya cargaste el desprendible de esta nómina (neto ${com.mipropiobanco.util.Formato.cop(neto)})"
            }
        }
        return null
    }
}
