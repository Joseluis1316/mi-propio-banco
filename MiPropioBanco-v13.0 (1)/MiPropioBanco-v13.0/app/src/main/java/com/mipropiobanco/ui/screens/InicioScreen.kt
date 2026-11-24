package com.mipropiobanco.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.domain.EstadoPago
import com.mipropiobanco.domain.PresupuestoQuincenal
import com.mipropiobanco.domain.Semaforo
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.MontoAnimado
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val es = Locale("es", "CO")

/**
 * Inicio responde una sola pregunta: ¿cómo voy?
 * Cuánto puedo gastar hoy, qué tengo y qué debo, si alcanza la quincena y qué viene por pagar.
 */
@Composable
fun InicioScreen(
    vm: BancoViewModel,
    alAjustes: () -> Unit,
    alPlan: () -> Unit,
    alDeudas: () -> Unit,
    alMovimientos: () -> Unit,
    alPorRevisar: () -> Unit,
    alAhorro: () -> Unit
) {
    val diagnostico by vm.diagnostico.collectAsStateWithLifecycle()
    val plan by vm.presupuestoActual.collectAsStateWithLifecycle()
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val medios by vm.medios.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val deudas by vm.deudas.collectAsStateWithLifecycle()
    val bandeja by vm.bandeja.collectAsStateWithLifecycle()
    val ocultar by vm.ocultarSaldos.collectAsStateWithLifecycle()

    val hoy = LocalDate.now()
    val tengo = com.mipropiobanco.domain.calcularSaldos(medios, movimientos).values.sum()
    val activas = deudas.filter { !it.pagada }
    val debo = activas.sumOf { it.saldo }
    val cuadre = com.mipropiobanco.domain.Cuadre.de(movimientos, medios, categorias, YearMonth.now())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Encabezado
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${hoy.dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() }} " +
                            "${hoy.dayOfMonth} de ${hoy.month.getDisplayName(TextStyle.FULL, es)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("¿Cómo voy?", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = { vm.cambiarOcultarSaldos(!ocultar) }) {
                    Icon(if (ocultar) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Mostrar u ocultar montos")
                }
                IconButton(onClick = alAjustes) { Icon(Icons.Filled.Settings, contentDescription = "Ajustes") }
            }
        }

        // Cuánto puedo gastar hoy
        item {
            val d = diagnostico
            Tarjeta(alPulsar = alPlan) {
                Text(
                    if (d != null && d.hoyPuedes < 0L) "Hoy te pasaste" else "Puedes gastar hoy",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (d == null) {
                    Text("…", style = MaterialTheme.typography.displaySmall)
                } else {
                    MontoAnimado(
                        if (d.hoyPuedes >= 0L) d.hoyPuedes else -d.hoyPuedes,
                        MaterialTheme.typography.displaySmall,
                        if (d.hoyPuedes >= 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    val usado = if (d.porDia > 0L) (d.gastadoHoy.toFloat() / d.porDia).coerceIn(0f, 1f) else if (d.gastadoHoy > 0L) 1f else 0f
                    Barra(usado, if (usado >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text(
                        if (plan?.ingresosPlaneados == 0L) "Configura en Plan cuánto ganas y qué días, y lo calculo exacto."
                        else "Llevas ${Formato.cop(d.gastadoHoy)} de ${Formato.cop(d.porDia)} del día · quedan ${d.diasRestantes} día(s) de quincena",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Tengo / Debo
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Mini("Tengo", Formato.cop(tengo), "${medios.size} cuenta(s) y efectivo", Modifier.weight(1f), alMovimientos)
                Mini("Debo", Formato.cop(debo), "${activas.size} deuda(s) activa(s)", Modifier.weight(1f), alDeudas)
            }
        }

        // Plata gastada sin origen: hay que cuadrarla (préstamo, ingreso o saldo que ya tenías).
        if (cuadre.totalSinOrigen > 0L) {
            item {
                com.mipropiobanco.ui.components.TarjetaCuadre(
                    cuadre, "Hay plata que no cuadra",
                    alCorregirInicio = { real, origen, acreedor -> vm.corregirInicio(YearMonth.now(), real, origen, acreedor) }
                ) { medio, monto, origen, acreedor -> vm.explicarFaltante(medio, monto, origen, acreedor) }
            }
        }

        if (bandeja.isNotEmpty()) {
            item {
                Surface(
                    onClick = alPorRevisar,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text("${bandeja.size} movimiento(s) por revisar", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("Revisar", color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }

        // La quincena
        plan?.let { pm ->
            val q = pm.de(PresupuestoQuincenal.quincenaDe(hoy))
            item {
                Tarjeta(alPulsar = alPlan) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${q.quincena.etiqueta} · ${q.desde.dayOfMonth} al ${q.hasta.dayOfMonth}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        val (texto, fondo, tinta) = when (q.semaforo) {
                            Semaforo.VERDE -> Triple("Alcanza", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                            Semaforo.AMARILLO -> Triple("Justo", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                            Semaforo.ROJO -> Triple("No alcanza", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
                        }
                        Surface(shape = RoundedCornerShape(10.dp), color = fondo) {
                            Text(texto, color = tinta, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    Linea("Tenías al empezar", Formato.cop(q.saldoQueViene))
                    Linea("Te entró", Formato.cop(q.ingresosReales))
                    if (q.porRecibir > 0L) Linea("Te falta recibir", Formato.cop(q.porRecibir))
                    Linea("Ya gastaste", Formato.cop(q.salidasReales))
                    Linea("Pagos que faltan", Formato.cop(q.pendiente))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row {
                        Text(if (q.libre >= 0L) "Te queda libre" else "Te falta", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            Formato.cop(if (q.libre >= 0L) q.libre else -q.libre),
                            fontWeight = FontWeight.SemiBold,
                            color = if (q.libre >= 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Próximos pagos
            val proximos = (pm.q1.obligaciones + pm.q2.obligaciones)
                .filter { it.estado != EstadoPago.PAGADO }
                .sortedWith(compareBy({ it.estado != EstadoPago.VENCIDO }, { it.fecha }))
                .take(4)
            if (proximos.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Próximos pagos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = alPlan) { Text("Ver plan") }
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            proximos.forEachIndexed { i, o ->
                                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(String.format("%02d", o.fecha.dayOfMonth), style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            o.fecha.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.').uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(o.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (o.estado == EstadoPago.VENCIDO) {
                                            Text("Vencido", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Text(Formato.cop(o.monto), fontWeight = FontWeight.SemiBold)
                                }
                                if (i < proximos.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        // Un solo consejo, el más importante
        diagnostico?.consejos?.firstOrNull()?.let { c ->
            item {
                val (fondo, tinta) = when (c.nivel) {
                    com.mipropiobanco.domain.NivelConsejo.PELIGRO -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                    com.mipropiobanco.domain.NivelConsejo.ALERTA -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                }
                Surface(
                    onClick = {
                        when (c.accion) {
                            com.mipropiobanco.domain.AccionConsejo.DEUDAS -> alDeudas()
                            com.mipropiobanco.domain.AccionConsejo.GASTOS -> alMovimientos()
                            com.mipropiobanco.domain.AccionConsejo.METAS, com.mipropiobanco.domain.AccionConsejo.BANCO -> alAhorro()
                            else -> alPlan()
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = fondo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.titulo, fontWeight = FontWeight.SemiBold, color = tinta)
                        Text(c.detalle, style = MaterialTheme.typography.bodySmall, color = tinta.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Tarjeta(alPulsar: () -> Unit, contenido: @Composable () -> Unit) {
    Surface(
        onClick = alPulsar,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { contenido() }
    }
}

@Composable
private fun Mini(titulo: String, valor: String, detalle: String, modifier: Modifier, alPulsar: () -> Unit) {
    Surface(onClick = alPulsar, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(detalle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Linea(texto: String, valor: String) {
    Row {
        Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Barra(fraccion: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(fraccion).height(6.dp).clip(RoundedCornerShape(50)).background(color))
    }
}
