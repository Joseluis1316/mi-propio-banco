package com.mipropiobanco.domain

import com.mipropiobanco.data.local.PagoEntity
import com.mipropiobanco.data.local.PrestamoEntity
import kotlin.math.pow
import kotlin.math.roundToLong

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

    fun cuotaFija(monto: Long, plazoMeses: Int, tasaMensualPct: Double): Long {
        if (monto <= 0L || plazoMeses <= 0) return 0L
        val i = tasaMensualPct / 100.0
        if (i <= 0.0) return (monto + plazoMeses - 1) / plazoMeses
        return (monto * i / (1.0 - (1.0 + i).pow(-plazoMeses))).roundToLong()
    }

    /** La última cuota se ajusta para dejar el saldo exactamente en $0 (redondeo a pesos enteros). */
    fun tabla(monto: Long, plazoMeses: Int, tasaMensualPct: Double): List<FilaAmortizacion> {
        if (monto <= 0L || plazoMeses <= 0) return emptyList()
        val i = tasaMensualPct / 100.0
        val cuota = cuotaFija(monto, plazoMeses, tasaMensualPct)
        val filas = ArrayList<FilaAmortizacion>(plazoMeses)
        var saldo = monto
        for (n in 1..plazoMeses) {
            val interes = (saldo * i).roundToLong()
            var capital = cuota - interes
            if (n == plazoMeses || capital > saldo) capital = saldo
            if (capital < 0L) capital = 0L
            val saldoFinal = saldo - capital
            filas.add(FilaAmortizacion(n, saldo, capital + interes, capital, interes, saldoFinal))
            saldo = saldoFinal
        }
        return filas
    }

    fun tasaEfectivaAnual(tasaMensualPct: Double): Double =
        ((1.0 + tasaMensualPct / 100.0).pow(12) - 1.0) * 100.0
}

/**
 * Estado del banco. Todo se deriva de los movimientos, así nunca hay saldos descuadrados:
 * disponible = aportes − prestado + capital recuperado + intereses
 * capital total = disponible + por recuperar = aportes + intereses
 */
data class ResumenBanco(
    val totalAportes: Long = 0L,
    val totalPrestado: Long = 0L,
    val capitalRecuperado: Long = 0L,
    val interesesGanados: Long = 0L
) {
    val disponible: Long get() = totalAportes - totalPrestado + capitalRecuperado + interesesGanados
    val porRecuperar: Long get() = totalPrestado - capitalRecuperado
    val capitalTotal: Long get() = disponible + porRecuperar
    val fraccionPrestada: Float
        get() = if (capitalTotal > 0L) (porRecuperar.toFloat() / capitalTotal).coerceIn(0f, 1f) else 0f
}

data class PrestamoConPagos(
    val prestamo: PrestamoEntity,
    val pagos: List<PagoEntity>
) {
    val cuotasPagadas: Int get() = pagos.size
    val capitalPagado: Long get() = pagos.sumOf { it.capital }
    val interesesPagados: Long get() = pagos.sumOf { it.interes }
    val saldoPendiente: Long get() = prestamo.monto - capitalPagado
    val progreso: Float
        get() = if (prestamo.monto > 0L) (capitalPagado.toFloat() / prestamo.monto).coerceIn(0f, 1f) else 0f
}
