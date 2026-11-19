package com.mipropiobanco.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mipropiobanco.util.Formato

/** Una porción de la dona. */
data class Porcion(val etiqueta: String, val valor: Long, val color: Color)

/** Colores para categorías (se repiten si hay más). */
val PaletaGraficas = listOf(
    Color(0xFF00A86B), Color(0xFF3D5AFE), Color(0xFFFFA000), Color(0xFFE53935),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFF7CB342), Color(0xFFF4511E)
)

/** Dona animada con el total en el centro y la leyenda al lado. */
@Composable
fun GraficaDona(porciones: List<Porcion>, tituloCentro: String, tamano: Dp = 150.dp) {
    val total = porciones.sumOf { it.valor }.coerceAtLeast(1L).toFloat()
    val avance = remember(porciones) { Animatable(0f) }
    LaunchedEffect(porciones) { avance.animateTo(1f, tween(900)) }
    val fondo = MaterialTheme.colorScheme.surfaceVariant

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(tamano)) {
            Canvas(Modifier.size(tamano)) {
                val grosor = size.minDimension * 0.16f
                val estilo = Stroke(width = grosor, cap = StrokeCap.Butt)
                val inset = grosor / 2f
                val area = androidx.compose.ui.geometry.Size(size.width - grosor, size.height - grosor)
                val origen = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(fondo, 0f, 360f, false, origen, area, style = estilo)
                var inicio = -90f
                porciones.forEach { p ->
                    val barrido = 360f * (p.valor / total) * avance.value
                    drawArc(p.color, inicio, (barrido - 1.5f).coerceAtLeast(0f), false, origen, area, style = estilo)
                    inicio += barrido
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tituloCentro, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formato.cop(total.toLong()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            porciones.take(6).forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(p.color))
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(p.etiqueta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${Formato.cop(p.valor)} · ${(p.valor / total * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
