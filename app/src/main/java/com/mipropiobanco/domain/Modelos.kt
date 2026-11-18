package com.mipropiobanco.domain

import com.mipropiobanco.data.local.PagoEntity
import com.mipropiobanco.data.local.PrestamoEntity
import com.mipropiobanco.util.Formato
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.roundToLong

/** Frecuencia de las cuotas. porAnio se usa para convertir la tasa E.A. en tasa del periodo. */
enum class Periodicidad(val etiqueta: String, val singular: String, val plural: String, val porAnio: Int) {
    DIARIA("Días", "día", "días", 365),
    SEMANAL("Semanas", "semana", "semanas", 52),
    QUINCENAL("Quincenas", "quincena", "quincenas", 24),
    MENSUAL("Meses", "mes", "meses", 12),
    ANUAL("Años", "año", "años", 1);

    /** Fecha en que vence la cuota número [cuota] contando desde el desembolso. */
    fun vencimiento(desembolso: LocalDate, cuota: Int): LocalDate {
        val n = cuota.toLong()
        return when (this) {
            DIARIA -> desembolso.plusDays(n)
            SEMANAL -> desembolso.plusWeeks(n)
            QUINCENAL -> desembolso.plusDays(15L * n)
            MENSUAL -> desembolso.plusMonths(n)
            ANUAL -> desembolso.plusYears(n)
        }
    }

    fun cantidad(n: Int): String = "$n ${if (n == 1) singular else plural}"

    companion object {
        fun desde(nombre: String?): Periodicidad = entries.firstOrNull { it.name == nombre } ?: MENSUAL
    }
}

/** Conversión de tasas (equivalencia compuesta) y cálculo de mora. Todas las tasas en %. */
object Tasas {
    fun periodoDesdeEA(eaPct: Double, p: Periodicidad): Double =
        ((1.0 + eaPct / 100.0).pow(1.0 / p.porAnio) - 1.0) * 100.0

    fun eaDesdePeriodo(periodoPct: Double, p: Periodicidad): Double =
        ((1.0 + periodoPct / 100.0).pow(p.porAnio.toDouble()) - 1.0) * 100.0

    fun diasAtraso(vence: LocalDate, fechaPago: LocalDate): Long =
        if (fechaPago.isAfter(vence)) ChronoUnit.DAYS.between(vence, fechaPago) else 0L

    /** Interés moratorio sobre la cuota vencida, capitalizado por día a la tasa E.A. de mora. */
    fun mora(valorVencido: Long, moraEAPct: Double, diasAtraso: Long): Long {
        if (diasAtraso <= 0L || valorVencido <= 0L || moraEAPct <= 0.0) return 0L
        return (valorVencido * ((1.0 + moraEAPct / 100.0).pow(diasAtraso / 365.0) - 1.0)).roundToLong()
    }
}

data class FilaAmortizacion(
    val numero: Int,
    val saldoInicial: Long,
    val cuota: Long,
    val capital: Long,
    val interes: Long,
    val saldoFinal: Long
)

/** Sistema francés: cuota fija, interés sobre saldo, abono a capital creciente. */
object Amortizacion {

    fun cuotaFija(monto: Long, cuotas: Int, tasaPeriodoPct: Double): Long {
        if (monto <= 0L || cuotas <= 0) return 0L
        val i = tasaPeriodoPct / 100.0
        if (i <= 0.0) return (monto + cuotas - 1) / cuotas
        return (monto * i / (1.0 - (1.0 + i).pow(-cuotas))).roundToLong()
    }

    /** La última cuota se ajusta para dejar el saldo exactamente en $0 (redondeo a pesos enteros). */
    fun tabla(monto: Long, cuotas: Int, tasaPeriodoPct: Double): List<FilaAmortizacion> {
        if (monto <= 0L || cuotas <= 0) return emptyList()
        val i = tasaPeriodoPct / 100.0
        val cuota = cuotaFija(monto, cuotas, tasaPeriodoPct)
        val filas = ArrayList<FilaAmortizacion>(cuotas)
        var saldo = monto
        for (n in 1..cuotas) {
            val interes = (saldo * i).roundToLong()
            var capital = cuota - interes
            if (n == cuotas || capital > saldo) capital = saldo
            if (capital < 0L) capital = 0L
            val saldoFinal = saldo - capital
            filas.add(FilaAmortizacion(n, saldo, capital + interes, capital, interes, saldoFinal))
            saldo = saldoFinal
        }
        return filas
    }
}

/**
 * Estado del banco. Todo se deriva de los movimientos, así nunca hay saldos descuadrados:
 * disponible = aportes − prestado + capital recuperado + intereses + mora
 * capital total = disponible + por recuperar = aportes + intereses + mora
 */
data class ResumenBanco(
    val totalAportes: Long = 0L,
    val totalPrestado: Long = 0L,
    val capitalRecuperado: Long = 0L,
    val interesesOrdinarios: Long = 0L,
    val moraCobrada: Long = 0L
) {
    val interesesGanados: Long get() = interesesOrdinarios + moraCobrada
    val disponible: Long get() = totalAportes - totalPrestado + capitalRecuperado + interesesGanados
    val porRecuperar: Long get() = totalPrestado - capitalRecuperado
    val capitalTotal: Long get() = disponible + porRecuperar
    val fraccionPrestada: Float
        get() = if (capitalTotal > 0L) (porRecuperar.toFloat() / capitalTotal).coerceIn(0f, 1f) else 0f
}

/** Situación de la próxima cuota en un instante dado (para el cronómetro). */
data class EstadoVencimiento(
    val fila: FilaAmortizacion,
    val vence: LocalDate,
    val restanteMs: Long,
    val diasAtraso: Long,
    val moraEstimada: Long
) {
    val atrasado: Boolean get() = diasAtraso > 0L
    val totalAPagar: Long get() = fila.cuota + moraEstimada
}

data class PrestamoConPagos(
    val prestamo: PrestamoEntity,
    val pagos: List<PagoEntity>
) {
    val periodicidad: Periodicidad get() = Periodicidad.desde(prestamo.periodicidad)
    val tabla: List<FilaAmortizacion> by lazy {
        Amortizacion.tabla(prestamo.monto, prestamo.plazo, prestamo.tasaPeriodo)
    }
    val desembolso: LocalDate get() = Formato.aLocalDate(prestamo.fechaCreacion)
    val cuotasPagadas: Int get() = pagos.size
    val capitalPagado: Long get() = pagos.sumOf { it.capital }
    val interesesPagados: Long get() = pagos.sumOf { it.interes }
    val moraPagada: Long get() = pagos.sumOf { it.mora }
    val saldoPendiente: Long get() = prestamo.monto - capitalPagado
    val progreso: Float
        get() = if (prestamo.monto > 0L) (capitalPagado.toFloat() / prestamo.monto).coerceIn(0f, 1f) else 0f

    fun vencimiento(cuota: Int): LocalDate = periodicidad.vencimiento(desembolso, cuota)

    val proximaCuota: FilaAmortizacion?
        get() = if (prestamo.pagado) null else tabla.getOrNull(cuotasPagadas)

    /** La cuota vence al terminar su día (medianoche en la zona horaria del teléfono). */
    fun estado(ahoraMs: Long): EstadoVencimiento? {
        val fila = proximaCuota ?: return null
        val vence = vencimiento(fila.numero)
        val zona = ZoneId.systemDefault()
        val limite = vence.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        val hoy = Instant.ofEpochMilli(ahoraMs).atZone(zona).toLocalDate()
        val dias = Tasas.diasAtraso(vence, hoy)
        return EstadoVencimiento(
            fila = fila,
            vence = vence,
            restanteMs = limite - ahoraMs,
            diasAtraso = dias,
            moraEstimada = Tasas.mora(fila.cuota, prestamo.tasaMoraEA, dias)
        )
    }
}
