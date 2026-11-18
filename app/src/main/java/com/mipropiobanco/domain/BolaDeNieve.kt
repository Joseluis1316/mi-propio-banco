package com.mipropiobanco.domain

import com.mipropiobanco.data.local.AbonoDeudaEntity
import com.mipropiobanco.data.local.DeudaEntity
import com.mipropiobanco.util.Formato
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToLong

data class DeudaConAbonos(
    val deuda: DeudaEntity,
    val abonos: List<AbonoDeudaEntity>
) {
    val totalAbonado: Long get() = abonos.sumOf { it.monto }
    val saldo: Long get() = (deuda.saldoInicial - totalAbonado).coerceAtLeast(0L)
    val pagada: Boolean get() = saldo == 0L
    val progreso: Float
        get() = if (deuda.saldoInicial > 0L) (totalAbonado.toFloat() / deuda.saldoInicial).coerceIn(0f, 1f) else 1f
    val tasaMensual: Double get() = Tasas.periodoDesdeEA(deuda.tasaEA, Periodicidad.MENSUAL)

    private fun fechaEnMes(mes: YearMonth): LocalDate = mes.atDay(deuda.diaPago.coerceIn(1, mes.lengthOfMonth()))

    private fun pagoEntre(desdeExclusivo: LocalDate, hastaInclusivo: LocalDate): Boolean =
        abonos.any {
            val f = Formato.aLocalDate(it.fecha)
            f.isAfter(desdeExclusivo) && !f.isAfter(hastaInclusivo)
        }

    /**
     * Fecha del próximo pago:
     * - si la fecha anterior quedó sin pago, esa (atrasada);
     * - si ya pagaste en el ciclo actual, la del mes siguiente;
     * - si no, la de este ciclo.
     */
    fun proximoVencimiento(hoy: LocalDate): LocalDate {
        val mesActual = YearMonth.from(hoy)
        var actual = fechaEnMes(mesActual)
        var mes = mesActual
        if (actual.isBefore(hoy)) {
            mes = mesActual.plusMonths(1)
            actual = fechaEnMes(mes)
        }
        val anterior = fechaEnMes(mes.minusMonths(1))
        val antesDeAnterior = fechaEnMes(mes.minusMonths(2))
        val registro = Formato.aLocalDate(deuda.fechaRegistro)
        if (!anterior.isBefore(registro) && anterior.isBefore(hoy) && !pagoEntre(antesDeAnterior, anterior)) {
            return anterior
        }
        return if (pagoEntre(anterior, actual)) fechaEnMes(mes.plusMonths(1)) else actual
    }

    fun estado(ahoraMs: Long): EstadoPagoDeuda? {
        if (pagada) return null
        val zona = ZoneId.systemDefault()
        val hoy = Instant.ofEpochMilli(ahoraMs).atZone(zona).toLocalDate()
        val vence = proximoVencimiento(hoy)
        val limite = vence.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        return EstadoPagoDeuda(vence, limite - ahoraMs, Tasas.diasAtraso(vence, hoy))
    }
}

data class EstadoPagoDeuda(val vence: LocalDate, val restanteMs: Long, val diasAtraso: Long) {
    val atrasado: Boolean get() = diasAtraso > 0L
}

data class PlanBolaNieve(
    /** Deudas activas en orden de ataque: la de menor saldo primero. */
    val orden: List<DeudaConAbonos>,
    /** Cuánto pagarle a cada deuda este mes (mínimo + excedente al objetivo). */
    val pagoEsteMes: Map<Long, Long>,
    /** En qué mes del plan (1, 2, 3…) queda liquidada cada deuda. */
    val mesLiquidacion: Map<Long, Int>,
    /** Meses para quedar libre de deudas; null si el presupuesto no alcanza a bajarlas. */
    val mesesTotales: Int?,
    val interesesProyectados: Long,
    val sumaMinimos: Long,
    val presupuestoUsado: Long
) {
    val objetivoId: Long? get() = orden.firstOrNull()?.deuda?.id
    val presupuestoInsuficiente: Boolean get() = presupuestoUsado > 0L && mesesTotales == null
}

/**
 * Método bola de nieve: se paga el mínimo en todas las deudas y todo el excedente
 * va a la de menor saldo. Al liquidarla, su pago (mínimo + excedente) se suma a la siguiente.
 */
object BolaDeNieve {
    private const val MAX_MESES = 600

    fun planificar(deudas: List<DeudaConAbonos>, presupuestoMensual: Long): PlanBolaNieve {
        val orden = deudas.filter { !it.pagada }.sortedWith(compareBy({ it.saldo }, { it.deuda.id }))
        val sumaMinimos = orden.sumOf { minOf(it.deuda.pagoMinimo, it.saldo) }
        val presupuesto = maxOf(presupuestoMensual, sumaMinimos)
        val saldos = orden.associate { it.deuda.id to it.saldo.toDouble() }.toMutableMap()
        val liquidacion = mutableMapOf<Long, Int>()
        var pagoEsteMes = emptyMap<Long, Long>()
        var intereses = 0.0
        var mes = 0

        while (saldos.values.any { it > 0.5 } && mes < MAX_MESES) {
            mes++
            orden.forEach { d ->
                val s = saldos.getValue(d.deuda.id)
                if (s > 0.5) {
                    val i = s * d.tasaMensual / 100.0
                    saldos[d.deuda.id] = s + i
                    intereses += i
                }
            }
            var disponible = presupuesto.toDouble()
            val pagos = mutableMapOf<Long, Double>()
            orden.forEach { d ->
                val s = saldos.getValue(d.deuda.id)
                if (s > 0.5 && disponible > 0.0) {
                    val p = minOf(d.deuda.pagoMinimo.toDouble(), s, disponible)
                    saldos[d.deuda.id] = s - p
                    disponible -= p
                    pagos[d.deuda.id] = p
                }
            }
            for (d in orden) {
                if (disponible <= 0.5) break
                val s = saldos.getValue(d.deuda.id)
                if (s > 0.5) {
                    val p = minOf(s, disponible)
                    saldos[d.deuda.id] = s - p
                    disponible -= p
                    pagos[d.deuda.id] = (pagos[d.deuda.id] ?: 0.0) + p
                }
            }
            if (mes == 1) pagoEsteMes = pagos.mapValues { it.value.roundToLong() }
            orden.forEach { d ->
                if (saldos.getValue(d.deuda.id) <= 0.5 && d.deuda.id !in liquidacion) liquidacion[d.deuda.id] = mes
            }
        }

        val terminado = saldos.values.all { it <= 0.5 }
        return PlanBolaNieve(
            orden = orden,
            pagoEsteMes = pagoEsteMes,
            mesLiquidacion = liquidacion,
            mesesTotales = if (terminado) mes else null,
            interesesProyectados = if (terminado) intereses.roundToLong() else 0L,
            sumaMinimos = sumaMinimos,
            presupuestoUsado = if (orden.isEmpty()) 0L else presupuesto
        )
    }
}
