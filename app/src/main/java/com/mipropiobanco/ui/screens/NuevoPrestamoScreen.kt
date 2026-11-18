package com.mipropiobanco.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.domain.Amortizacion
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.CampoMonto
import com.mipropiobanco.ui.components.EncabezadoTabla
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.FilaResumen
import com.mipropiobanco.ui.components.FilaTabla
import com.mipropiobanco.util.Formato

/** Regla 1 del reglamento: no prestar más del 70 % de lo disponible en la bóveda. */
private const val LIMITE_RECOMENDADO = 0.70

@Composable
fun NuevoPrestamoScreen(vm: BancoViewModel, alVolver: () -> Unit, alCrear: (Long) -> Unit) {
    val resumen by vm.resumen.collectAsStateWithLifecycle()

    var concepto by rememberSaveable { mutableStateOf("") }
    var monto by rememberSaveable { mutableStateOf("") }
    var plazo by rememberSaveable { mutableStateOf("12") }
    var tasa by rememberSaveable { mutableStateOf("1,5") }
    var confirmar by remember { mutableStateOf(false) }

    val montoValor = monto.toLongOrNull() ?: 0L
    val plazoValor = plazo.toIntOrNull() ?: 0
    val tasaValor = Formato.parsearTasa(tasa)

    val errorMonto = when {
        montoValor > resumen.disponible -> "Supera el disponible en bóveda (${Formato.cop(resumen.disponible)})"
        else -> null
    }
    val avisoMonto = when {
        montoValor > 0L && errorMonto == null && montoValor > resumen.disponible * LIMITE_RECOMENDADO ->
            "⚠ Supera el 70% de la bóveda: rompe la regla de reserva"
        else -> "Disponible en bóveda: ${Formato.cop(resumen.disponible)}"
    }
    val errorPlazo = if (plazo.isNotEmpty() && plazoValor !in 1..120) "Entre 1 y 120 meses" else null
    val errorTasa = if (tasaValor == null || tasaValor < 0.0 || tasaValor > 10.0) "Tasa entre 0 y 10 % mensual" else null

    val valido = concepto.isNotBlank() && montoValor > 0L && errorMonto == null &&
        plazoValor in 1..120 && errorTasa == null && tasaValor != null

    val tabla = remember(montoValor, plazoValor, tasaValor, valido) {
        if (valido && tasaValor != null) Amortizacion.tabla(montoValor, plazoValor, tasaValor) else emptyList()
    }
    val totalIntereses = tabla.sumOf { it.interes }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Nuevo autopréstamo", alVolver = alVolver)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = concepto,
                        onValueChange = { concepto = it.take(60) },
                        label = { Text("Concepto (¿qué vas a financiar?)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CampoMonto(
                        valor = monto,
                        alCambiar = { monto = it },
                        etiqueta = "Monto a prestar",
                        error = errorMonto,
                        apoyo = avisoMonto
                    )
                    OutlinedTextField(
                        value = plazo,
                        onValueChange = { plazo = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Plazo (meses)") },
                        singleLine = true,
                        isError = errorPlazo != null,
                        supportingText = errorPlazo?.let { t -> { Text(t) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tasa,
                        onValueChange = { nuevo ->
                            val limpio = nuevo.filter { it.isDigit() || it == ',' || it == '.' }.take(5)
                            if (limpio.count { it == ',' || it == '.' } <= 1) tasa = limpio
                        },
                        label = { Text("Tasa de interés mensual (%)") },
                        suffix = { Text("% mes") },
                        singleLine = true,
                        isError = errorTasa != null,
                        supportingText = {
                            Text(
                                errorTasa ?: ("Equivale a ${Formato.porcentaje(Amortizacion.tasaEfectivaAnual(tasaValor ?: 0.0))} " +
                                    "efectivo anual · sugerida: 1,5% mes")
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (tabla.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Simulación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            FilaResumen("Cuota fija mensual", Formato.cop(tabla.first().cuota), destacado = true)
                            FilaResumen("Total autointereses que ganará tu banco", Formato.cop(totalIntereses))
                            FilaResumen("Total que te devolverás", Formato.cop(montoValor + totalIntereses))
                            FilaResumen("Bóveda después del desembolso", Formato.cop(resumen.disponible - montoValor))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Tabla de amortización (pesos)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    EncabezadoTabla()
                }
                items(tabla, key = { it.numero }) { fila -> FilaTabla(fila) }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = { confirmar = true },
                enabled = valido,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp)
            ) {
                Text("Aprobar y desembolsar")
            }
        }
    }

    if (confirmar && tabla.isNotEmpty() && tasaValor != null) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Aprobar autopréstamo?") },
            text = {
                Text(
                    "Saldrán ${Formato.cop(montoValor)} de la bóveda para «${concepto.trim()}». " +
                        "Te comprometes a pagar $plazoValor cuotas de ${Formato.cop(tabla.first().cuota)}."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    vm.crearPrestamo(concepto, montoValor, plazoValor, tasaValor, alCrear)
                }) { Text("Aprobar") }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}
