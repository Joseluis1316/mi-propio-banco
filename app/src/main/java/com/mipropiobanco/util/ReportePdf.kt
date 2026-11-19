package com.mipropiobanco.util

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.mipropiobanco.data.local.CategoriaEntity
import com.mipropiobanco.data.local.MedioPagoEntity
import com.mipropiobanco.data.local.MovimientoEntity
import com.mipropiobanco.domain.Meta
import com.mipropiobanco.domain.ResumenMes
import com.mipropiobanco.domain.TipoMovimiento
import com.mipropiobanco.domain.calcularSaldos
import java.io.OutputStream
import java.time.YearMonth

/** Reporte mensual en PDF (tamaño carta): resumen, categorías, comparación y movimientos. */
object ReportePdf {

    private const val ANCHO = 612
    private const val ALTO = 792
    private const val MARGEN = 40f

    fun escribir(
        salida: OutputStream,
        mes: YearMonth,
        movimientos: List<MovimientoEntity>,
        categorias: List<CategoriaEntity>,
        medios: List<MedioPagoEntity>,
        debo: Long,
        miBanco: Long,
        metas: List<Meta>
    ) {
        val doc = PdfDocument()
        val cats = categorias.associateBy { it.id }
        val actual = ResumenMes.de(movimientos, mes)
        val anterior = ResumenMes.de(movimientos, mes.minusMonths(1))
        val enCuentas = calcularSaldos(medios, movimientos).values.sum()
        val verde = Color.rgb(0, 135, 90)
        val rojo = Color.rgb(211, 47, 47)
        val gris = Color.rgb(90, 99, 115)

        val titulo = Paint().apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(21, 26, 34) }
        val subtitulo = Paint().apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(21, 26, 34) }
        val texto = Paint().apply { textSize = 10.5f; color = Color.rgb(40, 45, 55) }
        val suave = Paint().apply { textSize = 9.5f; color = gris }
        val derecha = Paint(texto).apply { textAlign = Paint.Align.RIGHT }
        val barra = Paint().apply { style = Paint.Style.FILL }

        var numero = 1
        var pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create())
        var lienzo = pagina.canvas
        var y = MARGEN

        fun nuevaPaginaSiHaceFalta(alto: Float) {
            if (y + alto > ALTO - MARGEN) {
                doc.finishPage(pagina)
                numero++
                pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create())
                lienzo = pagina.canvas
                y = MARGEN
            }
        }

        fun fila(izq: String, der: String, colorDer: Int = Color.rgb(40, 45, 55)) {
            nuevaPaginaSiHaceFalta(16f)
            lienzo.drawText(izq, MARGEN, y, texto)
            derecha.color = colorDer
            lienzo.drawText(der, ANCHO - MARGEN, y, derecha)
            y += 16f
        }

        // Encabezado con franja de color
        barra.color = verde
        lienzo.drawRect(0f, 0f, ANCHO.toFloat(), 8f, barra)
        y += 14f
        lienzo.drawText("Mi Propio Banco", MARGEN, y, titulo)
        y += 18f
        lienzo.drawText("Reporte de ${Formato.mesAnio(mes)} · generado el ${Formato.hoyIso()}", MARGEN, y, suave)
        y += 28f

        // Resumen del mes
        lienzo.drawText("Resumen del mes", MARGEN, y, subtitulo); y += 20f
        fila("Ingresos", Formato.copReal(actual.ingresos), verde)
        fila("Gastos", Formato.copReal(actual.gastos), rojo)
        fila("Balance (ingresos − gastos)", Formato.copReal(actual.balance), if (actual.balance >= 0) verde else rojo)
        val difGasto = actual.gastos - anterior.gastos
        fila(
            "Gastos vs. ${Formato.mesAnio(mes.minusMonths(1))}",
            (if (difGasto > 0) "+" else "") + Formato.copReal(difGasto),
            if (difGasto > 0) rojo else verde
        )
        y += 10f

        // Patrimonio
        lienzo.drawText("Lo que tengo hoy", MARGEN, y, subtitulo); y += 20f
        fila("En cuentas y efectivo", Formato.copReal(enCuentas))
        fila("Mi banco (bóveda + prestado)", Formato.copReal(miBanco))
        fila("Lo que debo", "−" + Formato.copReal(debo), rojo)
        fila("Patrimonio neto", Formato.copReal(enCuentas + miBanco - debo), verde)
        y += 10f

        // Gasto por categoría con barras
        if (actual.gastoPorCategoria.isNotEmpty()) {
            nuevaPaginaSiHaceFalta(40f)
            lienzo.drawText("¿En qué se fue la plata?", MARGEN, y, subtitulo); y += 18f
            val orden = actual.gastoPorCategoria.entries.sortedByDescending { it.value }
            val maximo = orden.first().value.coerceAtLeast(1L).toFloat()
            orden.forEach { (id, monto) ->
                nuevaPaginaSiHaceFalta(30f)
                val c = cats[id]
                val antes = anterior.gastoPorCategoria[id] ?: 0L
                lienzo.drawText("${c?.nombre ?: "Otros"}", MARGEN, y, texto)
                derecha.color = Color.rgb(40, 45, 55)
                lienzo.drawText(Formato.copReal(monto), ANCHO - MARGEN, y, derecha)
                y += 5f
                barra.color = Color.rgb(227, 232, 240)
                lienzo.drawRoundRect(RectF(MARGEN, y, ANCHO - MARGEN, y + 6f), 3f, 3f, barra)
                barra.color = verde
                lienzo.drawRoundRect(RectF(MARGEN, y, MARGEN + (ANCHO - 2 * MARGEN) * (monto / maximo), y + 6f), 3f, 3f, barra)
                y += 16f
                if (c != null && c.presupuestoMensual > 0L) {
                    lienzo.drawText(
                        "Presupuesto ${Formato.copReal(c.presupuestoMensual)} · mes anterior ${Formato.copReal(antes)}",
                        MARGEN, y, suave
                    )
                    y += 14f
                }
            }
            y += 6f
        }

        // Metas
        if (metas.isNotEmpty()) {
            nuevaPaginaSiHaceFalta(40f)
            lienzo.drawText("Metas de ahorro", MARGEN, y, subtitulo); y += 18f
            metas.forEach { m ->
                fila("${m.nombre} (${(m.fraccion * 100).toInt()}%)", "${Formato.copReal(m.ahorrado)} de ${Formato.copReal(m.objetivo)}")
            }
            y += 6f
        }

        // Movimientos
        val delMes = movimientos
            .filter { YearMonth.from(Formato.aLocalDate(it.fecha)) == mes }
            .sortedBy { it.fecha }
        if (delMes.isNotEmpty()) {
            nuevaPaginaSiHaceFalta(40f)
            lienzo.drawText("Movimientos (${delMes.size})", MARGEN, y, subtitulo); y += 18f
            delMes.forEach { m ->
                val signo = when (m.tipo) {
                    TipoMovimiento.INGRESO.name -> "+"
                    TipoMovimiento.GASTO.name -> "−"
                    else -> "↔"
                }
                val desc = (cats[m.categoriaId]?.nombre ?: "") + if (m.nota.isNotBlank()) " · ${m.nota}" else ""
                fila(
                    "${Formato.fecha(m.fecha)}  ${desc.take(60)}",
                    signo + Formato.copReal(m.monto),
                    if (m.tipo == TipoMovimiento.INGRESO.name) verde else if (m.tipo == TipoMovimiento.GASTO.name) rojo else gris
                )
            }
        }

        doc.finishPage(pagina)
        doc.writeTo(salida)
        doc.close()
    }
}
