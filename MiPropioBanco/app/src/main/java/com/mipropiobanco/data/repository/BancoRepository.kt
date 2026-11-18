package com.mipropiobanco.data.repository

import androidx.room.withTransaction
import com.mipropiobanco.data.local.AporteEntity
import com.mipropiobanco.data.local.BancoDatabase
import com.mipropiobanco.data.local.PagoEntity
import com.mipropiobanco.data.local.PrestamoEntity
import com.mipropiobanco.domain.Amortizacion
import com.mipropiobanco.domain.PrestamoConPagos
import com.mipropiobanco.domain.ResumenBanco
import com.mipropiobanco.util.Formato
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Resultado de registrar una cuota, para informar al usuario. */
data class ResultadoPago(val pago: PagoEntity, val prestamoLiquidado: Boolean)

class BancoRepository(private val db: BancoDatabase) {

    private val aporteDao = db.aporteDao()
    private val prestamoDao = db.prestamoDao()
    private val pagoDao = db.pagoDao()

    val aportes: Flow<List<AporteEntity>> = aporteDao.observarTodos()

    private val prestamos: Flow<List<PrestamoEntity>> = prestamoDao.observarTodos()
    private val pagos: Flow<List<PagoEntity>> = pagoDao.observarTodos()

    val resumen: Flow<ResumenBanco> = combine(aportes, prestamos, pagos) { a, p, g ->
        ResumenBanco(
            totalAportes = a.sumOf { it.monto },
            totalPrestado = p.sumOf { it.monto },
            capitalRecuperado = g.sumOf { it.capital },
            interesesGanados = g.sumOf { it.interes }
        )
    }

    val prestamosConPagos: Flow<List<PrestamoConPagos>> = combine(prestamos, pagos) { p, g ->
        val porPrestamo = g.groupBy { it.prestamoId }
        p.map { prestamo ->
            PrestamoConPagos(prestamo, porPrestamo[prestamo.id].orEmpty().sortedBy { it.numeroCuota })
        }
    }

    private suspend fun resumenActual(): ResumenBanco = ResumenBanco(
        totalAportes = aporteDao.sumarMontos(),
        totalPrestado = prestamoDao.sumarMontos(),
        capitalRecuperado = pagoDao.sumarCapital(),
        interesesGanados = pagoDao.sumarInteres()
    )

    suspend fun registrarAporte(fecha: Long, concepto: String, monto: Long) {
        require(monto > 0L) { "El monto del aporte debe ser mayor que cero" }
        require(concepto.isNotBlank()) { "Escribe el concepto del aporte" }
        aporteDao.insertar(AporteEntity(fecha = fecha, concepto = concepto.trim(), monto = monto))
    }

    suspend fun eliminarAporte(aporte: AporteEntity) = db.withTransaction {
        val r = resumenActual()
        check(r.disponible >= aporte.monto) {
            "No se puede eliminar: parte de ese dinero está prestado. Disponible: ${Formato.cop(r.disponible)}"
        }
        aporteDao.eliminar(aporte)
    }

    suspend fun crearPrestamo(concepto: String, monto: Long, plazoMeses: Int, tasaMensual: Double): Long =
        db.withTransaction {
            require(concepto.isNotBlank()) { "Escribe el concepto del préstamo" }
            require(monto > 0L) { "El monto debe ser mayor que cero" }
            require(plazoMeses in 1..120) { "El plazo debe estar entre 1 y 120 meses" }
            require(tasaMensual in 0.0..10.0) { "La tasa debe estar entre 0% y 10% mensual" }
            val r = resumenActual()
            check(monto <= r.disponible) {
                "Fondos insuficientes: en la bóveda hay ${Formato.cop(r.disponible)}"
            }
            prestamoDao.insertar(
                PrestamoEntity(
                    concepto = concepto.trim(),
                    monto = monto,
                    plazoMeses = plazoMeses,
                    tasaMensual = tasaMensual,
                    cuota = Amortizacion.cuotaFija(monto, plazoMeses, tasaMensual),
                    fechaCreacion = Formato.hoyMillis()
                )
            )
        }

    /** Registra la siguiente cuota pendiente según la tabla de amortización. */
    suspend fun registrarPagoCuota(prestamoId: Long): ResultadoPago = db.withTransaction {
        val prestamo = prestamoDao.obtenerPorId(prestamoId)
            ?: throw IllegalStateException("El préstamo no existe")
        check(!prestamo.pagado) { "Este préstamo ya está pagado" }
        val pagados = pagoDao.obtenerPorPrestamo(prestamoId)
        val tabla = Amortizacion.tabla(prestamo.monto, prestamo.plazoMeses, prestamo.tasaMensual)
        val fila = tabla.getOrNull(pagados.size)
            ?: throw IllegalStateException("No hay cuotas pendientes")
        val pago = PagoEntity(
            prestamoId = prestamoId,
            numeroCuota = fila.numero,
            fecha = Formato.hoyMillis(),
            capital = fila.capital,
            interes = fila.interes
        )
        pagoDao.insertar(pago)
        val liquidado = fila.numero >= tabla.size || fila.saldoFinal <= 0L
        if (liquidado) prestamoDao.actualizar(prestamo.copy(pagado = true))
        ResultadoPago(pago, liquidado)
    }

    /** Corrige un pago registrado por error. */
    suspend fun deshacerUltimoPago(prestamoId: Long) = db.withTransaction {
        val prestamo = prestamoDao.obtenerPorId(prestamoId)
            ?: throw IllegalStateException("El préstamo no existe")
        val ultimo = pagoDao.obtenerPorPrestamo(prestamoId).maxByOrNull { it.numeroCuota }
            ?: throw IllegalStateException("Este préstamo no tiene pagos")
        val r = resumenActual()
        check(r.disponible >= ultimo.capital + ultimo.interes) {
            "No se puede deshacer: ese dinero ya se volvió a prestar"
        }
        pagoDao.eliminar(ultimo)
        if (prestamo.pagado) prestamoDao.actualizar(prestamo.copy(pagado = false))
    }

    /** Solo se puede anular un préstamo sin pagos; el dinero vuelve completo a la bóveda. */
    suspend fun anularPrestamo(prestamoId: Long) = db.withTransaction {
        val prestamo = prestamoDao.obtenerPorId(prestamoId)
            ?: throw IllegalStateException("El préstamo no existe")
        check(pagoDao.obtenerPorPrestamo(prestamoId).isEmpty()) {
            "Solo se pueden anular préstamos sin pagos registrados"
        }
        prestamoDao.eliminar(prestamo)
    }
}
