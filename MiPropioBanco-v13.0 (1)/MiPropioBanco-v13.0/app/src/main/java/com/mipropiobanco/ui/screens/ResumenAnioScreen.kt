package com.mipropiobanco.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.domain.Cuadre
import com.mipropiobanco.domain.TipoMovimiento
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.GraficaDona
import com.mipropiobanco.ui.components.PaletaGraficas
import com.mipropiobanco.ui.components.Porcion
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.YearMonth

/** Mi año en números: ingresos vs gastos mes a mes, ahorro y en qué se me fue la plata. */
@Composable
fun ResumenAnioScreen(vm: BancoViewModel, alVolver: () -> Unit) {
    var anio by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val cats = categorias.associateBy { it.id }

    val delAnio = movimientos.filter { Formato.aLocalDate(it.fecha).year == anio }
    val porMes = (1..12).map { m ->
        val ym = YearMonth.of(anio, m)
        val movs = delAnio.filter { YearMonth.from(Formato.aLocalDate(it.fecha)) == ym }
        Triple(
            ym,
            movs.filter { it.tipo == TipoMovimiento.INGRESO.name }.sumOf { it.monto },
            movs.filter { it.tipo == TipoMovimiento.GASTO.name }.sumOf { it.monto }
        )
    }
    val ingresos = porMes.sumOf { it.second }
    val gastos = porMes.sumOf { it.third }
    val ahorro = delAnio.filter { it.tipo == TipoMovimiento.GASTO.name && Cuadre.esAhorro(cats[it.categoriaId]) }.sumOf { it.monto }
    val conDatos = porMes.filter { it.second > 0L || it.third > 0L }
    val mejor = conDatos.maxByOrNull { it.second - it.third }
    val peor = conDatos.minByOrNull { it.second - it.third }
    val topCats = delAnio.filter { it.tipo == TipoMovimiento.GASTO.name }
        .groupBy { it.categoriaId }.mapValues { e -> e.value.sumOf { it.monto } }
        .entries.sortedByDescending { it.value }.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                Text("Mi año", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { anio-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Año anterior") }
                Text("$anio", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { anio++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Año siguiente") }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilaAnio("💰 Ingresos del año", ingresos, MaterialTheme.colorScheme.primary)
                    FilaAnio("💸 Gastos del año", gastos, MaterialTheme.colorScheme.error)
                    FilaAnio("📊 Balance", ingresos - gastos, if (ingresos >= gastos) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    FilaAnio("🏦 Ahorrado (metas y Mi banco)", ahorro, MaterialTheme.colorScheme.tertiary)
                    if (ingresos > 0L) Text("Tasa de ahorro del año: ${ahorro * 100 / ingresos}%", style = MaterialTheme.typography.bodySmall)
                    if (conDatos.isNotEmpty()) {
                        Text(
                            "Promedio mensual: entra ${Formato.cop(ingresos / conDatos.size)} y sale ${Formato.cop(gastos / conDatos.size)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mes a mes", style = MaterialTheme.typography.titleMedium)
                    BarrasAnio(porMes.map { it.second to it.third })
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Leyenda("Ingresos", MaterialTheme.colorScheme.primary)
                        Leyenda("Gastos", MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (mejor != null && peor != null && conDatos.size > 1) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🏆 Mejor mes: ${Formato.mesAnio(mejor.first)} (${Formato.cop(mejor.second - mejor.third)})", style = MaterialTheme.typography.bodyMedium)
                    Text("⚠️ Mes más duro: ${Formato.mesAnio(peor.first)} (${Formato.cop(peor.second - peor.third)})", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (topCats.isNotEmpty()) {
            item { Text("¿En qué se me fue la plata?", style = MaterialTheme.typography.titleMedium) }
            item {
                GraficaDona(
                    topCats.mapIndexed { i, e ->
                        val c = cats[e.key]
                        Porcion("${c?.icono ?: ""} ${c?.nombre ?: "Otros"}", e.value, PaletaGraficas[i % PaletaGraficas.size])
                    },
                    Formato.cop(gastos)
                )
            }
        }
    }
}

@Composable
private fun FilaAnio(texto: String, valor: Long, color: androidx.compose.ui.graphics.Color) {
    Row {
        Text(texto, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(Formato.cop(valor), color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Leyenda(texto: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .then(Modifier.background(color))
        )
        Text(texto, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BarrasAnio(datos: List<Pair<Long, Long>>) {
    val colorIng = MaterialTheme.colorScheme.primary
    val colorGas = MaterialTheme.colorScheme.error
    val colorTexto = MaterialTheme.colorScheme.onSurfaceVariant
    val maximo = datos.maxOfOrNull { maxOf(it.first, it.second) }?.coerceAtLeast(1L) ?: 1L
    val avance = remember(datos) { Animatable(0f) }
    LaunchedEffect(datos) { avance.animateTo(1f, tween(900)) }
    val letras = listOf("E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    Column {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val ancho = size.width / 12f
            val barra = ancho * 0.32f
            datos.forEachIndexed { i, (ing, gas) ->
                val x = i * ancho + ancho * 0.15f
                val hIng = size.height * (ing.toFloat() / maximo) * avance.value
                val hGas = size.height * (gas.toFloat() / maximo) * avance.value
                drawRoundRect(colorIng, Offset(x, size.height - hIng), Size(barra, hIng), CornerRadius(6f, 6f))
                drawRoundRect(colorGas, Offset(x + barra + 2f, size.height - hGas), Size(barra, hGas), CornerRadius(6f, 6f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            letras.forEach { l ->
                Text(l, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colorTexto)
            }
        }
    }
}
