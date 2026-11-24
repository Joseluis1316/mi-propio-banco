package com.mipropiobanco.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mipropiobanco.domain.AccionConsejo
import com.mipropiobanco.domain.Consejo
import com.mipropiobanco.domain.Diagnostico
import com.mipropiobanco.domain.NivelConsejo
import com.mipropiobanco.util.Formato

/** Número que sube animado hasta su valor (como en las apps de banco). */
@Composable
fun MontoAnimado(valor: Long, estilo: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(valor) {
        anim.animateTo(valor.toFloat(), tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    val mostrado = if (anim.isRunning) anim.value.toLong() else valor
    Text(Formato.cop(mostrado), style = estilo, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
}

/** Anillo con el puntaje de salud financiera (0 a 100). */
@Composable
fun AnilloPuntaje(puntaje: Int, color: Color, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(puntaje) { anim.animateTo(puntaje / 100f, tween(1100, easing = FastOutSlowInEasing)) }
    Box(modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(76.dp)) {
            val grosor = 9.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.18f), 135f, 270f, false, style = Stroke(grosor, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * anim.value, false, style = Stroke(grosor, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$puntaje", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("salud", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun colorPuntaje(p: Int): Color = when {
    p >= 85 -> Color(0xFFB5E86A)
    p >= 65 -> Color(0xFFD6EE9E)
    p >= 45 -> Color(0xFFF2B84B)
    else -> Color(0xFFFF7A7A)
}

/** La tarjeta estrella de Inicio: cuánto puedo gastar hoy. */
@Composable
fun TarjetaHoy(d: Diagnostico, alPulsar: () -> Unit) {
    val fondo = com.mipropiobanco.ui.theme.FondoHero
    Surface(onClick = alPulsar, shape = RoundedCornerShape(28.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.background(fondo).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (d.hoyPuedes >= 0L) "Hoy puedes gastar" else "Hoy te pasaste",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelLarge
                        )
                        MontoAnimado(
                            if (d.hoyPuedes >= 0L) d.hoyPuedes else -d.hoyPuedes,
                            MaterialTheme.typography.displaySmall,
                            if (d.hoyPuedes >= 0L) Color(0xFFB5E86A) else Color(0xFFFF7A7A)
                        )
                        Text(
                            "Límite diario ${Formato.cop(d.porDia)} · ${d.diasRestantes} día(s) para cerrar la quincena",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    AnilloPuntaje(d.puntaje, colorPuntaje(d.puntaje))
                }
                Spacer(Modifier.height(8.dp))
                val usado = if (d.porDia > 0L) (d.gastadoHoy.toFloat() / d.porDia).coerceIn(0f, 1f) else if (d.gastadoHoy > 0L) 1f else 0f
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))) {
                    Box(
                        Modifier.fillMaxWidth(usado).height(8.dp).clip(RoundedCornerShape(50))
                            .background(if (usado >= 1f) Color(0xFFFF7A7A) else Color(0xFFB5E86A))
                    )
                }
                Row {
                    Text("Gastado hoy ${Formato.cop(d.gastadoHoy)}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text("Salud: ${d.etiquetaPuntaje}", color = colorPuntaje(d.puntaje), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                if (d.gastoMesAnterior > 0L || d.proyeccionMes > 0L) {
                    Text(
                        "🔮 Cierre del mes a este ritmo: ${Formato.cop(d.proyeccionMes)} en gastos del día a día" +
                            if (d.gastoMesAnterior > 0L) " (mes pasado ${Formato.cop(d.gastoMesAnterior)})" else "",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun coloresNivel(n: NivelConsejo): Pair<Color, Color> = when (n) {
    NivelConsejo.PELIGRO -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    NivelConsejo.ALERTA -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    NivelConsejo.INFO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    NivelConsejo.BUENO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
}

/** Carrusel de consejos del asesor. */
@Composable
fun CarruselConsejos(consejos: List<Consejo>, alAccion: (AccionConsejo) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(consejos, key = { it.titulo }) { c ->
            val (fondo, texto) = coloresNivel(c.nivel)
            Surface(shape = RoundedCornerShape(20.dp), color = fondo, modifier = Modifier.width(270.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.icono, style = MaterialTheme.typography.headlineSmall)
                    Text(c.titulo, color = texto, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(c.detalle, color = texto.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    c.accion?.let { a ->
                        TextButton(onClick = { alAccion(a) }) {
                            Text("${a.texto} →", color = texto, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Ícono de categoría dentro de un círculo (estilo apps de banca). */
@Composable
fun IconoCirculo(icono: String, tamano: androidx.compose.ui.unit.Dp = 42.dp) {
    Box(
        Modifier.size(tamano).clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Text(icono, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Curva del gasto acumulado: este mes (línea de acento) contra el mes pasado (gris).
 * Si la línea va por debajo de la gris, vas gastando menos que el mes pasado.
 */
@Composable
fun CurvaGastoMes(esteMes: List<Long>, mesPasado: List<Long>, diasMes: Int) {
    val acento = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.outline
    val alerta = MaterialTheme.colorScheme.error
    val maximo = maxOf(esteMes.maxOrNull() ?: 0L, mesPasado.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val anim = remember(esteMes) { Animatable(0f) }
    LaunchedEffect(esteMes) { anim.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    val hoyVal = esteMes.lastOrNull() ?: 0L
    val antesVal = mesPasado.getOrNull(esteMes.size - 1) ?: 0L
    val diferencia = hoyVal - antesVal
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Gasto del mes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Formato.cop(hoyVal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            if (antesVal > 0L) {
                Text(
                    if (diferencia <= 0L) "${Formato.cop(-diferencia)} menos que el mes pasado a esta fecha"
                    else "${Formato.cop(diferencia)} más que el mes pasado a esta fecha",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (diferencia <= 0L) acento else alerta
                )
            }
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp)) {
                val paso = size.width / (diasMes - 1).coerceAtLeast(1)
                fun y(v: Long) = size.height - size.height * (v.toFloat() / maximo)
                fun trazo(datos: List<Long>, color: Color, ancho: Float, hasta: Float) {
                    val n = (datos.size * hasta).toInt().coerceAtMost(datos.size)
                    if (n < 2) return
                    val camino = androidx.compose.ui.graphics.Path()
                    camino.moveTo(0f, y(datos[0]))
                    for (i in 1 until n) camino.lineTo(i * paso, y(datos[i]))
                    drawPath(camino, color, style = Stroke(ancho, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                trazo(mesPasado, gris.copy(alpha = 0.6f), 2.dp.toPx(), 1f)
                trazo(esteMes, acento, 3.dp.toPx(), anim.value)
                if (esteMes.isNotEmpty() && anim.value >= 1f) {
                    drawCircle(acento, 5.dp.toPx(), androidx.compose.ui.geometry.Offset((esteMes.size - 1) * paso, y(hoyVal)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("━ Este mes", color = acento, style = MaterialTheme.typography.labelSmall)
                Text("━ Mes pasado", color = gris, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Suscripciones y cobros que se repiten cada mes. */
@Composable
fun TarjetaRecurrentes(cobros: List<com.mipropiobanco.domain.CobroRecurrente>) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cobros que se repiten", style = MaterialTheme.typography.titleMedium)
                    Text("Detectados solos en tus movimientos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Formato.cop(cobros.sumOf { it.montoMensual }) + "/mes", fontWeight = FontWeight.SemiBold)
            }
            cobros.take(6).forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoCirculo("🔁", 36.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(c.nombre, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${c.meses} meses seguidos · último ${Formato.fecha(c.ultimo)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Formato.cop(c.montoMensual), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "Al año son ${Formato.cop(cobros.sumOf { it.montoMensual } * 12)}. ¿Usas todos?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
