@file:OptIn(ExperimentalMaterial3Api::class)

package com.mipropiobanco.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.data.local.AporteEntity
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.CampoMonto
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.EstadoVacio
import com.mipropiobanco.util.Formato

@Composable
fun AportesScreen(vm: BancoViewModel, abrirFormulario: Boolean) {
    val aportes by vm.aportes.collectAsStateWithLifecycle()
    val resumen by vm.resumen.collectAsStateWithLifecycle()
    var mostrarFormulario by rememberSaveable { mutableStateOf(abrirFormulario) }
    var porEliminar by remember { mutableStateOf<AporteEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Encabezado("Aportes de capital")
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Total aportado", style = MaterialTheme.typography.labelLarge)
                            Text(
                                Formato.cop(resumen.totalAportes),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${aportes.size} aportes · Disponible en bóveda ${Formato.cop(resumen.disponible)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                if (aportes.isEmpty()) {
                    item {
                        EstadoVacio(
                            "Aún no hay aportes",
                            "Registra tu capital inicial para fundar tu banco y luego tu aporte fijo cada mes."
                        )
                    }
                }
                items(aportes, key = { it.id }) { aporte ->
                    Card(Modifier.fillMaxWidth()) {
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.Savings, contentDescription = null) },
                            headlineContent = { Text(aporte.concepto, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(Formato.fecha(aporte.fecha)) },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "+" + Formato.cop(aporte.monto),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(onClick = { porEliminar = aporte }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar aporte")
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { mostrarFormulario = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nuevo aporte") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (mostrarFormulario) {
        DialogoAporte(
            conceptoSugerido = if (aportes.isEmpty()) "Capital inicial" else "Aporte mensual",
            alCerrar = { mostrarFormulario = false },
            alGuardar = { fecha, concepto, monto ->
                vm.registrarAporte(fecha, concepto, monto)
                mostrarFormulario = false
            }
        )
    }

    porEliminar?.let { aporte ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text("¿Eliminar aporte?") },
            text = {
                Text(
                    "Se quitarán ${Formato.cop(aporte.monto)} («${aporte.concepto}») de la bóveda. " +
                        "Hazlo solo para corregir un error de registro."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.eliminarAporte(aporte)
                    porEliminar = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { porEliminar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun DialogoAporte(
    conceptoSugerido: String,
    alCerrar: () -> Unit,
    alGuardar: (fecha: Long, concepto: String, monto: Long) -> Unit
) {
    var monto by rememberSaveable { mutableStateOf("") }
    var concepto by rememberSaveable { mutableStateOf(conceptoSugerido) }
    var fecha by rememberSaveable { mutableStateOf(Formato.hoyMillis()) }
    var mostrarCalendario by remember { mutableStateOf(false) }
    val montoValor = monto.toLongOrNull() ?: 0L
    val valido = montoValor > 0L && concepto.isNotBlank()

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Nuevo aporte de capital") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoMonto(valor = monto, alCambiar = { monto = it }, etiqueta = "Monto")
                OutlinedTextField(
                    value = concepto,
                    onValueChange = { concepto = it.take(60) },
                    label = { Text("Concepto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { mostrarCalendario = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Text("  " + Formato.fecha(fecha))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valido, onClick = { alGuardar(fecha, concepto, montoValor) }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )

    if (mostrarCalendario) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fecha)
        DatePickerDialog(
            onDismissRequest = { mostrarCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { fecha = it }
                    mostrarCalendario = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarCalendario = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = estado)
        }
    }
}
