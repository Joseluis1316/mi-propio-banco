package com.mipropiobanco.domain

import com.mipropiobanco.data.local.CategoriaEntity
import com.mipropiobanco.data.local.MovimientoEntity
import com.mipropiobanco.data.local.PlantillaEntity
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.YearMonth

enum class TipoMovimiento(val etiqueta: String) {
    GASTO("Gasto"),
    INGRESO("Ingreso");

    companion object {
        fun desde(nombre: String?): TipoMovimiento = entries.firstOrNull { it.name == nombre } ?: GASTO
    }
}

/** Una repetición concreta de una plantilla fija. */
data class Ocurrencia(val plantilla: PlantillaEntity, val indice: Int, val fecha: LocalDate)

object Recurrencia {
    private const val MAX_ITERACIONES = 20_000

    fun fecha(p: PlantillaEntity, indice: Int): LocalDate =
        Periodicidad.desde(p.periodicidad).vencimiento(Formato.aLocalDate(p.fechaInicio), indice)

    /** Repeticiones que ya llegaron (fecha ≤ hoy) y no se han registrado ni omitido. */
    fun pendientes(p: PlantillaEntity, hoy: LocalDate): List<Ocurrencia> {
        if (!p.activa) return emptyList()
        val lista = mutableListOf<Ocurrencia>()
        var i = p.ultimaOcurrencia + 1
        while (i < MAX_ITERACIONES) {
            val f = fecha(p, i)
            if (f.isAfter(hoy)) break
            lista.add(Ocurrencia(p, i, f))
            i++
        }
        return lista
    }

    /** Repeticiones aún no registradas que caen entre [desde] y [hasta] (para el calendario). */
    fun enRango(p: PlantillaEntity, desde: LocalDate, hasta: LocalDate): List<Ocurrencia> {
        if (!p.activa) return emptyList()
        val lista = mutableListOf<Ocurrencia>()
        var i = p.ultimaOcurrencia + 1
        while (i < MAX_ITERACIONES) {
            val f = fecha(p, i)
            if (f.isAfter(hasta)) break
            if (!f.isBefore(desde)) lista.add(Ocurrencia(p, i, f))
            i++
        }
        return lista
    }

    fun proxima(p: PlantillaEntity): LocalDate = fecha(p, p.ultimaOcurrencia + 1)
}

data class ResumenMes(
    val ingresos: Long,
    val gastos: Long,
    val gastoPorCategoria: Map<Long, Long>,
    val ingresoPorCategoria: Map<Long, Long>,
    val gastoPorMedio: Map<Long, Long>,
    val ingresoPorMedio: Map<Long, Long>
) {
    val balance: Long get() = ingresos - gastos

    companion object {
        fun de(movimientos: List<MovimientoEntity>, mes: YearMonth): ResumenMes {
            val delMes = movimientos.filter { YearMonth.from(Formato.aLocalDate(it.fecha)) == mes }
            val gastos = delMes.filter { it.tipo == TipoMovimiento.GASTO.name }
            val ingresos = delMes.filter { it.tipo == TipoMovimiento.INGRESO.name }
            return ResumenMes(
                ingresos = ingresos.sumOf { it.monto },
                gastos = gastos.sumOf { it.monto },
                gastoPorCategoria = gastos.groupBy { it.categoriaId }.mapValues { e -> e.value.sumOf { it.monto } },
                ingresoPorCategoria = ingresos.groupBy { it.categoriaId }.mapValues { e -> e.value.sumOf { it.monto } },
                gastoPorMedio = gastos.groupBy { it.medioId }.mapValues { e -> e.value.sumOf { it.monto } },
                ingresoPorMedio = ingresos.groupBy { it.medioId }.mapValues { e -> e.value.sumOf { it.monto } }
            )
        }
    }
}

/** Avance del presupuesto de una categoría de gasto en el mes. */
data class AvancePresupuesto(val categoria: CategoriaEntity, val gastado: Long) {
    val limite: Long get() = categoria.presupuestoMensual
    val fraccion: Float get() = if (limite > 0L) (gastado.toFloat() / limite) else 0f
    val excedido: Boolean get() = limite > 0L && gastado > limite
    val restante: Long get() = limite - gastado
}

// ---------------- Calendario ----------------

enum class TipoEvento { INGRESO, GASTO, PROGRAMADO, CUOTA_PRESTAMO, PAGO_DEUDA }

data class EventoCalendario(
    val fecha: LocalDate,
    val tipo: TipoEvento,
    val titulo: String,
    val monto: Long,
    val detalle: String = ""
)

object Calendario {
    fun eventos(
        mes: YearMonth,
        movimientos: List<MovimientoEntity>,
        categorias: Map<Long, CategoriaEntity>,
        plantillas: List<PlantillaEntity>,
        prestamos: List<PrestamoConPagos>,
        deudas: List<DeudaConAbonos>
    ): Map<LocalDate, List<EventoCalendario>> {
        val desde = mes.atDay(1)
        val hasta = mes.atEndOfMonth()
        val lista = mutableListOf<EventoCalendario>()

        movimientos.forEach { m ->
            val f = Formato.aLocalDate(m.fecha)
            if (!f.isBefore(desde) && !f.isAfter(hasta)) {
                val c = categorias[m.categoriaId]
                val esIngreso = m.tipo == TipoMovimiento.INGRESO.name
                lista.add(
                    EventoCalendario(
                        fecha = f,
                        tipo = if (esIngreso) TipoEvento.INGRESO else TipoEvento.GASTO,
                        titulo = "${c?.icono.orEmpty()} ${c?.nombre ?: "Sin categoría"}".trim(),
                        monto = m.monto,
                        detalle = m.nota
                    )
                )
            }
        }
        plantillas.forEach { p ->
            Recurrencia.enRango(p, desde, hasta).forEach { o ->
                val c = categorias[p.categoriaId]
                lista.add(
                    EventoCalendario(
                        fecha = o.fecha,
                        tipo = TipoEvento.PROGRAMADO,
                        titulo = "${c?.icono.orEmpty()} ${p.nombre}".trim(),
                        monto = p.monto,
                        detalle = (if (p.tipo == TipoMovimiento.INGRESO.name) "Ingreso fijo" else "Gasto fijo") +
                            if (p.automatica) " · automático" else " · por confirmar"
                    )
                )
            }
        }
        prestamos.filter { !it.prestamo.pagado }.forEach { item ->
            item.tabla.drop(item.cuotasPagadas).forEach { fila ->
                val f = item.vencimiento(fila.numero)
                if (!f.isBefore(desde) && !f.isAfter(hasta)) {
                    lista.add(
                        EventoCalendario(
                            fecha = f,
                            tipo = TipoEvento.CUOTA_PRESTAMO,
                            titulo = "🏦 Cuota ${fila.numero} · ${item.prestamo.concepto}",
                            monto = fila.cuota,
                            detalle = "Autopréstamo"
                        )
                    )
                }
            }
        }
        deudas.filter { !it.pagada }.forEach { d ->
            val f = mes.atDay(d.deuda.diaPago.coerceIn(1, mes.lengthOfMonth()))
            if (!f.isBefore(Formato.aLocalDate(d.deuda.fechaRegistro))) {
                lista.add(
                    EventoCalendario(
                        fecha = f,
                        tipo = TipoEvento.PAGO_DEUDA,
                        titulo = "💳 Pago a ${d.deuda.acreedor}",
                        monto = d.deuda.pagoMinimo,
                        detalle = "Pago mínimo · saldo ${Formato.cop(d.saldo)}"
                    )
                )
            }
        }
        return lista.groupBy { it.fecha }
    }
}
