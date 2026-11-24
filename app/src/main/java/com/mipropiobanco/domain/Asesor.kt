package com.mipropiobanco.domain

import com.mipropiobanco.data.local.CategoriaEntity
import com.mipropiobanco.data.local.MovimientoEntity
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class NivelConsejo { PELIGRO, ALERTA, INFO, BUENO }

/** A dónde lleva el botón del consejo. */
enum class AccionConsejo(val texto: String) {
    PLAN("Ver plan"), DEUDAS("Ver deudas"), METAS("Ver metas"),
    GASTOS("Ver gastos"), BANCO("Ir a Mi banco"), SALARIO("Configurar")
}

data class Consejo(
    val icono: String,
    val titulo: String,
    val detalle: String,
    val nivel: NivelConsejo,
    val accion: AccionConsejo? = null
)

data class Diagnostico(
    /** Cuánto puedo gastar hoy sin descuadrar la quincena (negativo = ya me pasé). */
    val hoyPuedes: Long,
    val porDia: Long,
    val gastadoHoy: Long,
    val diasRestantes: Int,
    /** Gasto del día a día proyectado al cierre del mes. */
    val proyeccionMes: Long,
    val gastoMesAnterior: Long,
    /** 0 a 100: qué tan sanas están mis finanzas este mes. */
    val puntaje: Int,
    val consejos: List<Consejo>
) {
    val etiquetaPuntaje: String
        get() = when {
            puntaje >= 85 -> "Excelente"
            puntaje >= 65 -> "Bien"
            puntaje >= 45 -> "Con cuidado"
            else -> "En riesgo"
        }
}

/** Mira todos los números y dice, en palabras, qué hacer. */
object Asesor {

    /** Gastos que cuentan como "día a día" (no deudas, ahorro, Mi banco ni fijos). */
    fun esDiaADia(c: CategoriaEntity?, fijos: Set<Long>): Boolean =
        c != null && c.id !in fijos && !Cuadre.esAhorro(c) && !Cuadre.esPagoDeuda(c) && !Cuadre.esDeMiBanco(c)

    fun analizar(
        pm: ResumenPresupuestoMes,
        hoy: LocalDate,
        movimientos: List<MovimientoEntity>,
        categorias: List<CategoriaEntity>,
        deudas: List<DeudaConAbonos>,
        fijos: Set<Long>
    ): Diagnostico {
        val cats = categorias.associateBy { it.id }
        val mes = pm.mes
        val esMesActual = YearMonth.from(hoy) == mes
        val q = pm.de(PresupuestoQuincenal.quincenaDe(hoy))
        val dias = (ChronoUnit.DAYS.between(hoy, q.hasta).toInt() + 1).coerceAtLeast(1)

        val gastos = movimientos.filter { it.tipo == TipoMovimiento.GASTO.name }
        val gastadoHoy = gastos.filter { Formato.aLocalDate(it.fecha) == hoy && esDiaADia(cats[it.categoriaId], fijos) }.sumOf { it.monto }
        val paraVariables = q.libre + gastadoHoy
        val porDia = if (paraVariables > 0L) paraVariables / dias else 0L
        val hoyPuedes = porDia - gastadoHoy

        // Día a día del mes y del mes pasado al mismo día.
        fun diaADia(m: YearMonth, hastaDia: Int) = gastos.filter {
            val f = Formato.aLocalDate(it.fecha)
            YearMonth.from(f) == m && f.dayOfMonth <= hastaDia && esDiaADia(cats[it.categoriaId], fijos)
        }
        val diaHoy = if (esMesActual) hoy.dayOfMonth else mes.lengthOfMonth()
        val esteMes = diaADia(mes, diaHoy)
        val anterior = mes.minusMonths(1)
        val mesPasadoMismoDia = diaADia(anterior, diaHoy)
        val gastoMesAnterior = diaADia(anterior, 31).sumOf { it.monto }
        val proyeccion = if (diaHoy > 0) esteMes.sumOf { it.monto } * mes.lengthOfMonth() / diaHoy else 0L

        val consejos = mutableListOf<Consejo>()

        // 1. Pagos vencidos y próximos.
        val vencidos = (pm.q1.obligaciones + pm.q2.obligaciones).filter { it.estado == EstadoPago.VENCIDO }
        if (vencidos.isNotEmpty()) {
            consejos += Consejo(
                "⛔", "Tienes ${vencidos.size} pago(s) vencido(s)",
                vencidos.take(3).joinToString(" · ") { "${it.nombre} ${Formato.cop(it.monto)}" } +
                    ". Págalos primero: la mora sale más cara que cualquier otra cosa.",
                NivelConsejo.PELIGRO, AccionConsejo.PLAN
            )
        }
        val pronto = (pm.q1.obligaciones + pm.q2.obligaciones).filter {
            it.estado == EstadoPago.PENDIENTE && !it.fecha.isBefore(hoy) && it.fecha.isBefore(hoy.plusDays(4))
        }
        if (pronto.isNotEmpty()) {
            consejos += Consejo(
                "⏰", "Pagos en los próximos 3 días",
                pronto.joinToString(" · ") { "${it.nombre} el ${it.fecha.dayOfMonth} (${Formato.cop(it.monto)})" },
                NivelConsejo.ALERTA, AccionConsejo.PLAN
            )
        }

        // 2. ¿Alcanza la quincena?
        if (q.faltante > 0L) {
            consejos += Consejo(
                "🔴", "La ${q.quincena.etiqueta.lowercase()} no alcanza",
                "Te faltan ${Formato.cop(q.faltante)}. Frena gastos que no sean urgentes o mueve un pago a la siguiente quincena.",
                NivelConsejo.PELIGRO, AccionConsejo.PLAN
            )
        } else if (hoyPuedes < 0L) {
            consejos += Consejo(
                "🛑", "Hoy ya gastaste más de la cuenta",
                "Te pasaste ${Formato.cop(-hoyPuedes)} del límite diario. Si mañana no gastas, la quincena se recupera.",
                NivelConsejo.ALERTA, AccionConsejo.GASTOS
            )
        }

        // 3. Categoría que se disparó frente al mes pasado.
        val ahoraPorCat = esteMes.groupBy { it.categoriaId }.mapValues { e -> e.value.sumOf { it.monto } }
        val antesPorCat = mesPasadoMismoDia.groupBy { it.categoriaId }.mapValues { e -> e.value.sumOf { it.monto } }
        ahoraPorCat.entries
            .map { (id, v) -> Triple(id, v, antesPorCat[id] ?: 0L) }
            .filter { (_, v, a) -> a > 0L && v - a >= 20_000L && v * 10 >= a * 13 }
            .maxByOrNull { (_, v, a) -> v - a }
            ?.let { (id, v, a) ->
                val c = cats[id]
                consejos += Consejo(
                    "📈", "${c?.icono ?: ""} ${c?.nombre ?: "Una categoría"} subió ${((v - a) * 100 / a)}%",
                    "Llevas ${Formato.cop(v)} y el mes pasado a esta altura llevabas ${Formato.cop(a)}.",
                    NivelConsejo.ALERTA, AccionConsejo.GASTOS
                )
            }

        // 4. Categoría pasada de su tope.
        pm.gastosPorCategoria.filter { it.planeado > 0L && it.real > it.planeado }.maxByOrNull { it.real - it.planeado }?.let {
            consejos += Consejo(
                "💸", "Te pasaste en ${it.categoria.icono} ${it.categoria.nombre}",
                "Gastaste ${Formato.cop(it.real)} de un tope de ${Formato.cop(it.planeado)}.",
                NivelConsejo.ALERTA, AccionConsejo.PLAN
            )
        }

        // 5. Gastos hormiga.
        val hormiga = esteMes.filter { it.monto <= 15_000L }
        if (hormiga.size >= 8) {
            consejos += Consejo(
                "🐜", "Gastos hormiga: ${hormiga.size} este mes",
                "Compras pequeñas que suman ${Formato.cop(hormiga.sumOf { it.monto })}. Al año serían unos ${Formato.cop(hormiga.sumOf { it.monto } * 12)}.",
                NivelConsejo.INFO, AccionConsejo.GASTOS
            )
        }

        // 6. Proyección.
        if (gastoMesAnterior > 0L && proyeccion > gastoMesAnterior * 11 / 10 && esMesActual) {
            consejos += Consejo(
                "🔮", "A este ritmo cierras con ${Formato.cop(proyeccion)} en gastos",
                "Es ${Formato.cop(proyeccion - gastoMesAnterior)} más que el mes pasado (${Formato.cop(gastoMesAnterior)}).",
                NivelConsejo.INFO, AccionConsejo.GASTOS
            )
        }

        // 7. Deuda más cara.
        deudas.filter { !it.pagada && it.deuda.tasaEA > 0.0 }.maxByOrNull { it.deuda.tasaEA }?.let { d ->
            if (q.libre > d.deuda.pagoMinimo && q.faltante == 0L) {
                consejos += Consejo(
                    "🎯", "Abona extra a ${d.deuda.acreedor}",
                    "Es tu deuda más cara (${"%.1f".format(d.deuda.tasaEA)}% E.A.). Cada peso extra ahí te ahorra intereses.",
                    NivelConsejo.INFO, AccionConsejo.DEUDAS
                )
            }
        }

        // 8. Sin ingresos configurados.
        if (pm.ingresosPlaneados == 0L) {
            consejos += Consejo(
                "💼", "Dime cuánto ganas y qué días",
                "Con eso calculo cuánto puedes gastar cada día y te aviso antes de que falte plata.",
                NivelConsejo.INFO, AccionConsejo.SALARIO
            )
        }

        // 9. Lo bueno.
        if (pm.tasaAhorro >= 10) {
            consejos += Consejo(
                "🏆", "Estás ahorrando el ${pm.tasaAhorro}% de lo que ganas",
                "Vas por encima del 10% recomendado. ¡Sigue así!",
                NivelConsejo.BUENO, AccionConsejo.METAS
            )
        } else if (pm.ingresosReales > 0L && q.libre > 0L && q.faltante == 0L) {
            val sugerido = minOf(q.libre / 2, pm.ingresosReales / 10).coerceAtLeast(0L)
            if (sugerido >= 10_000L) {
                consejos += Consejo(
                    "🏦", "Págate primero: guarda ${Formato.cop(sugerido)}",
                    "Te sobra plata en esta quincena. Muévela a Mi banco o a una meta antes de que se vaya en gastos.",
                    NivelConsejo.BUENO, AccionConsejo.BANCO
                )
            }
        }
        if (vencidos.isEmpty() && q.faltante == 0L && consejos.none { it.nivel == NivelConsejo.ALERTA }) {
            consejos += Consejo(
                "✅", "Todo en orden",
                "No tienes pagos vencidos y la quincena alcanza. Vas bien.",
                NivelConsejo.BUENO
            )
        }

        val puntaje = (100 -
            consejos.count { it.nivel == NivelConsejo.PELIGRO } * 25 -
            consejos.count { it.nivel == NivelConsejo.ALERTA } * 10 -
            (if (pm.tasaAhorro < 5) 10 else 0) +
            (if (pm.tasaAhorro >= 10) 5 else 0)).coerceIn(0, 100)

        return Diagnostico(
            hoyPuedes = hoyPuedes,
            porDia = porDia,
            gastadoHoy = gastadoHoy,
            diasRestantes = dias,
            proyeccionMes = proyeccion,
            gastoMesAnterior = gastoMesAnterior,
            puntaje = puntaje,
            consejos = consejos.sortedBy { it.nivel.ordinal }
        )
    }
}
