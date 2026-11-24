package com.mipropiobanco.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class PresupuestoQuincenalTest {

    @Test
    fun quincenasDelMes() {
        assertEquals(Quincena.Q1, PresupuestoQuincenal.quincenaDe(LocalDate.of(2026, 11, 15)))
        assertEquals(Quincena.Q2, PresupuestoQuincenal.quincenaDe(LocalDate.of(2026, 11, 16)))
        val (desde, hasta) = PresupuestoQuincenal.rango(YearMonth.of(2027, 2), Quincena.Q2)
        assertEquals(16, desde.dayOfMonth)
        assertEquals(28, hasta.dayOfMonth)
    }

    @Test
    fun dia31EnNoviembreCaeEl30() {
        assertEquals(30, PresupuestoQuincenal.diaEnMes(YearMonth.of(2026, 11), 31).dayOfMonth)
    }

    @Test
    fun partirDaElPesoSobranteALaQ1() {
        assertEquals(500_001L to 500_000L, PresupuestoQuincenal.partir(1_000_001L))
    }

    @Test
    fun cuotaSugeridaSinRendimiento() {
        assertEquals(100_000L, PresupuestoQuincenal.cuotaSugeridaMeta(1_200_000L, 0L, 12))
        assertEquals(0L, PresupuestoQuincenal.cuotaSugeridaMeta(500_000L, 600_000L, 3))
    }

    @Test
    fun cuotaConRendimientoEsMenor() {
        val sin = PresupuestoQuincenal.cuotaSugeridaMeta(1_200_000L, 0L, 12)
        val con = PresupuestoQuincenal.cuotaSugeridaMeta(1_200_000L, 0L, 12, tasaEA = 10.0)
        assert(con < sin)
    }

    private fun quincena(viene: Long, entra: Long, porRecibir: Long, salio: Long, pendiente: Long): ResumenQuincena {
        val hoy = LocalDate.of(2026, 11, 20)
        val obligaciones = if (pendiente > 0L) listOf(
            Obligacion(TipoObligacion.DEUDA, 1L, "X", hoy, pendiente, EstadoPago.PENDIENTE)
        ) else emptyList()
        val esperados = if (porRecibir > 0L) listOf(
            IngresoEsperado(
                "Salario", hoy, porRecibir, false,
                Ocurrencia(
                    com.mipropiobanco.data.local.PlantillaEntity(
                        nombre = "Salario", tipo = "INGRESO", monto = porRecibir, categoriaId = 1L, medioId = 1L,
                        periodicidad = "MENSUAL", fechaInicio = 0L, automatica = false
                    ), 0, hoy
                )
            )
        ) else emptyList()
        return ResumenQuincena(
            Quincena.Q2, hoy, hoy, esperados, porRecibir, entra, viene, obligaciones, emptyList(), salio, 0L
        )
    }

    @Test
    fun semaforo() {
        assertEquals(Semaforo.VERDE, quincena(0L, 100_000L, 0L, 30_000L, 50_000L).semaforo)
        assertEquals(Semaforo.AMARILLO, quincena(60_000L, 50_000L, 0L, 30_000L, 50_000L).semaforo)
        val rojo = quincena(0L, 50_000L, 0L, 30_000L, 50_000L)
        assertEquals(Semaforo.ROJO, rojo.semaforo)
        assertEquals(30_000L, rojo.faltante)
    }
}
