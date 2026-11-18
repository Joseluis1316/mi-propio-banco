package com.mipropiobanco.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.domain.Amortizacion
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.BarraProgreso
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.EncabezadoTabla
import com.mipropiobanco.ui.components.FilaResumen
import com.mipropiobanco.ui.components.FilaTabla
import com.mipropiobanco.util.Formato

private enum class Confirmacion { PAGAR, DESHACER, ANULAR }

@Composable
fun DetallePrestamoScreen(vm: BancoViewModel, prestamoId: Long, alVolver: () -> Unit) {
    val prestamos by vm.prestamos.collectAsStateWithLifecycle()
    val item = prestamos.firstOrNull { it.prestamo.id == prestamoId }

    if (item == null) {
        Column(Modifier.fillMaxSize()) {
            Encabezado("Préstamo", alVolver = alVolver)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Préstamo no encontrado", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val p = item.prestamo
    val tabla = remember(p.monto, p.plazoMeses, p.tasaMensual) {
        Amortizacion.tabla(p.monto, p.plazoMeses, p.tasaMensual)
    }
    val pagosPorCuota = item.pagos.associateBy { it.numeroCuota }
    val siguiente = if (p.pagado) null else tabla.getOrNull(item.cuotasPagadas)
    var confirmacion by remember { mutableStateOf<Confirmacion?>(null) }

    Column(Modifier.fillMaxSize()) {
        Encabezado(p.concepto, alVolver = alVolver)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (p.pagado) "Préstamo pagado ✓" else "Saldo por recuperar",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            Formato.cop(item.saldoPendiente),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        BarraProgreso(item.progreso)
                        Text(
                            "${item.cuotasPagadas} de ${p.plazoMeses} cuotas · " +
                                "${Formato.porcentaje(item.progreso * 100.0, 0)} del capital recuperado",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        FilaResumen("Monto prestado", Formato.cop(p.monto))
                        FilaResumen(
                            "Tasa",
                            "${Formato.tasa(p.tasaMensual)} mes (${Formato.porcentaje(Amortizacion.tasaEfectivaAnual(p.tasaMensual))} E.A.)"
                        )
                        FilaResumen("Cuota fija", Formato.cop(p.cuota))
                        FilaResumen("Desembolsado", Formato.fecha(p.fechaCreacion))
                        FilaResumen("Capital recuperado", Formato.cop(item.capitalPagado))
                        FilaResumen("Autointereses ganados", Formato.cop(item.interesesPagados), destacado = true)
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (siguiente != null) {
                    Button(
                        onClick = { confirmacion = Confirmacion.PAGAR },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.Payments, contentDescription = null)
                        Text("  Pagar cuota ${siguiente.numero} · ${Formato.cop(siguiente.cuota)}")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.pagos.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { confirmacion = Confirmacion.DESHACER },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                            Text(" Deshacer pago")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { confirmacion = Confirmacion.ANULAR },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Cancel, contentDescription = null)
                            Text(" Anular préstamo")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Tabla de amortización (pesos)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                EncabezadoTabla()
            }
            items(tabla, key = { it.numero }) { fila ->
                val pago = pagosPorCuota[fila.numero]
                FilaTabla(fila, pagada = pago != null, fechaPago = pago?.let { Formato.fecha(it.fecha) })
            }
        }
    }

    when (confirmacion) {
        Confirmacion.PAGAR -> siguiente?.let { fila ->
            DialogoConfirmar(
                titulo = "Registrar pago de la cuota ${fila.numero}",
                texto = "Cuota: ${Formato.cop(fila.cuota)}\n\n" +
                    "• ${Formato.cop(fila.capital)} de capital regresan a la bóveda.\n" +
                    "• ${Formato.cop(fila.interes)} de interés se suman a la bóveda y a tus autointereses ganados.",
                accion = "Registrar pago",
                alConfirmar = { vm.pagarCuota(p.id) },
                alCerrar = { confirmacion = null }
            )
        }
        Confirmacion.DESHACER -> DialogoConfirmar(
            titulo = "¿Deshacer el último pago?",
            texto = "Úsalo solo si registraste una cuota por error. El dinero de esa cuota sale de nuevo de la bóveda.",
            accion = "Deshacer",
            alConfirmar = { vm.deshacerUltimoPago(p.id) },
            alCerrar = { confirmacion = null }
        )
        Confirmacion.ANULAR -> DialogoConfirmar(
            titulo = "¿Anular este préstamo?",
            texto = "Los ${Formato.cop(p.monto)} regresarán completos a la bóveda y el préstamo se eliminará.",
            accion = "Anular",
            alConfirmar = { vm.anularPrestamo(p.id, alVolver) },
            alCerrar = { confirmacion = null }
        )
        null -> Unit
    }
}

@Composable
private fun DialogoConfirmar(
    titulo: String,
    texto: String,
    accion: String,
    alConfirmar: () -> Unit,
    alCerrar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(onClick = {
                alConfirmar()
                alCerrar()
            }) { Text(accion) }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}
