package com.mipropiobanco.domain

import com.mipropiobanco.data.local.CategoriaEntity
import com.mipropiobanco.data.local.MedioPagoEntity
import com.mipropiobanco.data.local.MovimientoEntity
import com.mipropiobanco.data.local.PlantillaEntity
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.YearMonth

/** Ingreso que es un préstamo: con esto se crea la deuda sola. */
data class DatosPrestamoRecibido(val acreedor: String, val cuota: Long, val proximoPago: Long)

/** Q1 = día 1 al 15; Q2 = día 16 al último día del mes. */
enum class Quincena(val etiqueta: String) { Q1("Quincena 1"), Q2("Quincena 2") }

enum class EstadoPago { PENDIENTE, PAGADO, VENCIDO }

enum class Semaforo { VERDE, AMARILLO, ROJO }

enum class TipoObligacion(val icono: String, val etiqueta: String) {
    DEUDA("💳", "Deuda"),
    CUOTA_BANCO("🏦", "Cuota Mi banco"),
    FIJO("📌", "Gasto fijo"),
    META("🎯", "Meta")
}

/** Algo que hay que pagar en una fecha del mes. */
data class Obligacion(
    val tipo: TipoObligacion,
    val refId: Long,
    val nombre: String,
    val fecha: LocalDate,
    val monto: Long,
    val estado: EstadoPago,
    /** Para gastos fijos: la ocurrencia que se registra al pagar. */
    val ocurrencia: Ocurrencia? = null
)

/** Un ingreso esperado (fijo) en la quincena. */
data class IngresoEsperado(val nombre: String, val fecha: LocalDate, val monto: Long, val recibido: Boolean, val ocurrencia: Ocurrencia)

/** Presupuesto variable de una categoría en la quincena. */
data class RenglonVariable(val categoria: CategoriaEntity, val planeado: Long, val real: Long)

/** Ingreso por categoría: lo que esperaba (fijos) contra lo que de verdad entró. */
data class RenglonIngreso(val categoria: CategoriaEntity, val planeado: Long, val real: Long)

/** Junta los renglones de las dos quincenas por categoría. */
private fun unirVariables(a: List<RenglonVariable>, b: List<RenglonVariable>): List<RenglonVariable> =
    (a + b).groupBy { it.categoria.id }.values.map { g -> RenglonVariable(g.first().categoria, g.sumOf { it.planeado }, g.sumOf { it.real }) }
        .sortedByDescending { maxOf(it.planeado, it.real) }

private fun unirIngresos(a: List<RenglonIngreso>, b: List<RenglonIngreso>): List<RenglonIngreso> =
    (a + b).groupBy { it.categoria.id }.values.map { g -> RenglonIngreso(g.first().categoria, g.sumOf { it.planeado }, g.sumOf { it.real }) }
        .sortedByDescending { maxOf(it.planeado, it.real) }

data class ResumenQuincena(
    val quincena: Quincena,
    val desde: LocalDate,
    val hasta: LocalDate,
    val ingresosEsperados: List<IngresoEsperado>,
    val ingresosPlaneados: Long,
    val ingresosReales: Long,
    /** Lo que había en las cuentas al empezar la quincena. */
    val saldoQueViene: Long,
    val obligaciones: List<Obligacion>,
    val variables: List<RenglonVariable>,
    val ingresosPorCategoria: List<RenglonIngreso>,
    /** Todo lo que salió de verdad en la quincena (gastos, pagos de deudas, ahorro). */
    val salidasReales: Long,
    val ahorroReal: Long
) {
    val totalObligaciones: Long get() = obligaciones.sumOf { it.monto }
    val pagado: Long get() = obligaciones.filter { it.estado == EstadoPago.PAGADO }.sumOf { it.monto }
    val pendiente: Long get() = obligaciones.filter { it.estado != EstadoPago.PAGADO }.sumOf { it.monto }

    /** Ingresos que todavía no llegan (los esperados no recibidos). */
    val porRecibir: Long get() = ingresosEsperados.filter { !it.recibido }.sumOf { it.monto }

    /** Lo que tengo hoy en esta quincena (o tuve al cerrarla). */
    val disponible: Long get() = saldoQueViene + ingresosReales - salidasReales

    /** Lo que me queda libre después de pagar lo pendiente, contando lo que falta por recibir. */
    val libre: Long get() = disponible + porRecibir - pendiente

    /** Cuánto falta para cubrir todo (0 si alcanza). */
    val faltante: Long get() = (-libre).coerceAtLeast(0L)

    /**
     * 🟢 lo que entra en la quincena cubre sus pagos; 🟡 cubre solo con lo que venía de antes;
     * 🔴 no alcanza ni con eso.
     */
    val semaforo: Semaforo
        get() {
            val entra = ingresosReales + porRecibir
            val sale = salidasReales + pendiente
            return when {
                entra >= sale -> Semaforo.VERDE
                saldoQueViene + entra >= sale -> Semaforo.AMARILLO
                else -> Semaforo.ROJO
            }
        }
}

data class ResumenPresupuestoMes(
    val mes: YearMonth,
    val q1: ResumenQuincena,
    val q2: ResumenQuincena,
    val ingresosPlaneados: Long,
    val ingresosReales: Long,
    val asignado: Long,
    val ahorroReal: Long
) {
    /** Ingresos planeados − todo lo asignado (obligaciones + variables). 0 = base cero. */
    val porAsignar: Long get() = ingresosPlaneados - asignado
    val tasaAhorro: Int get() = if (ingresosReales > 0L) (ahorroReal * 100 / ingresosReales).toInt() else 0

    fun de(q: Quincena) = if (q == Quincena.Q1) q1 else q2

    val gastosPorCategoria: List<RenglonVariable> get() = unirVariables(q1.variables, q2.variables)
    val ingresosPorCategoria: List<RenglonIngreso> get() = unirIngresos(q1.ingresosPorCategoria, q2.ingresosPorCategoria)
}

object PresupuestoQuincenal {

    /** Clave guardada en el ingreso: "2026-12-1" o "2026-12-2". */
    fun claveQuincena(mes: YearMonth, q: Quincena): String = "$mes-${if (q == Quincena.Q1) 1 else 2}"

    /** Sugerencia: lo que te pagan del 26 en adelante suele ser para la quincena 1 del mes siguiente; del 11 al 25, para la 2. */
    fun quincenaSugerida(fecha: LocalDate): Pair<YearMonth, Quincena> = when {
        fecha.dayOfMonth >= 26 -> YearMonth.from(fecha).plusMonths(1) to Quincena.Q1
        fecha.dayOfMonth >= 11 -> YearMonth.from(fecha) to Quincena.Q2
        else -> YearMonth.from(fecha) to Quincena.Q1
    }

    fun quincenaDe(fecha: LocalDate): Quincena = if (fecha.dayOfMonth <= 15) Quincena.Q1 else Quincena.Q2

    fun rango(mes: YearMonth, q: Quincena): Pair<LocalDate, LocalDate> =
        if (q == Quincena.Q1) mes.atDay(1) to mes.atDay(15) else mes.atDay(16) to mes.atEndOfMonth()

    /** Día [dia] dentro del mes; si el mes es más corto (31 en noviembre), cae el último día. */
    fun diaEnMes(mes: YearMonth, dia: Int): LocalDate = mes.atDay(dia.coerceIn(1, mes.lengthOfMonth()))

    /** Parte un monto en dos quincenas; el peso que sobra va a la Q1. */
    fun partir(monto: Long): Pair<Long, Long> {
        val q2 = monto / 2
        return (monto - q2) to q2
    }

    /**
     * Cuota mensual sugerida para una meta (redondeo hacia arriba).
     * Con rendimiento E.A. usa la fórmula de anualidad; sin rendimiento, falta / meses.
     */
    fun cuotaSugeridaMeta(objetivo: Long, ahorrado: Long, mesesRestantes: Int, tasaEA: Double = 0.0): Long {
        val falta = objetivo - ahorrado
        if (falta <= 0L) return 0L
        val n = mesesRestantes.coerceAtLeast(1)
        if (tasaEA <= 0.0) return (falta + n - 1) / n
        val r = Math.pow(1 + tasaEA / 100.0, 1.0 / 12) - 1
        val faltaFuturo = objetivo - ahorrado * Math.pow(1 + r, n.toDouble())
        if (faltaFuturo <= 0.0) return 0L
        return Math.ceil(faltaFuturo * r / (Math.pow(1 + r, n.toDouble()) - 1)).toLong()
    }

    /** Todas las ocurrencias de una plantilla dentro de un rango (registradas o no). */
    fun ocurrenciasEn(p: PlantillaEntity, desde: LocalDate, hasta: LocalDate): List<Ocurrencia> {
        val lista = mutableListOf<Ocurrencia>()
        var i = 0
        while (i < 5_000) {
            val f = Recurrencia.fecha(p, i)
            if (f.isAfter(hasta)) break
            if (!f.isBefore(desde)) lista += Ocurrencia(p, i, f)
            i++
        }
        return lista
    }

    fun construir(
        mes: YearMonth,
        hoy: LocalDate,
        movimientos: List<MovimientoEntity>,
        medios: List<MedioPagoEntity>,
        categorias: List<CategoriaEntity>,
        plantillas: List<PlantillaEntity>,
        deudas: List<DeudaConAbonos>,
        prestamos: List<PrestamoConPagos>,
        metas: List<Meta>
    ): ResumenPresupuestoMes {
        val cats = categorias.associateBy { it.id }
        val inicioMes = mes.atDay(1)
        val finMes = mes.atEndOfMonth()

        // ---- Obligaciones con fecha en el mes ----
        val obligaciones = mutableListOf<Obligacion>()

        // Deudas: la fecha manda la misma que ves en Deudas (próximo pago).
        // Meses anteriores a ese próximo pago solo muestran lo que sí abonaste.
        deudas.forEach { d ->
            val abonosMes = d.abonos.filter { YearMonth.from(Formato.aLocalDate(it.fecha)) == mes }
            val prox = d.proximoVencimiento(hoy)
            val mesProx = YearMonth.from(prox)
            val pagadosAntes = if (mes == mesProx) abonosMes.filter { Formato.aLocalDate(it.fecha).isBefore(prox) } else abonosMes
            if (pagadosAntes.isNotEmpty() && !mes.isAfter(mesProx)) {
                obligaciones += Obligacion(
                    TipoObligacion.DEUDA, d.deuda.id, d.deuda.acreedor,
                    Formato.aLocalDate(pagadosAntes.minOf { it.fecha }), pagadosAntes.sumOf { it.monto }, EstadoPago.PAGADO
                )
            }
            if (d.pagada || mes.isBefore(mesProx)) return@forEach
            val vence = if (mes == mesProx) prox else diaEnMes(mes, prox.dayOfMonth)
            val monto = minOf(d.deuda.pagoMinimo.coerceAtLeast(1L), d.saldo)
            val estado = if (vence.isBefore(hoy)) EstadoPago.VENCIDO else EstadoPago.PENDIENTE
            obligaciones += Obligacion(TipoObligacion.DEUDA, d.deuda.id, d.deuda.acreedor, vence, monto, estado)
        }

        // Solo autopréstamos: la cuota de un préstamo a otra persona es plata que me entra.
        prestamos.filter { it.prestamo.prestatario.isBlank() }.forEach { p ->
            p.tabla.forEach { fila ->
                val vence = p.vencimiento(fila.numero)
                if (vence.isBefore(inicioMes) || vence.isAfter(finMes)) return@forEach
                val pagada = fila.numero <= p.cuotasPagadas
                val estado = when {
                    pagada -> EstadoPago.PAGADO
                    vence.isBefore(hoy) -> EstadoPago.VENCIDO
                    else -> EstadoPago.PENDIENTE
                }
                obligaciones += Obligacion(
                    TipoObligacion.CUOTA_BANCO, p.prestamo.id,
                    "Cuota ${fila.numero} · ${p.prestamo.concepto}", vence, fila.cuota, estado
                )
            }
        }

        val ingresosEsperados = mutableListOf<IngresoEsperado>()
        plantillas.filter { it.activa }.forEach { pl ->
            ocurrenciasEn(pl, inicioMes, finMes).forEach { o ->
                val registrada = o.indice <= pl.ultimaOcurrencia
                if (pl.tipo == TipoMovimiento.INGRESO.name) {
                    ingresosEsperados += IngresoEsperado(pl.nombre, o.fecha, pl.monto, registrada, o)
                } else if (pl.tipo == TipoMovimiento.GASTO.name) {
                    val estado = when {
                        registrada -> EstadoPago.PAGADO
                        o.fecha.isBefore(hoy) -> EstadoPago.VENCIDO
                        else -> EstadoPago.PENDIENTE
                    }
                    obligaciones += Obligacion(TipoObligacion.FIJO, pl.id, pl.nombre, o.fecha, pl.monto, estado, o)
                }
            }
        }

        // Metas: la cuota del mes se reparte en las dos quincenas (día 1 y día 16).
        val esMetaCat = { c: CategoriaEntity? -> c != null && Interprete.normalizar(c.nombre).contains("ahorro para metas") }
        metas.filter { !it.cumplida }.forEach { m ->
            val meses = if (m.fechaLimite > 0L) {
                val limite = YearMonth.from(Formato.aLocalDate(m.fechaLimite))
                ((limite.year - mes.year) * 12 + (limite.monthValue - mes.monthValue) + 1).coerceAtLeast(1)
            } else 12
            val cuota = cuotaSugeridaMeta(m.objetivo, m.ahorrado, meses)
            if (cuota <= 0L) return@forEach
            val (a, b) = partir(cuota)
            listOf(Quincena.Q1 to a, Quincena.Q2 to b).forEach { (q, monto) ->
                if (monto <= 0L) return@forEach
                val (desde, hasta) = rango(mes, q)
                val abonado = movimientos.filter {
                    it.tipo == TipoMovimiento.GASTO.name && esMetaCat(cats[it.categoriaId]) &&
                        it.nota.contains(m.nombre, ignoreCase = true) &&
                        Formato.aLocalDate(it.fecha).let { f -> !f.isBefore(desde) && !f.isAfter(hasta) }
                }.sumOf { it.monto }
                val estado = when {
                    abonado >= monto -> EstadoPago.PAGADO
                    hasta.isBefore(hoy) -> EstadoPago.VENCIDO
                    else -> EstadoPago.PENDIENTE
                }
                obligaciones += Obligacion(TipoObligacion.META, m.id, "${m.icono} ${m.nombre}", desde, monto, estado)
            }
        }

        // ---- Por quincena ----
        val inicioMs = Formato.aMillis(inicioMes)
        val clavesMes = setOf(claveQuincena(mes, Quincena.Q1), claveQuincena(mes, Quincena.Q2))
        // Ingresos que llegaron antes del mes pero son para este mes: se cuentan en su quincena, no en "lo que venía".
        val adelantados = movimientos.filter { it.fecha < inicioMs && it.tipo == TipoMovimiento.INGRESO.name && it.quincena in clavesMes }.sumOf { it.monto }
        val saldoInicioMes = calcularSaldos(medios, movimientos.filter { it.fecha < inicioMs }).values.sum() - adelantados
        val variablesCats = categorias.filter { it.tipo == TipoMovimiento.GASTO.name && it.presupuestoMensual > 0L }
        val fijosCats = plantillas.filter { it.tipo == TipoMovimiento.GASTO.name }.map { it.categoriaId }.toSet()

        fun resumen(q: Quincena, saldoQueViene: Long): ResumenQuincena {
            val (desde, hasta) = rango(mes, q)
            val delRango = movimientos.filter {
                val f = Formato.aLocalDate(it.fecha); !f.isBefore(desde) && !f.isAfter(hasta)
            }
            val claveQ = claveQuincena(mes, q)
            // Un ingreso cuenta en la quincena que tú le asignaste; si no le asignaste, en la de su fecha.
            val ingresosQ = movimientos.filter {
                it.tipo == TipoMovimiento.INGRESO.name && (
                    if (it.quincena.isNotBlank()) it.quincena == claveQ
                    else Formato.aLocalDate(it.fecha).let { f -> !f.isBefore(desde) && !f.isAfter(hasta) }
                    )
            }
            val ingresos = ingresosQ.sumOf { it.monto }
            val gastos = delRango.filter { it.tipo == TipoMovimiento.GASTO.name }
            val ahorro = gastos.filter { Cuadre.esAhorro(cats[it.categoriaId]) }.sumOf { it.monto }
            val esperados = ingresosEsperados.filter { quincenaDe(it.fecha) == q }.sortedBy { it.fecha }
            val realPorCat = gastos.groupBy { it.categoriaId }.mapValues { e -> e.value.sumOf { it.monto } }
            val variables = categorias.filter { c ->
                c.tipo == TipoMovimiento.GASTO.name &&
                    ((c.presupuestoMensual > 0L && c.id !in fijosCats) || (realPorCat[c.id] ?: 0L) > 0L)
            }.map { c ->
                val (p1, p2) = partir(if (c.id in fijosCats) 0L else c.presupuestoMensual)
                RenglonVariable(c, if (q == Quincena.Q1) p1 else p2, realPorCat[c.id] ?: 0L)
            }.sortedByDescending { maxOf(it.planeado, it.real) }
            val ingresosReales = ingresosQ
                .groupBy { it.categoriaId }.mapValues { e -> e.value.sumOf { it.monto } }
            val ingresosPlan = esperados.groupBy { it.ocurrencia.plantilla.categoriaId }
                .mapValues { e -> e.value.sumOf { it.monto } }
            val ingresosCat = (ingresosReales.keys + ingresosPlan.keys).mapNotNull { id ->
                cats[id]?.let { RenglonIngreso(it, ingresosPlan[id] ?: 0L, ingresosReales[id] ?: 0L) }
            }.sortedByDescending { maxOf(it.planeado, it.real) }
            return ResumenQuincena(
                quincena = q, desde = desde, hasta = hasta,
                ingresosEsperados = esperados,
                ingresosPlaneados = esperados.sumOf { it.monto },
                ingresosReales = ingresos,
                saldoQueViene = saldoQueViene,
                obligaciones = obligaciones.filter { quincenaDe(it.fecha) == q }
                    .sortedWith(compareBy({ it.estado != EstadoPago.VENCIDO }, { it.fecha })),
                variables = variables,
                ingresosPorCategoria = ingresosCat,
                salidasReales = gastos.sumOf { it.monto },
                ahorroReal = ahorro
            )
        }

        val q1 = resumen(Quincena.Q1, saldoInicioMes)
        val q2 = resumen(Quincena.Q2, q1.disponible)
        val asignado = obligaciones.sumOf { it.monto } +
            variablesCats.filter { it.id !in fijosCats }.sumOf { it.presupuestoMensual }
        return ResumenPresupuestoMes(
            mes = mes, q1 = q1, q2 = q2,
            ingresosPlaneados = q1.ingresosPlaneados + q2.ingresosPlaneados,
            ingresosReales = q1.ingresosReales + q2.ingresosReales,
            asignado = asignado,
            ahorroReal = q1.ahorroReal + q2.ahorroReal
        )
    }
}
