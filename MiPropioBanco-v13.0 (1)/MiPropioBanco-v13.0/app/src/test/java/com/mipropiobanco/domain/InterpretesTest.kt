package com.mipropiobanco.domain

import com.mipropiobanco.data.local.MedioPagoEntity
import com.mipropiobanco.data.local.MovimientoEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterpretesTest {

    private val hoy = LocalDate.of(2026, 11, 18)

    @Test
    fun correo_pagaste() {
        val m = InterpreteCorreo.interpretar(
            "1", "Bancolombia: Pagaste \$53,938.00 a PEXTO CAPITAL desde tu producto *3888 el 14/11/2026", hoy
        )
        assertNotNull(m)
        assertEquals(53_938L, m!!.monto)
        assertEquals(TipoMovimiento.GASTO, m.tipo)
    }

    @Test
    fun correo_transferencia_boton_bancolombia() {
        val m = InterpreteCorreo.interpretar(
            "2", "Bancolombia: Transferiste \$561,245.00 por Boton Bancolombia a SISTECREDITO SAS desde producto *3888. 14/11/2026", hoy
        )
        assertNotNull(m)
        assertEquals(561_245L, m!!.monto)
        assertTrue(m.contraparte.contains("SISTECREDITO"))
    }

    @Test
    fun correo_recibiste_pago_por() {
        val m = InterpreteCorreo.interpretar(
            "3", "Recibiste un pago por \$410,150.00 de Banco Finandina a tu cuenta AHORROS, el 15:57 a las 02/10/2026.", hoy
        )
        assertNotNull(m)
        assertEquals(410_150L, m!!.monto)
        assertEquals(TipoMovimiento.INGRESO, m.tipo)
    }

    @Test
    fun correo_retiro_es_gasto() {
        val m = InterpreteCorreo.interpretar("4", "Bancolombia: Retiraste \$240,000.00 en CORRESPONSAL el 18/11/2026", hoy)
        assertNotNull(m)
        assertEquals(TipoMovimiento.GASTO, m!!.tipo)
        assertTrue(m.esRetiro)
    }

    @Test
    fun extracto_separa_movimientos() {
        val texto = """
            COP $ 9.000,00
            18 NOV 2026
            TRANSFERENCIA DESDE NEQUI
            COP $ 6.300,00
            18 NOV 2026
            TRANSFERENCIA DESDE NEQUI
            COP $ 50.000,00
            14 NOV 2026
            TRANSFERENCIAS A NEQUI
            COP -$ 150.000,00
        """.trimIndent()
        val filas = InterpreteExtracto.interpretar(texto)
        assertEquals(3, filas.size)
        assertEquals(6_300L, filas[0].monto)
        assertEquals(50_000L, filas[1].monto)
        assertTrue(filas[2].salida)
        assertEquals(150_000L, filas[2].monto)
    }

    @Test
    fun saldos_con_traslado() {
        val banco = MedioPagoEntity(id = 1, nombre = "Bancolombia", icono = "🏦", saldoInicial = 100_000)
        val nequi = MedioPagoEntity(id = 2, nombre = "Nequi", icono = "📲")
        val movs = listOf(
            MovimientoEntity(tipo = "GASTO", monto = 20_000, categoriaId = 1, medioId = 1, fecha = 0),
            MovimientoEntity(tipo = "TRASLADO", monto = 30_000, categoriaId = 0, medioId = 1, fecha = 0, medioDestinoId = 2)
        )
        val s = calcularSaldos(listOf(banco, nequi), movs)
        assertEquals(50_000L, s[1])
        assertEquals(30_000L, s[2])
    }
}
