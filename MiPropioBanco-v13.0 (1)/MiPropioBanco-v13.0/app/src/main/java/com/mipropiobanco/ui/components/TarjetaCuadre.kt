package com.mipropiobanco.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mipropiobanco.data.local.MedioPagoEntity
import com.mipropiobanco.domain.Cuadre
import com.mipropiobanco.util.Formato

/** Qué fue la plata que se gastó sin que se supiera de dónde salió. */
enum class OrigenFaltante { INGRESO, PRESTAMO, YA_LA_TENIA }

/**
 * Cuadre del mes: muestra de dónde vino y a dónde fue cada peso.
 * Si hay cuentas en negativo, pregunta de dónde salió esa plata y lo arregla en un toque.
 */
@Composable
fun TarjetaCuadre(
    c: Cuadre,
    titulo: String,
    alCorregirInicio: (Long, OrigenFaltante, String) -> Unit,
    alExplicar: (MedioPagoEntity, Long, OrigenFaltante, String) -> Unit
) {
    var explicando by remember { mutableStateOf<Pair<MedioPagoEntity, Long>?>(null) }
    var corrigiendoInicio by remember { mutableStateOf(false) }
    val verde = MaterialTheme.colorScheme.primary
    val rojo = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(
                "Cada peso tiene que venir de algún lado: lo ganaste, te lo prestaron o lo sacaste de Mi banco.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Fila("Empezaste con", c.inicio, null)
            TextButton(onClick = { corrigiendoInicio = true }) {
                Text("¿No empezaste con eso? Corrígelo aquí", style = MaterialTheme.typography.labelMedium)
            }
            if (c.ganado > 0L) Fila("💪 Ganaste", c.ganado, verde, "+")
            if (c.prestado > 0L) Fila("🤝 Te prestaron (es deuda)", c.prestado, verde, "+")
            if (c.deMiBanco > 0L) Fila("🏦 Sacaste de Mi banco", c.deMiBanco, verde, "+")
            if (c.gastado > 0L) Fila("🛒 Gastaste", c.gastado, rojo, "−")
            if (c.pagoDeudas > 0L) Fila("💳 Pagaste deudas", c.pagoDeudas, rojo, "−")
            if (c.ahorrado > 0L) Fila("🎯 Ahorraste (Mi banco y metas)", c.ahorrado, rojo, "−")
            HorizontalDivider()
            Fila("Te queda en cuentas", c.final, null, destacado = true)

            if (c.sinOrigen.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "⚠️ Gastaste ${Formato.cop(c.totalSinOrigen)} que no se sabe de dónde salió",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        c.sinOrigen.forEach { (medio, saldo) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${medio.icono} ${medio.nombre} quedó en ${Formato.cop(saldo)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { explicando = medio to -saldo }) { Text("¿Qué fue?") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (corrigiendoInicio) {
        var real by remember { mutableStateOf("") }
        var pidiendoNombre by remember { mutableStateOf(false) }
        var acreedor by remember { mutableStateOf("") }
        val hayValor = real.toLongOrNull() != null
        val realV: Long = real.toLongOrNull() ?: 0L
        val falta: Long = if (hayValor) c.inicio - realV else 0L
        AlertDialog(
            onDismissRequest = { corrigiendoInicio = false },
            title = { Text("¿Con cuánto empezaste el mes?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "La app cree que empezaste con ${Formato.cop(c.inicio)} sumando todas tus cuentas y el efectivo. Escribe lo que tenías de verdad el día 1:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    CampoMonto(valor = real, alCambiar = { real = it }, etiqueta = "Lo que tenía el día 1")
                    if (hayValor && falta > 0L) {
                        Text(
                            "Entonces ${Formato.cop(falta)} de lo que gastaste salió de algún lado que no está anotado. ¿Qué fue?",
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!pidiendoNombre) {
                            OutlinedButton(onClick = { alCorregirInicio(realV, OrigenFaltante.INGRESO, ""); corrigiendoInicio = false }, modifier = Modifier.fillMaxWidth()) {
                                Text("💪 Ingresos que no anoté")
                            }
                            OutlinedButton(onClick = { pidiendoNombre = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("🤝 Alguien me prestó (es deuda)")
                            }
                            Text(
                                "Si crees que hay gastos repetidos, revisa primero 🧹 Limpiar en Por revisar.",
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            OutlinedTextField(
                                value = acreedor, onValueChange = { acreedor = it },
                                label = { Text("¿Quién te prestó?") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (hayValor && falta < 0L) {
                        Text(
                            "Tenías ${Formato.cop(-falta)} más de lo que la app cree: entonces hay gastos que no están anotados. Guardar ajusta el punto de partida y la diferencia queda como gasto sin detalle.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                if (hayValor && (falta < 0L || pidiendoNombre)) {
                    FilledTonalButton(onClick = {
                        alCorregirInicio(realV, if (pidiendoNombre) OrigenFaltante.PRESTAMO else OrigenFaltante.YA_LA_TENIA, acreedor)
                        corrigiendoInicio = false
                    }) { Text("Guardar") }
                }
            },
            dismissButton = { TextButton(onClick = { corrigiendoInicio = false }) { Text("Cancelar") } }
        )
    }

    explicando?.let { (medio, monto) ->
        var acreedor by remember { mutableStateOf("") }
        var pidiendoNombre by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { explicando = null },
            title = { Text("¿De dónde salieron ${Formato.cop(monto)}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "En ${medio.nombre} se gastó más de lo que entró. Elige qué pasó y la app lo cuadra:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!pidiendoNombre) {
                        OutlinedButton(onClick = { alExplicar(medio, monto, OrigenFaltante.INGRESO, ""); explicando = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("💪 Fue un ingreso que no anoté")
                        }
                        OutlinedButton(onClick = { pidiendoNombre = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("🤝 Alguien me prestó (es deuda)")
                        }
                        OutlinedButton(onClick = { alExplicar(medio, monto, OrigenFaltante.YA_LA_TENIA, ""); explicando = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("💼 Ya la tenía antes de empezar a anotar")
                        }
                    } else {
                        OutlinedTextField(
                            value = acreedor,
                            onValueChange = { acreedor = it },
                            label = { Text("¿Quién te prestó?") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (pidiendoNombre) {
                    FilledTonalButton(onClick = {
                        alExplicar(medio, monto, OrigenFaltante.PRESTAMO, acreedor)
                        explicando = null
                    }) { Text("Crear ingreso y deuda") }
                }
            },
            dismissButton = { TextButton(onClick = { explicando = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Fila(texto: String, valor: Long, color: Color?, signo: String = "", destacado: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            texto,
            style = if (destacado) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            signo + Formato.cop(valor),
            style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (destacado) FontWeight.Bold else FontWeight.Medium,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
