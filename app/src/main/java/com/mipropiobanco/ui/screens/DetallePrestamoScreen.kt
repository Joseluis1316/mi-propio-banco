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
import com.mipropiobanco.domain.PrestamoConPagos
import com.mipropiobanco.domain.Tasas
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.BarraProgreso
import com.mipropiobanco.ui.components.CronometroPrestamo
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.EncabezadoTabla
import com.mipropiobanco.ui.components.FilaResumen
import com.mipropiobanco.ui.components.FilaTabla
import com.mipropiobanco.ui.components.SelectorFecha
import com.mipropiobanco.ui.components.rememberAhora
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

    val ahora = rememberAhora()
    val p = item.prestamo
    val per = item.periodicidad
    val pagosPorCuota = item.pagos.associateBy { it.numeroCuota }
    val siguiente = item.proximaCuota
    var confirmacion by remember { mutableStateOf<Confirmacion?>(null) }

    Column(Modifier.fillMaxSize()) {
        Encabezado(p.concepto, alVolver = alVolver)
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
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
                            "${item.cuotasPagadas} de ${per.cantidad(p.plazo)} · " +
                                "${Formato.porcentaje(item.progreso * 100.0, 0)} del capital recuperado",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                CronometroPrestamo(item, ahora)
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        FilaResumen("Tipo", p.tipo)
                        FilaResumen("Monto prestado", Formato.cop(p.monto))
                        FilaResumen("Tasa", "${Formato.tasa(p.tasaEA)} E.A. (${Formato.tasa(p.tasaPeriodo)} por ${per.singular})")
                        FilaResumen("Tasa de mora", "${Formato.tasa(p.tasaMoraEA)} E.A. por día de atraso")
                        FilaResumen("Cuota fija", "${Formato.cop(p.cuota)} cada ${per.singular}")
                        FilaResumen("Desembolsado", Formato.fecha(p.fechaCreacion))
                        FilaResumen("Capital recuperado", Formato.cop(item.capitalPagado))
                        FilaResumen("Intereses cobrados", Formato.cop(item.interesesPagados))
                        if (item.moraPagada > 0L) FilaResumen("Mora cobrada", Formato.cop(item.moraPagada))
                        FilaResumen(
                            "Autointereses ganados",
                            Formato.cop(item.interesesPagados + item.moraPagada),
                            destacado = true
                        )
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
                        Text("  Pagar cuota ${siguiente.numero}")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.pagos.isNotEmpty()) {
                        OutlinedButton(onClick = { confirmacion = Confirmacion.DESHACER }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                            Text(" Deshacer pago")
                        }
                    } else {
                        OutlinedButton(onClick = { confirmacion = Confirmacion.ANULAR }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Cancel, contentDescription = null)
                            Text(" Anular préstamo")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Tabla de amortización (pesos)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                EncabezadoTabla()
            }
            items(item.tabla, key = { it.numero }) { fila ->
                FilaTabla(fila, vence = item.vencimiento(fila.numero), pago = pagosPorCuota[fila.numero])
            }
        }
    }

    when (confirmacion) {
        Confirmacion.PAGAR -> DialogoPagoCuota(
            item = item,
            alConfirmar = { fecha -> vm.pagarCuota(p.id, fecha) },
            alCerrar = { confirmacion = null }
        )
        Confirmacion.DESHACER -> DialogoConfirmar(
            titulo = "¿Deshacer el último pago?",
            texto = "Úsalo solo si registraste una cuota por error. El dinero de esa cuota (incluida la mora) sale de nuevo de la bóveda.",
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

/** Pago de cuota con fecha editable: si es posterior al vencimiento, calcula la mora por día. */
@Composable
private fun DialogoPagoCuota(item: PrestamoConPagos, alConfirmar: (Long) -> Unit, alCerrar: () -> Unit) {
    val fila = item.proximaCuota ?: return
    var fechaPago by remember { mutableStateOf(Formato.hoyMillis()) }
    val vence = item.vencimiento(fila.numero)
    val dias = Tasas.diasAtraso(vence, Formato.aLocalDate(fechaPago))
    val mora = Tasas.mora(fila.cuota, item.prestamo.tasaMoraEA, dias)

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Pagar cuota ${fila.numero}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilaResumen("Vence", Formato.fecha(vence))
                SelectorFecha("Fecha de pago", fechaPago, { fechaPago = it })
                FilaResumen("Capital → bóveda", Formato.cop(fila.capital))
                FilaResumen("Interés → bóveda", Formato.cop(fila.interes))
                if (dias > 0L) {
                    Text(
                        "Pago con $dias ${if (dias == 1L) "día" else "días"} de atraso",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                    FilaResumen("Interés de mora → bóveda", Formato.cop(mora))
                }
                FilaResumen("Total a pagar", Formato.cop(fila.cuota + mora), destacado = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                alConfirmar(fechaPago)
                alCerrar()
            }) { Text("Registrar pago") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}

@Composable
fun DialogoConfirmar(
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
