@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mipropiobanco.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.domain.ResumenBanco
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.EstadoVacio
import com.mipropiobanco.ui.components.TarjetaDato
import com.mipropiobanco.ui.components.TarjetaPrestamo
import com.mipropiobanco.ui.components.Cronometro
import com.mipropiobanco.ui.components.rememberAhora
import androidx.compose.material.icons.filled.CreditCard
import com.mipropiobanco.util.Formato

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
    alBandeja: () -> Unit = {}
) {
    val resumen by vm.resumen.collectAsStateWithLifecycle()
    val prestamos by vm.prestamos.collectAsStateWithLifecycle()
    val activos = prestamos.filter { !it.prestamo.pagado }
    val plan by vm.planDeudas.collectAsStateWithLifecycle()
    val ahora = rememberAhora()
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val plantillas by vm.plantillas.collectAsStateWithLifecycle()
    val bandeja by vm.bandeja.collectAsStateWithLifecycle()
    val resumenMes = com.mipropiobanco.domain.ResumenMes.de(movimientos, java.time.YearMonth.now())
    val porRegistrar = plantillas.sumOf { com.mipropiobanco.domain.Recurrencia.pendientes(it, java.time.LocalDate.now()).size }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Mi Propio Banco",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tus finanzas en un solo lugar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = alAjustes) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                }
            }
        }
        if (bandeja.isNotEmpty()) {
            item {
                Card(
                    onClick = alBandeja,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Text(
                        "📥 ${bandeja.size} movimientos de tus correos por revisar",
                        modifier = Modifier.padding(14.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        item {
            Card(onClick = alVerMovimientos, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Este mes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    com.mipropiobanco.ui.components.FilaResumen("Ingresos", Formato.cop(resumenMes.ingresos))
                    com.mipropiobanco.ui.components.FilaResumen("Gastos", Formato.cop(resumenMes.gastos))
                    com.mipropiobanco.ui.components.FilaResumen(
                        if (resumenMes.balance >= 0L) "Te sobra" else "Te faltó",
                        Formato.cop(resumenMes.balance),
                        destacado = true
                    )
                    if (porRegistrar > 0) {
                        Text(
                            "📌 Tienes $porRegistrar fijos por registrar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
        item { TarjetaCapitalTotal(resumen) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaDato(
                    titulo = "Disponible en bóveda",
                    valor = resumen.disponible,
                    icono = Icons.Filled.Lock,
                    modifier = Modifier.weight(1f)
                )
                TarjetaDato(
                    titulo = "Prestado por recuperar",
                    valor = resumen.porRecuperar,
                    icono = Icons.Filled.RequestQuote,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            TarjetaDato(
                titulo = "Autointereses ganados",
                valor = resumen.interesesGanados,
                icono = Icons.AutoMirrored.Filled.TrendingUp,
                modifier = Modifier.fillMaxWidth(),
                contenedor = MaterialTheme.colorScheme.tertiaryContainer,
                contenido = MaterialTheme.colorScheme.onTertiaryContainer,
                estiloValor = MaterialTheme.typography.headlineSmall,
                detalle = if (resumen.moraCobrada > 0L) "Incluye ${Formato.cop(resumen.moraCobrada)} de mora" else null
            )
        }
        item {
            Text("Acciones rápidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccionRapida("Gasto", Icons.Filled.RemoveCircle, { alNuevoMovimiento("GASTO") }, Modifier.weight(1f))
                AccionRapida("Ingreso", Icons.Filled.AddCircle, { alNuevoMovimiento("INGRESO") }, Modifier.weight(1f))
                AccionRapida("Hablar", Icons.Filled.Mic, { alNuevoMovimiento("VOZ") }, Modifier.weight(1f))
                AccionRapida("Recibo", Icons.Filled.CameraAlt, { alNuevoMovimiento("FOTO") }, Modifier.weight(1f))
            }
        }
        item {
            Text("Préstamos activos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (activos.isEmpty()) {
            item {
                EstadoVacio(
                    titulo = if (resumen.totalAportes == 0L) "Funda tu banco" else "Sin autopréstamos activos",
                    detalle = if (resumen.totalAportes == 0L) {
                        "Registra tu capital inicial con el botón «Aporte» para empezar."
                    } else {
                        "Todo tu capital está en la bóveda. Cuando necesites financiar algo, préstate a ti mismo."
                    }
                )
            }
        } else {
            items(activos, key = { it.prestamo.id }) { item ->
                TarjetaPrestamo(item = item, ahora = ahora, alAbrir = { alAbrirPrestamo(item.prestamo.id) })
            }
        }
        val objetivo = plan.orden.firstOrNull()
        if (objetivo != null) {
            item {
                Text("Deudas con terceros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(onClick = alVerDeudas, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CreditCard, contentDescription = null)
                            Text(
                                "  Debes ${Formato.cop(plan.orden.sumOf { it.saldo })} en ${plan.orden.size} deudas",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            "Objetivo bola de nieve: ${objetivo.deuda.acreedor} (${Formato.cop(objetivo.saldo)})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        plan.mesesTotales?.let {
                            Text("Libre de deudas en $it meses con tu plan", style = MaterialTheme.typography.bodySmall)
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
    }
}

@Composable
private fun TarjetaCapitalTotal(r: ResumenBanco) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountBalance, contentDescription = null)
                Spacer(Modifier.padding(start = 8.dp))
                Text("Capital total de mi banco", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                Formato.cop(r.capitalTotal),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                "Aportado ${Formato.cop(r.totalAportes)} + intereses ${Formato.cop(r.interesesGanados)}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { r.fraccionPrestada },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${Formato.porcentaje(r.fraccionPrestada * 100.0, 0)} del capital está prestado",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun AccionRapida(texto: String, icono: ImageVector, alPulsar: () -> Unit, modifier: Modifier) {
    FilledTonalButton(
        onClick = alPulsar,
        modifier = modifier.height(72.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icono, contentDescription = null)
            Text(texto, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
