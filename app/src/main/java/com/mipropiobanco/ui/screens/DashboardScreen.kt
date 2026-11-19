@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mipropiobanco.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.domain.ResumenBanco
import com.mipropiobanco.domain.TipoMovimiento
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.Cronometro
import com.mipropiobanco.ui.components.EstadoVacio
import com.mipropiobanco.ui.components.TarjetaPrestamo
import com.mipropiobanco.ui.components.rememberAhora
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Composable
fun DashboardScreen(
    vm: BancoViewModel,
    alNuevoMovimiento: (String) -> Unit,
    alNuevoAporte: () -> Unit,
    alNuevoPrestamo: () -> Unit,
    alRegistrarPago: () -> Unit,
    alAbrirPrestamo: (Long) -> Unit,
    alVerDeudas: () -> Unit,
    alVerMovimientos: () -> Unit,
    alAjustes: () -> Unit,
    alBandeja: () -> Unit = {},
    alMetas: () -> Unit = {}
) {
    val resumen by vm.resumen.collectAsStateWithLifecycle()
    val prestamos by vm.prestamos.collectAsStateWithLifecycle()
    val activos = prestamos.filter { !it.prestamo.pagado }
    val plan by vm.planDeudas.collectAsStateWithLifecycle()
    val ahora = rememberAhora()
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val plantillas by vm.plantillas.collectAsStateWithLifecycle()
    val bandeja by vm.bandeja.collectAsStateWithLifecycle()
    val medios by vm.medios.collectAsStateWithLifecycle()
    val deudas by vm.deudas.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val metas by vm.metas.collectAsStateWithLifecycle()

    val saldos = com.mipropiobanco.domain.calcularSaldos(medios, movimientos)
    val enCuentas = saldos.values.sum()
    val debo = deudas.filter { !it.pagada }.sumOf { it.saldo }
    val patrimonio = enCuentas + resumen.capitalTotal - debo
    val mes = com.mipropiobanco.domain.ResumenMes.de(movimientos, YearMonth.now())
    val porRegistrar = plantillas.sumOf { com.mipropiobanco.domain.Recurrencia.pendientes(it, LocalDate.now()).size }
    val catMap = categorias.associateBy { it.id }
    val excedidas = categorias.filter { c ->
        c.tipo == TipoMovimiento.GASTO.name && c.presupuestoMensual > 0L &&
            (mes.gastoPorCategoria[c.id] ?: 0L) > c.presupuestoMensual
    }
    val topGastos = mes.gastoPorCategoria.entries.sortedByDescending { it.value }.take(5)
    val porClasificar = categorias.firstOrNull {
        com.mipropiobanco.domain.Interprete.normalizar(it.nombre).contains("por clasificar")
    }?.let { c -> movimientos.count { it.categoriaId == c.id } } ?: 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- Encabezado ----
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(saludo(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Mi Propio Banco", style = MaterialTheme.typography.headlineSmall)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    IconButton(onClick = alAjustes) { Icon(Icons.Filled.Settings, contentDescription = "Ajustes") }
                }
            }
        }

        // ---- Tarjeta principal ----
        item {
            val ocultar by vm.ocultarSaldos.collectAsStateWithLifecycle()
            TarjetaPrincipal(enCuentas, resumen.capitalTotal, debo, patrimonio, alVerMovimientos, ocultar) {
                vm.cambiarOcultarSaldos(!ocultar)
            }
        }

        // ---- Acciones rápidas ----
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Accion("Gasto", Icons.Filled.RemoveCircle, MaterialTheme.colorScheme.error) { alNuevoMovimiento("GASTO") }
                Accion("Ingreso", Icons.Filled.AddCircle, MaterialTheme.colorScheme.primary) { alNuevoMovimiento("INGRESO") }
                Accion("Hablar", Icons.Filled.Mic, MaterialTheme.colorScheme.secondary) { alNuevoMovimiento("VOZ") }
                Accion("Recibo", Icons.Filled.CameraAlt, MaterialTheme.colorScheme.tertiary) { alNuevoMovimiento("FOTO") }
            }
        }

        // ---- Avisos ----
        val avisos = buildList {
            if (bandeja.isNotEmpty()) add(Aviso("📥", "${bandeja.size} movimientos de tus correos por revisar", alBandeja))
            if (porClasificar > 0) add(Aviso("🏷️", "$porClasificar gastos por clasificar", alVerMovimientos))
            if (porRegistrar > 0) add(Aviso("📌", "$porRegistrar gastos fijos por registrar", alVerMovimientos))
            excedidas.forEach { c ->
                val gastado = mes.gastoPorCategoria[c.id] ?: 0L
                add(Aviso("⚠️", "Te pasaste en ${c.icono} ${c.nombre}: ${Formato.cop(gastado)} de ${Formato.cop(c.presupuestoMensual)}", alVerMovimientos))
            }
        }
        if (avisos.isNotEmpty()) {
            items(avisos, key = { it.texto }) { a -> FilaAviso(a) }
        }

        // ---- Este mes ----
        item {
            Card(
                onClick = alVerMovimientos,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Este mes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            Formato.cop(mes.balance),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (mes.balance >= 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniDato("Entró", mes.ingresos, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        MiniDato("Salió", mes.gastos, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    }
                    val fraccion = if (mes.ingresos > 0L) (mes.gastos.toFloat() / mes.ingresos).coerceIn(0f, 1f) else if (mes.gastos > 0L) 1f else 0f
                    LinearProgressIndicator(
                        progress = { fraccion },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = if (fraccion > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {}
                    )
                    Text(
                        if (mes.ingresos > 0L) "Has gastado el ${(fraccion * 100).toInt()}% de lo que entró este mes"
                        else "Aún no registras ingresos este mes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---- En qué se va la plata ----
        if (topGastos.isNotEmpty()) {
            item {
                Card(
                    onClick = alVerMovimientos,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("¿En qué se va la plata?", style = MaterialTheme.typography.titleMedium)
                        val maximo = topGastos.first().value.coerceAtLeast(1L)
                        val paleta = listOf(
                            MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error,
                            MaterialTheme.colorScheme.outline
                        )
                        topGastos.forEachIndexed { i, (catId, monto) ->
                            val c = catMap[catId]
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text(
                                        "${c?.icono ?: "•"} ${c?.nombre ?: "Otros"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(Formato.cop(monto), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Box(
                                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth(monto.toFloat() / maximo).height(6.dp)
                                            .clip(RoundedCornerShape(50)).background(paleta[i % paleta.size])
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Metas ----
        item {
            Card(
                onClick = alMetas,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎯 Metas de ahorro", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(if (metas.isEmpty()) "Crear ›" else "Ver ›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    if (metas.isEmpty()) {
                        Text(
                            "Moto, viaje, fondo de emergencia… ponle número a tus sueños.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    metas.take(3).forEach { m ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                Text("${m.icono} ${m.nombre}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("${(m.fraccion * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = { m.fraccion },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                drawStopIndicator = {}
                            )
                        }
                    }
                }
            }
        }

        // ---- Mi banco ----
        item { TarjetaMiBanco(resumen, alNuevoAporte, alNuevoPrestamo) }

        if (activos.isNotEmpty()) {
            item { Titulo("Autopréstamos activos") }
            items(activos, key = { it.prestamo.id }) { item ->
                TarjetaPrestamo(item = item, ahora = ahora, alAbrir = { alAbrirPrestamo(item.prestamo.id) })
            }
        }

        // ---- Deudas ----
        val objetivo = plan.orden.firstOrNull()
        if (objetivo != null) {
            item { Titulo("Deudas · bola de nieve") }
            item {
                Card(
                    onClick = alVerDeudas,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Debes en total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Formato.cop(debo), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                            }
                            Text("${plan.orden.size} deudas", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(
                            "🎯 Ataca primero: ${objetivo.deuda.acreedor} (${Formato.cop(objetivo.saldo)})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        plan.mesesTotales?.let {
                            Text("🏁 Libre de deudas en $it meses", style = MaterialTheme.typography.bodySmall)
                        }
                        val proxima = plan.orden.mapNotNull { d -> d.estado(ahora)?.let { d to it } }
                            .minByOrNull { it.second.restanteMs }
                        if (proxima != null) {
                            val (d, e) = proxima
                            Cronometro(
                                titulo = if (e.atrasado) "${d.deuda.acreedor}: atrasado ${e.diasAtraso} días"
                                else "Próximo pago: ${d.deuda.acreedor} · ${Formato.fecha(e.vence)}",
                                detalle = if (e.atrasado) "Págalo cuanto antes" else "Faltan ${Formato.duracion(e.restanteMs)}",
                                atrasado = e.atrasado
                            )
                        }
                    }
                }
            }
        }

        if (movimientos.isEmpty() && resumen.totalAportes == 0L) {
            item {
                EstadoVacio(
                    titulo = "Empieza aquí",
                    detalle = "Pon cuánto tienes hoy en Gastos › Mis cuentas hoy › ✏️ Ajustar, y registra tu primer gasto con la voz o una foto."
                )
            }
        }
    }
}

private data class Aviso(val icono: String, val texto: String, val accion: () -> Unit)

private fun saludo(): String {
    val h = LocalTime.now().hour
    return when {
        h < 12 -> "Buenos días 👋"
        h < 19 -> "Buenas tardes 👋"
        else -> "Buenas noches 👋"
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun TarjetaPrincipal(
    enCuentas: Long, miBanco: Long, debo: Long, patrimonio: Long, alAbrir: () -> Unit,
    ocultar: Boolean, alOjo: () -> Unit
) {
    val degradado = Brush.linearGradient(listOf(Color(0xFF0B8F5E), Color(0xFF0A5C8A), Color(0xFF1E2A78)))
    Card(onClick = alAbrir, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(
            Modifier.fillMaxWidth().background(degradado).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lo que tengo hoy",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = alOjo) {
                    Icon(
                        if (ocultar) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Mostrar u ocultar montos",
                        tint = Color.White
                    )
                }
            }
            Text(
                Formato.cop(enCuentas),
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1
            )
            Text("En cuentas y efectivo", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("🏦 Mi banco", Formato.cop(miBanco), Modifier.weight(1f))
                Chip("💳 Debo", Formato.cop(debo), Modifier.weight(1f))
                Chip("📊 Patrimonio", Formato.cop(patrimonio), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Chip(titulo: String, valor: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)).padding(10.dp)
    ) {
        Text(titulo, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(
            valor, color = Color.White, style = MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Accion(texto: String, icono: ImageVector, color: Color, alPulsar: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Surface(
            onClick = alPulsar,
            shape = RoundedCornerShape(20.dp),
            color = color.copy(alpha = 0.14f),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icono, contentDescription = texto, tint = color) }
        }
        Spacer(Modifier.height(6.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FilaAviso(a: Aviso) {
    Surface(
        onClick = a.accion,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.icono, style = MaterialTheme.typography.titleMedium)
            Text(
                a.texto,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun MiniDato(titulo: String, valor: Long, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f)).padding(12.dp)
    ) {
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Formato.cop(valor), style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

@Composable
private fun TarjetaMiBanco(r: ResumenBanco, alAportar: () -> Unit, alPrestar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🏦 Mi banco", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Capital ${Formato.cop(r.capitalTotal)} · ganado ${Formato.cop(r.interesesGanados)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniDato("En bóveda", r.disponible, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                MiniDato("Prestado", r.porRecuperar, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
            if (r.capitalTotal > 0L) {
                LinearProgressIndicator(
                    progress = { r.fraccionPrestada },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )
            }
            Row {
                TextButton(onClick = alAportar) { Text("➕ Aportar") }
                TextButton(onClick = alPrestar) { Text("💸 Prestarme") }
            }
        }
    }
}
