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
import androidx.compose.material.icons.filled.Backup
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
import com.mipropiobanco.util.Formato

@Composable
fun DashboardScreen(
    vm: BancoViewModel,
    alNuevoAporte: () -> Unit,
    alNuevoPrestamo: () -> Unit,
    alRegistrarPago: () -> Unit,
    alRespaldo: () -> Unit,
    alAbrirPrestamo: (Long) -> Unit
) {
    val resumen by vm.resumen.collectAsStateWithLifecycle()
    val prestamos by vm.prestamos.collectAsStateWithLifecycle()
    val activos = prestamos.filter { !it.prestamo.pagado }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    "Mi Propio Banco",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tu fondo de autofinanciamiento",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                estiloValor = MaterialTheme.typography.headlineSmall
            )
        }
        item {
            Text("Acciones rápidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccionRapida("Aporte", Icons.Filled.Savings, alNuevoAporte, Modifier.weight(1f))
                AccionRapida("Préstamo", Icons.Filled.RequestQuote, alNuevoPrestamo, Modifier.weight(1f))
                AccionRapida("Pagar", Icons.Filled.Payments, alRegistrarPago, Modifier.weight(1f))
                AccionRapida("Respaldo", Icons.Filled.Backup, alRespaldo, Modifier.weight(1f))
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
                TarjetaPrestamo(item = item, alAbrir = { alAbrirPrestamo(item.prestamo.id) })
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
