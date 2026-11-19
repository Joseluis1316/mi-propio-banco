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
import com.mipropiobanco.data.local.TipoPrestamoEntity
import com.mipropiobanco.data.prefs.ModoTema
import com.mipropiobanco.data.sync.EstadoNube
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import com.mipropiobanco.ui.components.CampoTasa
import com.mipropiobanco.ui.components.FilaResumen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.util.Formato

val REGLAMENTO = listOf(
    "Regla de reserva: nunca prestes más del 70% de lo disponible en la bóveda. El 30% restante es tu colchón de emergencia y nunca se presta.",
    "La cuota es sagrada: se paga el mismo día del mes, por transferencia programada a la cuenta-bóveda, antes de cualquier otro gasto. No se aplaza ni se perdona.",
    "Tasa definida y mora: cada préstamo usa la tasa de su tipo (por defecto, la usura vigente) y nunca 0%. Si te atrasas, pagas interés de mora por cada día.",
    "Un préstamo a la vez y deudas primero: no se abre otro autopréstamo hasta devolver el 50% del anterior, y mientras tengas deudas con terceros, el excedente va a la bola de nieve.",
    "El aporte mensual y los intereses no se tocan: el aporte fijo es aparte de la cuota y los autointereses se reinvierten durante al menos 24 meses."
)

@Composable
fun AjustesScreen(vm: BancoViewModel, alVolver: () -> Unit, alConfigFinanzas: () -> Unit) {
    val modo by vm.modoTema.collectAsStateWithLifecycle()
    var confirmarImportacion by remember { mutableStateOf(false) }
    val tipos by vm.tipos.collectAsStateWithLifecycle()
    val correo by vm.correo.collectAsStateWithLifecycle()
    val estadoNube by vm.estadoNube.collectAsStateWithLifecycle()
    val procesando by vm.procesandoCuenta.collectAsStateWithLifecycle()
    var confirmarSalir by remember { mutableStateOf(false) }
    val autoCorreos by vm.autoCorreos.collectAsStateWithLifecycle()
    val portapapeles = androidx.compose.ui.platform.LocalClipboardManager.current
    var tipoEditando by remember { mutableStateOf<TipoPrestamoEntity?>(null) }
    var tipoNuevo by remember { mutableStateOf(false) }

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
        Encabezado("Ajustes y respaldo", alVolver = alVolver)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val bloqueo by vm.bloqueo.collectAsStateWithLifecycle()
                val ocultar by vm.ocultarSaldos.collectAsStateWithLifecycle()
                val respaldos by vm.respaldosAuto.collectAsStateWithLifecycle()
                var restaurando by remember { mutableStateOf<String?>(null) }
                Seccion("🔒 Seguridad y privacidad") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Bloquear con huella o PIN")
                            Text(
                                "Al abrir la app y después de 1 minuto en segundo plano.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        androidx.compose.material3.Switch(checked = bloqueo, onCheckedChange = { vm.cambiarBloqueo(it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Modo privado")
                            Text(
                                "Oculta los montos ($ •••••). También con el 👁 en Inicio.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        androidx.compose.material3.Switch(checked = ocultar, onCheckedChange = { vm.cambiarOcultarSaldos(it) })
                    }
                    Text("Copias automáticas (una por día, se guardan 7)", fontWeight = FontWeight.Medium)
                    if (respaldos.isEmpty()) {
                        Text("Aún no hay copias. Se crea una cada día al abrir la app.", style = MaterialTheme.typography.bodySmall)
                    }
                    respaldos.forEach { dia ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📦 $dia", modifier = Modifier.weight(1f))
                            androidx.compose.material3.TextButton(onClick = { restaurando = dia }) { Text("Restaurar") }
                        }
                    }
                }
                restaurando?.let { dia ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { restaurando = null },
                        title = { Text("Restaurar copia del $dia") },
                        text = { Text("Tus datos vuelven a como estaban ese día. Lo de hoy se guarda antes, y puedes deshacerlo desde Gastos.") },
                        confirmButton = {
                            FilledTonalButton(onClick = { vm.restaurarRespaldoAuto(dia); restaurando = null }) { Text("Restaurar") }
                        },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { restaurando = null }) { Text("Cancelar") } }
                    )
                }
            }
            item {
                Seccion("Cuenta y nube") {
                    val cuenta = correo
                    if (cuenta != null) {
                        val (icono, texto) = when (val e = estadoNube) {
                            is EstadoNube.AlDia -> Icons.Filled.CloudDone to "Datos guardados en la nube · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, Formato.localeCO).format(java.util.Date(e.cuando))}"
                            is EstadoNube.Sincronizando -> Icons.Filled.CloudSync to "Sincronizando…"
                            is EstadoNube.Pendiente -> Icons.Filled.CloudOff to e.motivo
                            EstadoNube.SinCuenta -> Icons.Filled.CloudOff to "Sin cuenta"
                        }
                        Text("Conectado como $cuenta", fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("  $texto", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Cada cambio se sube solo. Si cambias de celular, instala la app e inicia sesión con este correo.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        FilledTonalButton(onClick = { vm.sincronizarAhora() }, enabled = !procesando, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Sync, contentDescription = null)
                            Text("  Sincronizar ahora")
                        }
                        OutlinedButton(onClick = { confirmarSalir = true }, enabled = !procesando, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Logout, contentDescription = null)
                            Text("  Cerrar sesión")
                        }
                    } else {
                        Text(
                            "Estás usando la app sin cuenta: los datos solo están en este celular.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { vm.irAIniciarSesion() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null)
                            Text("  Iniciar sesión o crear cuenta")
                        }
                    }
                }
            }
            item {
                Seccion("📥 Correos de Bancolombia") {
                    InstruccionesGmail(vm.uidNube) {
                        vm.uidNube?.let { portapapeles.setText(androidx.compose.ui.text.AnnotatedString(it)) }
                    }
                    val urlScript by vm.urlScript.collectAsStateWithLifecycle()
                    var url by remember(urlScript) { mutableStateOf(urlScript) }
                    androidx.compose.material3.OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Enlace del script (para 🔄 Buscar correos ahora)") },
                        placeholder = { Text("https://script.google.com/macros/s/…/exec") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (url.trim() != urlScript) {
                        OutlinedButton(onClick = { vm.cambiarUrlScript(url) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Guardar enlace")
                        }
                    }
                    val gastosSolos by vm.gastosSolos.collectAsStateWithLifecycle()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Toda salida de plata = gasto")
                            Text(
                                "Lo que sale de tu cuenta se anota solo como gasto en 🏷️ Por clasificar; le pones la categoría cuando quieras.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        androidx.compose.material3.Switch(checked = gastosSolos, onCheckedChange = { vm.cambiarGastosSolos(it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Registrar solos los conocidos")
                            Text(
                                "Cuentas y comercios que ya clasificaste una vez se anotan sin preguntar.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        androidx.compose.material3.Switch(checked = autoCorreos, onCheckedChange = { vm.cambiarAutoCorreos(it) })
                    }
                }
            }
            item {
                Seccion("Ingresos y gastos") {
                    val conectarBanco by vm.conectarBanco.collectAsStateWithLifecycle()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Conectar Mi banco con mis cuentas")
                            Text(
                                "Aportes y cuotas salen de Bancolombia como gasto; el autopréstamo entra como ingreso.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        androidx.compose.material3.Switch(checked = conectarBanco, onCheckedChange = { vm.cambiarConectarBanco(it) })
                    }
                    Text(
                        "Plantillas fijas, categorías con presupuesto y medios de pago.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = alConfigFinanzas, modifier = Modifier.fillMaxWidth()) {
                        Text("Plantillas, categorías y medios de pago")
                    }
                }
            }
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
                Seccion("Tipos de préstamo y tasas") {
                    Text(
                        "Tasas de usura de noviembre de 2026 (Superfinanciera). Cambian cada mes: " +
                            "actualízalas aquí. Solo afectan los préstamos nuevos.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    tipos.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.nombre, fontWeight = FontWeight.Medium)
                                Text(
                                    "${Formato.tasa(t.tasaEA)} E.A. · mora ${Formato.tasa(t.tasaMoraEA)} E.A.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { tipoEditando = t }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Editar ${t.nombre}")
                            }
                        }
                    }
                    OutlinedButton(onClick = { tipoNuevo = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("  Agregar tipo")
                    }
                }
            }
            item {
                Seccion("Respaldo de datos") {
                    Text(
                        "Con cuenta ya tienes respaldo automático. Además puedes exportar un archivo " +
                            "para guardarlo donde quieras o abrirlo en Excel.",
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
                        "Mi Propio Banco · funciona sin internet; con cuenta, cada cambio se respalda en la nube (Firebase). " +
                            "Montos en pesos colombianos sin decimales; cuotas por sistema francés.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (confirmarSalir) {
        AlertDialog(
            onDismissRequest = { confirmarSalir = false },
            title = { Text("¿Cerrar sesión?") },
            text = {
                Text(
                    "Primero se suben tus datos a la nube y luego se borran de este celular. " +
                        "Los recuperas al volver a iniciar sesión. Necesitas internet."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalir = false
                    vm.cerrarSesion()
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { confirmarSalir = false }) { Text("Cancelar") } }
        )
    }

    if (tipoNuevo || tipoEditando != null) {
        DialogoTipo(
            tipo = tipoEditando,
            alGuardar = { nombre, ea, mora -> vm.guardarTipo(tipoEditando?.id, nombre, ea, mora) },
            alEliminar = tipoEditando?.let { t -> { vm.eliminarTipo(t) } },
            alCerrar = {
                tipoNuevo = false
                tipoEditando = null
            }
        )
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

@Composable
private fun DialogoTipo(
    tipo: TipoPrestamoEntity?,
    alGuardar: (String, Double, Double) -> Unit,
    alEliminar: (() -> Unit)?,
    alCerrar: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf(tipo?.nombre.orEmpty()) }
    var tasa by rememberSaveable { mutableStateOf(tipo?.tasaEA?.let { Formato.decimal(it) }.orEmpty()) }
    var mora by rememberSaveable { mutableStateOf(tipo?.tasaMoraEA?.let { Formato.decimal(it) }.orEmpty()) }
    val tasaV = Formato.parsearDecimal(tasa)
    val moraV = Formato.parsearDecimal(mora)
    val valido = nombre.isNotBlank() && tasaV != null && tasaV in 0.0..300.0 && moraV != null && moraV in 0.0..300.0

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(if (tipo == null) "Nuevo tipo de préstamo" else "Editar tipo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it.take(40) },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CampoTasa(valor = tasa, alCambiar = { tasa = it }, etiqueta = "Tasa de interés (usura)")
                CampoTasa(valor = mora, alCambiar = { mora = it }, etiqueta = "Tasa de mora", apoyo = "En Colombia la mora no puede superar la usura")
                if (tasaV != null) {
                    FilaResumen("Equivale mensual", Formato.tasa(com.mipropiobanco.domain.Tasas.periodoDesdeEA(tasaV, com.mipropiobanco.domain.Periodicidad.MENSUAL)))
                    FilaResumen("Equivale diaria", Formato.tasa(com.mipropiobanco.domain.Tasas.periodoDesdeEA(tasaV, com.mipropiobanco.domain.Periodicidad.DIARIA)))
                }
                if (alEliminar != null) {
                    TextButton(onClick = {
                        alEliminar()
                        alCerrar()
                    }) { Text("Eliminar este tipo", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valido, onClick = {
                alGuardar(nombre, tasaV ?: 0.0, moraV ?: 0.0)
                alCerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}
