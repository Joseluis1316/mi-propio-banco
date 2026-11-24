package com.mipropiobanco.domain

import com.mipropiobanco.data.local.CategoriaEntity
import com.mipropiobanco.data.local.MedioPagoEntity
import com.mipropiobanco.data.local.MovimientoEntity
import com.mipropiobanco.util.Formato
import java.time.YearMonth

/**
 * Cuadre del mes: de dónde salió cada peso y a dónde fue.
 * Empecé con X + gané + me prestaron + saqué de Mi banco − gasté − pagué deudas − ahorré = tengo hoy.
 * Si una cuenta queda en negativo, se gastó plata que no se sabe de dónde salió.
 */
data class Cuadre(
    val inicio: Long,
    val ganado: Long,
    val prestado: Long,
    val deMiBanco: Long,
    val gastado: Long,
    val pagoDeudas: Long,
    val ahorrado: Long,
    val final: Long,
    /** Cuentas en negativo: (cuenta, saldo). */
    val sinOrigen: List<Pair<MedioPagoEntity, Long>>
) {
    val totalSinOrigen: Long get() = sinOrigen.sumOf { -it.second }

    companion object {
        private fun n(t: String) = Interprete.normalizar(t)

        /** Qué tipo de flujo es cada categoría (por su nombre). */
        fun esPrestamoRecibido(c: CategoriaEntity?) = c != null && n(c.nombre).let { it.contains("prestamo") && it.contains("recib") }
        fun esDeMiBanco(c: CategoriaEntity?) = c != null && n(c.nombre).contains("prestamo de mi banco")
        fun esPagoDeuda(c: CategoriaEntity?) = c != null && n(c.nombre).contains("deuda")
        fun esAhorro(c: CategoriaEntity?) = c != null && n(c.nombre).let {
            it.contains("ahorro") || it.contains("cuota a mi banco")
        }

        fun de(
            movimientos: List<MovimientoEntity>,
            medios: List<MedioPagoEntity>,
            categorias: List<CategoriaEntity>,
            mes: YearMonth
        ): Cuadre {
            val cats = categorias.associateBy { it.id }
            val inicioMs = Formato.aMillis(mes.atDay(1))
            val finMs = Formato.aMillis(mes.plusMonths(1).atDay(1))
            val antes = movimientos.filter { it.fecha < inicioMs }
            val hastaFin = movimientos.filter { it.fecha < finMs }
            val delMes = movimientos.filter { it.fecha in inicioMs until finMs }

            var ganado = 0L; var prestado = 0L; var deMiBanco = 0L
            var gastado = 0L; var pagoDeudas = 0L; var ahorrado = 0L
            for (m in delMes) {
                val c = cats[m.categoriaId]
                if (m.tipo == TipoMovimiento.INGRESO.name) {
                    if (esPrestamoRecibido(c)) prestado += m.monto
                    else if (esDeMiBanco(c)) deMiBanco += m.monto
                    else ganado += m.monto
                } else if (m.tipo == TipoMovimiento.GASTO.name) {
                    if (esPagoDeuda(c)) pagoDeudas += m.monto
                    else if (esAhorro(c)) ahorrado += m.monto
                    else gastado += m.monto
                }
            }
            val saldosFin = calcularSaldos(medios, hastaFin)
            val negativos = medios.mapNotNull { md -> (saldosFin[md.id] ?: 0L).takeIf { it < 0L }?.let { md to it } }
            return Cuadre(
                inicio = calcularSaldos(medios, antes).values.sum(),
                ganado = ganado, prestado = prestado, deMiBanco = deMiBanco,
                gastado = gastado, pagoDeudas = pagoDeudas, ahorrado = ahorrado,
                final = saldosFin.values.sum(),
                sinOrigen = negativos
            )
        }
    }
}
