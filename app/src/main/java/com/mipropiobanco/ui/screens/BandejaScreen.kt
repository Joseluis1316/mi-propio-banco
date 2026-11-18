@file:OptIn(ExperimentalMaterial3Api::class)

package com.mipropiobanco.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.domain.InterpreteCorreo
import com.mipropiobanco.domain.Propuesta
import com.mipropiobanco.domain.TipoMovimiento
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.EstadoVacio
import com.mipropiobanco.util.Formato
import java.time.LocalDate

/** Movimientos que llegaron por correo (Bancolombia) y esperan tu confirmación. */
@Composable
fun BandejaScreen(vm: BancoViewModel, alVolver: () -> Unit, alComoConectar: () -> Unit) {
    val correos by vm.bandeja.collectAsStateWithLifecycle()
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val medios by vm.medios.collectAsStateWithLifecycle()
    val deudas by vm.deudas.collectAsStateWithLifecycle()
    val correo by vm.correo.collectAsStateWithLifecycle()
    var propuesta by remember { mutableStateOf<Propuesta?>(null) }

    Column(Modifier.fillMaxSize()) {
        Encabezado("📥 Correos por revisar", alVolver = alVolver)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (correo == null) {
                item {
                    EstadoVacio(
                        "Necesitas iniciar sesión",
                        "Los correos del banco llegan a tu nube. Inicia sesión en Ajustes › Cuenta y nube."
                    )
                }
            } else if (correos.isEmpty()) {
                item {
                    EstadoVacio(
                        "Todo al día",
                        "Cuando Bancolombia te envíe una alerta (pagaste, compraste, transferiste, recibiste, retiraste), " +
                            "aparecerá aquí en unos minutos para que la confirmes."
                    )
                }
                item {
                    TextButton(onClick = alComoConectar) { Text("¿Cómo conectar mi Gmail?") }
                }
            }
            items(correos, key = { it.id }) { c ->
                val m = remember(c.id, c.texto) {
                    InterpreteCorreo.interpretar(c.id, c.texto, Formato.aLocalDate(c.fecha.takeIf { it > 0L } ?: Formato.hoyMillis()))
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (m == null) {
                            Text("No entendí este correo", fontWeight = FontWeight.SemiBold)
                            Text(c.texto.take(220), style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { vm.descartarCorreo(c.id) }) { Text("Descartar") }
                            }
                        } else {
                            val duplicado = remember(m, movimientos) { InterpreteCorreo.buscarDuplicado(m, movimientos) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when {
                                        m.esRetiro -> "🏧"
                                        m.esNomina -> "💼"
                                        m.tipo == TipoMovimiento.INGRESO -> "💰"
                                        else -> "💸"
                                    },
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(m.resumen, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(Formato.fecha(m.fecha), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    when (m.tipo) {
                                        TipoMovimiento.INGRESO -> "+"
                                        TipoMovimiento.GASTO -> "−"
                                        TipoMovimiento.TRASLADO -> "🔄 "
                                    } + Formato.cop(m.monto),
                                    fontWeight = FontWeight.Bold,
                                    color = when (m.tipo) {
                                        TipoMovimiento.INGRESO -> MaterialTheme.colorScheme.primary
                                        TipoMovimiento.GASTO -> MaterialTheme.colorScheme.error
                                        TipoMovimiento.TRASLADO -> MaterialTheme.colorScheme.outline
                                    }
                                )
                            }
                            if (m.esRetiro) {
                                Text("Retiro = traslado de tu cuenta a efectivo (no es gasto).", style = MaterialTheme.typography.bodySmall)
                            }
                            if (duplicado != null) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                    Text(
                                        "⚠ Posible duplicado: $duplicado",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { vm.descartarCorreo(c.id) }) {
                                    Text(if (duplicado != null) "Descartar (ya está)" else "Descartar")
                                }
                                FilledTonalButton(onClick = { vm.prepararCorreo(m) { propuesta = it } }) { Text("Revisar") }
                            }
                        }
                    }
                }
            }
        }
    }

    propuesta?.let { p ->
        val activas = deudas.filter { !it.pagada }
        val textoBusqueda = com.mipropiobanco.domain.Interprete.normalizar(p.nota + " " + p.textoOriginal)
        val deudaSugerida = activas.firstOrNull { d ->
            com.mipropiobanco.domain.Interprete.normalizar(d.deuda.acreedor).split(" ").filter { it.length >= 4 }
                .any { textoBusqueda.contains(it) }
        }?.deuda?.id
        DialogoMovimiento(
            existente = null,
            propuesta = p,
            deudas = activas,
            deudaSugerida = deudaSugerida,
            tipoInicial = p.tipo,
            categorias = categorias,
            medios = medios,
            alGuardar = { tipo, monto, cat, medio, fecha, nota, _, _, deudaId, destino ->
                vm.guardarDesdeCorreo(p, tipo, monto, cat, medio, fecha, nota, deudaId, destino)
            },
            alEliminar = null,
            alCancelar = {},
            alCerrar = { propuesta = null }
        )
    }
}

/** Pasos para conectar Gmail con el script (se muestra desde Ajustes y desde la bandeja). */
@Composable
fun InstruccionesGmail(uid: String?, alCopiar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Un script gratuito en tu cuenta de Google revisa cada 15 minutos los correos con la etiqueta MiBanco " +
                "y los envía a esta bandeja. Se configura una sola vez.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("1. En Gmail crea un filtro: de alertasynotificaciones@an.notificacionesbancolombia.com → etiqueta MiBanco.", style = MaterialTheme.typography.bodySmall)
        Text("2. Entra a script.google.com › Nuevo proyecto y pega el archivo scripts/LectorCorreosBancolombia.gs del repositorio.", style = MaterialTheme.typography.bodySmall)
        Text("3. En el script reemplaza TU_CODIGO por tu código de usuario:", style = MaterialTheme.typography.bodySmall)
        Text(uid ?: "Inicia sesión para ver tu código", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        if (uid != null) TextButton(onClick = alCopiar) { Text("Copiar código") }
        Text("4. Ejecuta la función «instalar» y acepta los permisos. Listo.", style = MaterialTheme.typography.bodySmall)
    }
}

@Suppress("unused")
private fun hoy(): LocalDate = LocalDate.now()
