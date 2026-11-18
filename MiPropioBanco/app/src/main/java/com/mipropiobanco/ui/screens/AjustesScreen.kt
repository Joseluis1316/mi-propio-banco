package com.mipropiobanco.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.data.prefs.ModoTema
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.util.Formato

val REGLAMENTO = listOf(
    "Regla de reserva: nunca prestes más del 70% de lo disponible en la bóveda. El 30% restante es tu colchón de emergencia y nunca se presta.",
    "La cuota es sagrada: se paga el mismo día del mes, por transferencia programada a la cuenta-bóveda, antes de cualquier otro gasto. No se aplaza ni se perdona.",
    "Tasa honesta: tu autotasa nunca es 0% ni menor que lo que rinde la cuenta de ahorro (≈0,75% mes). Por defecto 1,5% mensual.",
    "Un préstamo a la vez: no se abre un nuevo autopréstamo hasta haber devuelto al menos el 50% del anterior, y solo para compras planeadas, nunca para gustos impulsivos.",
    "El aporte mensual y los intereses no se tocan: el aporte fijo es aparte de la cuota y los autointereses se reinvierten durante al menos 24 meses."
)

@Composable
fun AjustesScreen(vm: BancoViewModel) {
    val modo by vm.modoTema.collectAsStateWithLifecycle()
    var confirmarImportacion by remember { mutableStateOf(false) }

    val exportarJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportarJson(it) } }

    val exportarCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { vm.exportarCsv(it) } }

    val importarJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importarJson(it) } }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Ajustes y respaldo")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Seccion("Apariencia") {
                    Column(Modifier.selectableGroup()) {
                        ModoTema.entries.forEach { opcion ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = modo == opcion,
                                        onClick = { vm.cambiarModoTema(opcion) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(selected = modo == opcion, onClick = null)
                                Text("  " + opcion.etiqueta)
                            }
                        }
                    }
                }
            }
            item {
                Seccion("Respaldo de datos") {
                    Text(
                        "Tus datos viven solo en este teléfono. Exporta un respaldo con frecuencia y guárdalo " +
                            "en otro lugar (correo, Drive o un computador).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { exportarJson.launch("mi_propio_banco_${Formato.hoyIso()}.json") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("  Exportar respaldo (JSON)")
                    }
                    FilledTonalButton(
                        onClick = { exportarCsv.launch("mi_propio_banco_movimientos_${Formato.hoyIso()}.csv") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("  Exportar movimientos (CSV)")
                    }
                    OutlinedButton(
                        onClick = { confirmarImportacion = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Text("  Importar respaldo (JSON)")
                    }
                }
            }
            item {
                Seccion("Reglamento interno") {
                    REGLAMENTO.forEachIndexed { i, regla ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${i + 1}.",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("  $regla", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Seccion("Acerca de") {
                    Text(
                        "Mi Propio Banco 1.0 · 100% offline, sin servidores ni permisos de internet. " +
                            "Montos en pesos colombianos sin decimales; cuotas por sistema francés.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (confirmarImportacion) {
        AlertDialog(
            onDismissRequest = { confirmarImportacion = false },
            icon = { Icon(Icons.Filled.Gavel, contentDescription = null) },
            title = { Text("¿Importar respaldo?") },
            text = {
                Text(
                    "Todos los datos actuales (aportes, préstamos y pagos) se reemplazarán por los del archivo. " +
                        "Si quieres conservarlos, exporta primero un respaldo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarImportacion = false
                    importarJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text("Elegir archivo") }
            },
            dismissButton = { TextButton(onClick = { confirmarImportacion = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            contenido()
        }
    }
}
