package com.mipropiobanco.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mipropiobanco.domain.DestinoFila
import com.mipropiobanco.domain.FilaExtracto
import com.mipropiobanco.domain.InterpreteExtracto
import com.mipropiobanco.util.Formato

/**
 * Varios movimientos leídos de una captura del extracto: uno por uno decides si es gasto,
 * ingreso, plata que te prestaron (se vuelve deuda) u omitirlo.
 */
@Composable
fun DialogoExtracto(
    filas: List<FilaExtracto>,
    yaRegistrado: (FilaExtracto) -> Boolean,
    alGuardar: (List<DestinoFila>, List<String>) -> Unit,
    alCancelar: () -> Unit
) {
    val destinos = remember(filas) {
        mutableStateListOf<DestinoFila>().apply {
            filas.forEach { f -> add(if (yaRegistrado(f)) DestinoFila.OMITIR else InterpreteExtracto.destinoSugerido(f)) }
        }
    }
    val acreedores = remember(filas) { mutableStateListOf<String>().apply { filas.forEach { add("") } } }
    val aGuardar = destinos.count { it != DestinoFila.OMITIR }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("📋 ${filas.size} movimientos en la imagen") },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        "Elige qué es cada uno. Si alguien te prestó esa plata, marca 🤝: entra como ingreso y se crea la deuda.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                itemsIndexed(filas) { i, f ->
                    val repetido = yaRegistrado(f)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.descripcion, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text(Formato.fecha(f.fecha), style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    (if (f.salida) "−" else "+") + Formato.cop(f.monto),
                                    fontWeight = FontWeight.Bold,
                                    color = if (f.salida) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            if (repetido) {
                                Text("⚠️ Parece que ya está anotado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                DestinoFila.entries.forEach { d ->
                                    FilterChip(
                                        selected = destinos[i] == d,
                                        onClick = { destinos[i] = d },
                                        label = { Text(d.etiqueta) }
                                    )
                                }
                            }
                            if (destinos[i] == DestinoFila.PRESTAMO) {
                                OutlinedTextField(
                                    value = acreedores[i],
                                    onValueChange = { acreedores[i] = it },
                                    label = { Text("¿Quién te prestó?") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { alGuardar(destinos.toList(), acreedores.toList()) }, enabled = aGuardar > 0) {
                Text("Guardar $aGuardar")
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
    )
}
