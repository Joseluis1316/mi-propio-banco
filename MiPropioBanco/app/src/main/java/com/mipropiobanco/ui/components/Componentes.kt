@file:OptIn(ExperimentalMaterial3Api::class)

package com.mipropiobanco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mipropiobanco.domain.FilaAmortizacion
import com.mipropiobanco.domain.PrestamoConPagos
import com.mipropiobanco.util.Formato

@Composable
fun Encabezado(
    titulo: String,
    alVolver: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(titulo, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (alVolver != null) {
                IconButton(onClick = alVolver) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        },
        actions = acciones,
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

@Composable
fun TarjetaDato(
    titulo: String,
    valor: Long,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    contenedor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contenido: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    estiloValor: TextStyle = MaterialTheme.typography.titleMedium
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = contenedor, contentColor = contenido)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icono, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text(titulo, style = MaterialTheme.typography.labelLarge)
            Text(
                Formato.cop(valor),
                style = estiloValor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BarraProgreso(progreso: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progreso },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    )
}

@Composable
fun TarjetaPrestamo(item: PrestamoConPagos, alAbrir: () -> Unit, modifier: Modifier = Modifier) {
    val p = item.prestamo
    Card(onClick = alAbrir, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.concepto,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (p.pagado) "Pagado ✓" else "Cuota ${item.cuotasPagadas}/${p.plazoMeses}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Saldo ${Formato.cop(item.saldoPendiente)} de ${Formato.cop(p.monto)}",
                style = MaterialTheme.typography.bodyMedium
            )
            BarraProgreso(item.progreso)
            Row {
                Text(
                    "Cuota ${Formato.cop(p.cuota)} · ${Formato.tasa(p.tasaMensual)} mes",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    Formato.porcentaje(item.progreso * 100.0, 0) + " pagado",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun FilaResumen(etiqueta: String, valor: String, destacado: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            valor,
            style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (destacado) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun EstadoVacio(titulo: String, detalle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(detalle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---------- Tabla de amortización ----------

@Composable
private fun RowScope.Celda(
    texto: String,
    peso: Float,
    negrita: Boolean = false,
    alinear: TextAlign = TextAlign.End,
    color: Color = Color.Unspecified
) {
    Text(
        texto,
        modifier = Modifier.weight(peso),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal,
        textAlign = alinear,
        color = color,
        maxLines = 1
    )
}

@Composable
fun EncabezadoTabla() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Celda("#", 0.5f, negrita = true, alinear = TextAlign.Start)
        Celda("Cuota", 1.2f, negrita = true)
        Celda("Capital", 1.2f, negrita = true)
        Celda("Interés", 1.1f, negrita = true)
        Celda("Saldo", 1.3f, negrita = true)
    }
}

@Composable
fun FilaTabla(fila: FilaAmortizacion, pagada: Boolean = false, fechaPago: String? = null) {
    val fondo = if (fila.numero % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    else Color.Transparent
    Column(
        Modifier
            .fillMaxWidth()
            .background(fondo)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Celda(
                if (pagada) "✓${fila.numero}" else fila.numero.toString(),
                0.5f,
                negrita = pagada,
                alinear = TextAlign.Start,
                color = if (pagada) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
            Celda(Formato.miles(fila.cuota), 1.2f)
            Celda(Formato.miles(fila.capital), 1.2f)
            Celda(Formato.miles(fila.interes), 1.1f, color = MaterialTheme.colorScheme.tertiary)
            Celda(Formato.miles(fila.saldoFinal), 1.3f)
        }
        if (fechaPago != null) {
            Text(
                "Pagada el $fechaPago",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ---------- Campos de entrada ----------

/** Campo de pesos: guarda solo dígitos y los muestra con puntos de miles. */
@Composable
fun CampoMonto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    apoyo: String? = null
) {
    val textoApoyo = error ?: apoyo
    OutlinedTextField(
        value = valor,
        onValueChange = { nuevo -> alCambiar(nuevo.filter { it.isDigit() }.trimStart('0').take(13)) },
        label = { Text(etiqueta) },
        prefix = { Text("$ ") },
        singleLine = true,
        isError = error != null,
        supportingText = textoApoyo?.let { t -> { Text(t) } },
        visualTransformation = MilesVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth()
    )
}

class MilesVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val sb = StringBuilder()
        val origenADestino = IntArray(original.length + 1)
        for (i in original.indices) {
            if (i > 0 && (original.length - i) % 3 == 0) sb.append('.')
            origenADestino[i] = sb.length
            sb.append(original[i])
        }
        origenADestino[original.length] = sb.length
        val salida = sb.toString()
        val destinoAOrigen = IntArray(salida.length + 1)
        var digitos = 0
        for (t in 0..salida.length) {
            destinoAOrigen[t] = digitos
            if (t < salida.length && salida[t] != '.') digitos++
        }
        return TransformedText(
            AnnotatedString(salida),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    origenADestino[offset.coerceIn(0, original.length)]

                override fun transformedToOriginal(offset: Int): Int =
                    destinoAOrigen[offset.coerceIn(0, salida.length)]
            }
        )
    }
}
