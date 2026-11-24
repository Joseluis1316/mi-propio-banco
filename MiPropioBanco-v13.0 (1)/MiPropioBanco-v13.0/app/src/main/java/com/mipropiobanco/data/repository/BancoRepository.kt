package com.mipropiobanco.data.repository

import androidx.room.withTransaction
import com.mipropiobanco.data.local.AbonoDeudaEntity
import com.mipropiobanco.data.local.AporteEntity
import com.mipropiobanco.data.local.BancoDatabase
import com.mipropiobanco.data.local.DeudaEntity
import com.mipropiobanco.data.local.PagoEntity
import com.mipropiobanco.data.local.PrestamoEntity
import com.mipropiobanco.data.local.TipoPrestamoEntity
import com.mipropiobanco.domain.Amortizacion
import com.mipropiobanco.domain.DeudaConAbonos
import com.mipropiobanco.domain.Periodicidad
import com.mipropiobanco.domain.PrestamoConPagos
import com.mipropiobanco.domain.ResumenBanco
import com.mipropiobanco.domain.Tasas
import com.mipropiobanco.util.Formato
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Resultado de registrar una cuota, para informar al usuario. */
data class ResultadoPago(val pago: PagoEntity, val prestamoLiquidado: Boolean)

class BancoRepository(private val db: BancoDatabase) {

    private val aporteDao = db.aporteDao()
    private val tipoDao = db.tipoPrestamoDao()
    private val prestamoDao = db.prestamoDao()
    private val pagoDao = db.pagoDao()
    private val deudaDao = db.deudaDao()
    private val abonoDao = db.abonoDeudaDao()

    val aportes: Flow<List<AporteEntity>> = aporteDao.observarTodos()
    val tipos: Flow<List<TipoPrestamoEntity>> = tipoDao.observarTodos()

    private val prestamos: Flow<List<PrestamoEntity>> = prestamoDao.observarTodos()
    private val pagos: Flow<List<PagoEntity>> = pagoDao.observarTodos()

    val resumen: Flow<ResumenBanco> = combine(aportes, prestamos, pagos) { a, p, g ->
        ResumenBanco(
            totalAportes = a.sumOf { it.monto },
            totalPrestado = p.sumOf { it.monto },
            capitalRecuperado = g.sumOf { it.capital },
            interesesOrdinarios = g.sumOf { it.interes },
            moraCobrada = g.sumOf { it.mora }
        )
    }

    val prestamosConPagos: Flow<List<PrestamoConPagos>> = combine(prestamos, pagos) { p, g ->
        val porPrestamo = g.groupBy { it.prestamoId }
        p.map { prestamo ->
            PrestamoConPagos(prestamo, porPrestamo[prestamo.id].orEmpty().sortedBy { it.numeroCuota })
        }
    }

    val deudas: Flow<List<DeudaConAbonos>> =
        combine(deudaDao.observarTodas(), abonoDao.observarTodos()) { d, a ->
            val porDeuda = a.groupBy { it.deudaId }
            d.map { DeudaConAbonos(it, porDeuda[it.id].orEmpty()) }
        }

    private suspend fun resumenActual(): ResumenBanco = ResumenBanco(
        totalAportes = aporteDao.sumarMontos(),
        totalPrestado = prestamoDao.sumarMontos(),
        capitalRecuperado = pagoDao.sumarCapital(),
        interesesOrdinarios = pagoDao.sumarInteres(),
        moraCobrada = pagoDao.sumarMora()
    )

    // ---------------- Aportes ----------------

    suspend fun registrarAporte(fecha: Long, concepto: String, monto: Long) {
        require(monto > 0L) { "El monto del aporte debe ser mayor que cero" }
        require(concepto.isNotBlank()) { "Escribe el concepto del aporte" }
        aporteDao.insertar(AporteEntity(fecha = fecha, concepto = concepto.trim(), monto = monto))
    }

    suspend fun eliminarAporte(aporte: AporteEntity) = db.withTransaction {
        val r = resumenActual()
        check(r.disponible >= aporte.monto) {
            "No se puede eliminar: parte de ese dinero está prestado. Disponible: ${Formato.copReal(r.disponible)}"
        }
        aporteDao.eliminar(aporte)
    }

    // ---------------- Tipos de préstamo ----------------

    suspend fun guardarTipo(id: Long?, nombre: String, tasaEA: Double, tasaMoraEA: Double) {
        require(nombre.isNotBlank()) { "Escribe el nombre del tipo de préstamo" }
        require(tasaEA in 0.0..300.0) { "La tasa E.A. debe estar entre 0% y 300%" }
        require(tasaMoraEA in 0.0..300.0) { "La tasa de mora debe estar entre 0% y 300%" }
        val tipo = TipoPrestamoEntity(id = id ?: 0L, nombre = nombre.trim(), tasaEA = tasaEA, tasaMoraEA = tasaMoraEA)
        if (id == null) tipoDao.insertar(tipo) else tipoDao.actualizar(tipo)
    }

    suspend fun eliminarTipo(tipo: TipoPrestamoEntity) = tipoDao.eliminar(tipo)

    // ---------------- Autopréstamos ----------------

    suspend fun crearPrestamo(
        concepto: String,
        monto: Long,
        cuotas: Int,
        periodicidad: Periodicidad,
        tipo: String,
        tasaEA: Double,
        tasaMoraEA: Double,
        fechaDesembolso: Long,
        prestatario: String = ""
    ): Long = db.withTransaction {
        require(concepto.isNotBlank()) { "Escribe el concepto del préstamo" }
        require(monto > 0L) { "El monto debe ser mayor que cero" }
        require(cuotas in 1..1000) { "El número de cuotas debe estar entre 1 y 1000" }
        require(tasaEA in 0.0..300.0) { "La tasa E.A. debe estar entre 0% y 300%" }
        require(tasaMoraEA in 0.0..300.0) { "La tasa de mora debe estar entre 0% y 300%" }
        val r = resumenActual()
        check(monto <= r.disponible) {
            "Fondos insuficientes: en la bóveda hay ${Formato.copReal(r.disponible)}"
        }
        val tasaPeriodo = Tasas.periodoDesdeEA(tasaEA, periodicidad)
        prestamoDao.insertar(
            PrestamoEntity(
                concepto = concepto.trim(),
                monto = monto,
                plazo = cuotas,
                tasaPeriodo = tasaPeriodo,
                cuota = Amortizacion.cuotaFija(monto, cuotas, tasaPeriodo),
                fechaCreacion = fechaDesembolso,
                periodicidad = periodicidad.name,
                tipo = tipo,
                prestatario = prestatario.trim(),
                tasaEA = tasaEA,
                tasaMoraEA = tasaMoraEA
            )
        )
    }

    /** Registra la siguiente cuota pendiente; si se paga después del vencimiento, cobra mora por día. */
    suspend fun registrarPagoCuota(prestamoId: Long, fechaPago: Long): ResultadoPago = db.withTransaction {
        val prestamo = prestamoDao.obtenerPorId(prestamoId)
            ?: throw IllegalStateException("El préstamo no existe")
        check(!prestamo.pagado) { "Este préstamo ya está pagado" }
        val item = PrestamoConPagos(prestamo, pagoDao.obtenerPorPrestamo(prestamoId))
        val fila = item.proximaCuota ?: throw IllegalStateException("No hay cuotas pendientes")
        val dias = Tasas.diasAtraso(item.vencimiento(fila.numero), Formato.aLocalDate(fechaPago))
        val pago = PagoEntity(
            prestamoId = prestamoId,
            numeroCuota = fila.numero,
            fecha = fechaPago,
            capital = fila.capital,
            interes = fila.interes,
            mora = Tasas.mora(fila.cuota, prestamo.tasaMoraEA, dias),
            diasAtraso = dias.toInt()
        )
        pagoDao.insertar(pago)
        val liquidado = fila.numero >= item.tabla.size || fila.saldoFinal <= 0L
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
        check(r.disponible >= ultimo.capital + ultimo.interes + ultimo.mora) {
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

    // ---------------- Deudas con terceros ----------------

    /** Crea o edita una deuda. Al editar, [saldoActual] ajusta el saldo respetando los pagos ya hechos. */
    suspend fun guardarDeuda(
        id: Long?,
        acreedor: String,
        saldoActual: Long,
        pagoMinimo: Long,
        tasaEA: Double,
        diaPago: Int,
        proximoPago: Long = 0L
    ) = db.withTransaction {
        require(acreedor.isNotBlank()) { "Escribe a quién le debes" }
        require(saldoActual > 0L) { "El saldo debe ser mayor que cero" }
        require(pagoMinimo > 0L) { "El pago mínimo debe ser mayor que cero" }
        require(tasaEA in 0.0..300.0) { "La tasa E.A. debe estar entre 0% y 300%" }
        require(diaPago in 1..31) { "El día de pago debe estar entre 1 y 31" }
        if (id == null) {
            deudaDao.insertar(
                DeudaEntity(
                    acreedor = acreedor.trim(),
                    saldoInicial = saldoActual,
                    pagoMinimo = pagoMinimo,
                    tasaEA = tasaEA,
                    diaPago = diaPago,
                    fechaRegistro = Formato.hoyMillis(),
                    proximoPago = proximoPago
                )
            )
        } else {
            val actual = deudaDao.obtenerPorId(id) ?: throw IllegalStateException("La deuda no existe")
            val abonado = abonoDao.obtenerPorDeuda(id).sumOf { it.monto }
            deudaDao.actualizar(
                actual.copy(
                    acreedor = acreedor.trim(),
                    saldoInicial = saldoActual + abonado,
                    pagoMinimo = pagoMinimo,
                    tasaEA = tasaEA,
                    diaPago = diaPago,
                    proximoPago = if (proximoPago > 0L) proximoPago else actual.proximoPago
                )
            )
        }
    }

    suspend fun eliminarDeuda(deudaId: Long) = db.withTransaction {
        val deuda = deudaDao.obtenerPorId(deudaId) ?: throw IllegalStateException("La deuda no existe")
        deudaDao.eliminar(deuda)
    }

    /** Registra un pago; si supera el saldo, se ajusta al saldo pendiente. Devuelve el saldo restante. */
    suspend fun abonarDeuda(deudaId: Long, monto: Long, fecha: Long): Long = db.withTransaction {
        require(monto > 0L) { "El pago debe ser mayor que cero" }
        val deuda = deudaDao.obtenerPorId(deudaId) ?: throw IllegalStateException("La deuda no existe")
        val item = DeudaConAbonos(deuda, abonoDao.obtenerPorDeuda(deudaId))
        check(!item.pagada) { "Esta deuda ya está pagada" }
        val aplicado = minOf(monto, item.saldo)
        abonoDao.insertar(AbonoDeudaEntity(deudaId = deudaId, fecha = fecha, monto = aplicado))
        // Si la deuda tiene fecha exacta y con este pago se cubre la cuota, el próximo pago pasa al mes siguiente.
        if (deuda.proximoPago > 0L) {
            val vence = Formato.aLocalDate(deuda.proximoPago)
            val desde = vence.minusDays(25)
            val enCiclo = abonoDao.obtenerPorDeuda(deudaId)
                .filter { !Formato.aLocalDate(it.fecha).isBefore(desde) }
                .sumOf { it.monto }
            if (enCiclo >= minOf(deuda.pagoMinimo, item.saldo)) {
                val siguiente = java.time.YearMonth.from(vence).plusMonths(1)
                val dia = deuda.diaPago.coerceIn(1, siguiente.lengthOfMonth())
                deudaDao.actualizar(deuda.copy(proximoPago = Formato.aMillis(siguiente.atDay(dia))))
            }
        }
        item.saldo - aplicado
    }

    suspend fun eliminarAbono(abono: AbonoDeudaEntity) = abonoDao.eliminar(abono)
}
