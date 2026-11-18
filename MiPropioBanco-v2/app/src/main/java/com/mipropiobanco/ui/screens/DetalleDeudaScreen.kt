package com.mipropiobanco.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.data.local.AbonoDeudaEntity
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.BarraProgreso
import com.mipropiobanco.ui.components.CampoMonto
import com.mipropiobanco.ui.components.Cronometro
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.FilaResumen
import com.mipropiobanco.ui.components.SelectorFecha
import com.mipropiobanco.ui.components.rememberAhora
import com.mipropiobanco.util.Formato

@Composable
fun DetalleDeudaScreen(vm: BancoViewModel, deudaId: Long, alVolver: () -> Unit) {
    val deudas by vm.deudas.collectAsStateWithLifecycle()
    val plan by vm.planDeudas.collectAsStateWithLifecycle()
    val item = deudas.firstOrNull { it.deuda.id == deudaId }

    if (item == null) {
        Column(Modifier.fillMaxSize()) {
            Encabezado("Deuda", alVolver = alVolver)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Deuda no encontrada", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val ahora = rememberAhora()
    val d = item.deuda
    val estado = item.estado(ahora)
    val posicion = plan.orden.indexOfFirst { it.deuda.id == deudaId }
    val pagoSugerido = plan.pagoEsteMes[deudaId] ?: minOf(d.pagoMinimo, item.saldo)
    var pagar by remember { mutableStateOf(false) }
    var editar by remember { mutableStateOf(false) }
    var eliminar by remember { mutableStateOf(false) }
    var abonoPorEliminar by remember { mutableStateOf<AbonoDeudaEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Encabezado(d.acreedor, alVolver = alVolver, acciones = {
            IconButton(onClick = { editar = true }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
            IconButton(onClick = { eliminar = true }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
        })
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (item.pagada) "Deuda pagada ✓" else "Saldo pendiente", style = MaterialTheme.typography.labelLarge)
                        Text(Formato.cop(item.saldo), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        BarraProgreso(item.progreso)
                        Text(
                            "Has pagado ${Formato.cop(item.totalAbonado)} de ${Formato.cop(d.saldoInicial)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (estado != null) {
                item {
                    Cronometro(
                        titulo = if (estado.atrasado) "Atrasado ${estado.diasAtraso} ${if (estado.diasAtraso == 1L) "día" else "días"} (venció ${Formato.fecha(estado.vence)})"
                        else "Próximo pago: ${Formato.fecha(estado.vence)}",
                        detalle = if (estado.atrasado) "Paga ya para evitar intereses de mora" else "Faltan ${Formato.duracion(estado.restanteMs)}",
                        atrasado = estado.atrasado
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (posicion >= 0) {
                            FilaResumen(
                                "Puesto en la bola de nieve",
                                if (posicion == 0) "#1 🎯 objetivo" else "#${posicion + 1} (solo mínimo)"
                            )
                        }
                        if (!item.pagada) FilaResumen("Paga este mes", Formato.cop(pagoSugerido), destacado = true)
                        FilaResumen("Pago mínimo", Formato.cop(d.pagoMinimo))
                        FilaResumen("Día de pago", "Día ${d.diaPago} de cada mes")
                        FilaResumen("Tasa", if (d.tasaEA > 0.0) "${Formato.tasa(d.tasaEA)} E.A. (${Formato.tasa(item.tasaMensual)} mes)" else "Sin intereses")
                        plan.mesLiquidacion[deudaId]?.let { FilaResumen("La liquidas en", mesDelPlan(it)) }
                    }
                }
            }
            if (!item.pagada) {
                item {
                    Button(onClick = { pagar = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Filled.Payments, contentDescription = null)
                        Text("  Registrar pago")
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Historial de pagos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (item.abonos.isEmpty()) {
                    Text("Aún no hay pagos registrados.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(item.abonos.reversed(), key = { it.id }) { abono ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(Formato.cop(abono.monto), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(Formato.fecha(abono.fecha)) },
                        trailingContent = {
                            IconButton(onClick = { abonoPorEliminar = abono }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar pago")
                            }
                        }
                    )
                }
            }
        }
    }

    if (pagar) {
        DialogoAbono(
            sugerido = pagoSugerido,
            saldo = item.saldo,
            alConfirmar = { monto, fecha -> vm.abonarDeuda(deudaId, monto, fecha) },
            alCerrar = { pagar = false }
        )
    }
    if (editar) {
        DialogoDeuda(
            existente = item,
            alGuardar = { acreedor, saldo, minimo, tasa, dia ->
                vm.guardarDeuda(deudaId, acreedor, saldo, minimo, tasa, dia) { editar = false }
            },
            alCerrar = { editar = false }
        )
    }
    if (eliminar) {
        DialogoConfirmar(
            titulo = "¿Eliminar esta deuda?",
            texto = "Se borrará la deuda con ${d.acreedor} y todo su historial de pagos.",
            accion = "Eliminar",
            alConfirmar = { vm.eliminarDeuda(deudaId, alVolver) },
            alCerrar = { eliminar = false }
        )
    }
    abonoPorEliminar?.let { abono ->
        DialogoConfirmar(
            titulo = "¿Eliminar este pago?",
            texto = "Se quitará el pago de ${Formato.cop(abono.monto)} del ${Formato.fecha(abono.fecha)} y el saldo subirá.",
            accion = "Eliminar",
            alConfirmar = { vm.eliminarAbono(abono) },
            alCerrar = { abonoPorEliminar = null }
        )
    }
}

@Composable
private fun DialogoAbono(sugerido: Long, saldo: Long, alConfirmar: (Long, Long) -> Unit, alCerrar: () -> Unit) {
    var monto by rememberSaveable { mutableStateOf(sugerido.toString()) }
    var fecha by rememberSaveable { mutableStateOf(Formato.hoyMillis()) }
    val montoV = monto.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Registrar pago") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CampoMonto(
                    valor = monto,
                    alCambiar = { monto = it },
                    etiqueta = "Monto pagado",
                    apoyo = "Sugerido por la bola de nieve: ${Formato.cop(sugerido)}"
                )
                SelectorFecha("Fecha", fecha, { fecha = it })
                if (montoV >= saldo) {
                    Text(
                        "Con este pago liquidas la deuda 🎉 (se registra ${Formato.cop(saldo)})",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = montoV > 0L, onClick = {
                alConfirmar(montoV, fecha)
                alCerrar()
            }) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}
