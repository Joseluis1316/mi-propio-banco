@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mipropiobanco.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.domain.Meta
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.CampoMonto
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.EstadoVacio
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Metas de ahorro: cuánto quiero, cuánto llevo y cuánto me falta por mes. */
@Composable
fun MetasScreen(vm: BancoViewModel, alVolver: () -> Unit) {
    val metas by vm.metas.collectAsStateWithLifecycle()
    var editando by remember { mutableStateOf<Meta?>(null) }
    var nueva by remember { mutableStateOf(false) }
    var abonando by remember { mutableStateOf<Meta?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Encabezado("🎯 Metas de ahorro", alVolver = alVolver)
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (metas.isNotEmpty()) {
                    item {
                        val total = metas.sumOf { it.ahorrado }
                        val objetivo = metas.sumOf { it.objetivo }
                        Text(
                            "Llevas ${Formato.cop(total)} de ${Formato.cop(objetivo)} en ${metas.size} metas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (metas.isEmpty()) {
                    item {
                        EstadoVacio(
                            "Ponle nombre a tus sueños",
                            "Crea una meta (moto, viaje, fondo de emergencia) y ve abonándole. La app te dice cuánto separar cada mes."
                        )
                    }
                }
                items(metas, key = { it.id }) { m ->
                    TarjetaMeta(m, alAbonar = { abonando = m }, alEditar = { editando = m })
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nueva = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nueva meta") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }

    if (nueva || editando != null) {
        DialogoMeta(
            meta = editando,
            alCerrar = { nueva = false; editando = null },
            alGuardar = { nombre, icono, objetivo, fecha ->
                vm.guardarMeta(editando?.id, nombre, icono, objetivo, fecha)
                nueva = false; editando = null
            },
            alEliminar = editando?.let { m -> { vm.eliminarMeta(m.id); editando = null } }
        )
    }
    abonando?.let { m ->
        DialogoAbono(
            meta = m,
            alCerrar = { abonando = null },
            alGuardar = { monto, desdeCuenta -> vm.abonarMeta(m.id, monto, desdeCuenta); abonando = null }
        )
    }
}

@Composable
private fun TarjetaMeta(m: Meta, alAbonar: () -> Unit, alEditar: () -> Unit) {
    Card(
        onClick = alEditar,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (m.cumplida) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.icono, style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(m.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${Formato.cop(m.ahorrado)} de ${Formato.cop(m.objetivo)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("${(m.fraccion * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = { m.fraccion },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {}
            )
            val detalle = when {
                m.cumplida -> "🎉 ¡Meta cumplida!"
                m.fechaLimite > 0L -> {
                    val meses = ChronoUnit.MONTHS.between(LocalDate.now(), Formato.aLocalDate(m.fechaLimite)).coerceAtLeast(1L)
                    "Faltan ${Formato.cop(m.falta)} · separa ${Formato.cop(m.falta / meses)} al mes hasta el ${Formato.fecha(m.fechaLimite)}"
                }
                else -> "Faltan ${Formato.cop(m.falta)}"
            }
            Text(detalle, style = MaterialTheme.typography.bodySmall)
            if (!m.cumplida) {
                FilledTonalButton(onClick = alAbonar, modifier = Modifier.fillMaxWidth()) { Text("➕ Abonar a esta meta") }
            }
        }
    }
}

@Composable
private fun DialogoMeta(
    meta: Meta?,
    alCerrar: () -> Unit,
    alGuardar: (String, String, Long, Long) -> Unit,
    alEliminar: (() -> Unit)?
) {
    var nombre by remember { mutableStateOf(meta?.nombre ?: "") }
    var icono by remember { mutableStateOf(meta?.icono ?: "🎯") }
    var objetivo by remember { mutableStateOf(meta?.objetivo?.toString() ?: "") }
    var fecha by remember { mutableStateOf(meta?.fechaLimite ?: 0L) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(if (meta == null) "Nueva meta" else "Editar meta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("🎯", "🏍️", "✈️", "🏠", "🚗", "📱", "🎓", "🛟").forEach { e ->
                        TextButton(onClick = { icono = e }, contentPadding = PaddingValues(2.dp)) {
                            Text(e, style = if (icono == e) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it },
                    label = { Text("¿Para qué ahorras?") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                CampoMonto(valor = objetivo, alCambiar = { objetivo = it }, etiqueta = "¿Cuánto necesitas?")
                com.mipropiobanco.ui.components.SelectorFecha(
                    "Para cuándo", if (fecha > 0L) fecha else Formato.aMillis(LocalDate.now().plusMonths(6)), { fecha = it }
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { alGuardar(nombre, icono, objetivo.toLongOrNull() ?: 0L, fecha) }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (alEliminar != null) TextButton(onClick = alEliminar) { Text("Eliminar") }
                TextButton(onClick = alCerrar) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun DialogoAbono(meta: Meta, alCerrar: () -> Unit, alGuardar: (Long, Boolean) -> Unit) {
    var monto by remember { mutableStateOf("") }
    var desdeCuenta by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("${meta.icono} Abonar a ${meta.nombre}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoMonto(valor = monto, alCambiar = { monto = it }, etiqueta = "¿Cuánto separas?")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = desdeCuenta, onCheckedChange = { desdeCuenta = it })
                    Text("Sale de mi cuenta (se anota como ahorro en Gastos)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { alGuardar(monto.toLongOrNull() ?: 0L, desdeCuenta) }) { Text("Abonar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}
