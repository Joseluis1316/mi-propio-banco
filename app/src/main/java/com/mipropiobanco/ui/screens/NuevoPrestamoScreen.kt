@file:OptIn(ExperimentalMaterial3Api::class)

package com.mipropiobanco.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.domain.Amortizacion
import com.mipropiobanco.domain.Periodicidad
import com.mipropiobanco.domain.Tasas
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.CampoEntero
import com.mipropiobanco.ui.components.CampoMonto
import com.mipropiobanco.ui.components.CampoTasa
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.EncabezadoTabla
import com.mipropiobanco.ui.components.FilaResumen
import com.mipropiobanco.ui.components.FilaTabla
import com.mipropiobanco.ui.components.SelectorFecha
import com.mipropiobanco.util.Formato

/** Regla 1 del reglamento: no prestar más del 70 % de lo disponible en la bóveda. */
private const val LIMITE_RECOMENDADO = 0.70

@Composable
fun NuevoPrestamoScreen(vm: BancoViewModel, alVolver: () -> Unit, alCrear: (Long) -> Unit) {
    val resumen by vm.resumen.collectAsStateWithLifecycle()
    val tipos by vm.tipos.collectAsStateWithLifecycle()

    var concepto by rememberSaveable { mutableStateOf("") }
    var monto by rememberSaveable { mutableStateOf("") }
    var tipoNombre by rememberSaveable { mutableStateOf("") }
    var tasa by rememberSaveable { mutableStateOf("") }
    var tasaMora by rememberSaveable { mutableStateOf("") }
    var periodicidad by rememberSaveable { mutableStateOf(Periodicidad.MENSUAL) }
    var cuotas by rememberSaveable { mutableStateOf("12") }
    var fechaDesembolso by rememberSaveable { mutableStateOf(Formato.hoyMillis()) }
    var menuTipos by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf(false) }

    // Precarga el primer tipo (tasa de usura de consumo) cuando cargan los tipos.
    LaunchedEffect(tipos) {
        if (tipoNombre.isEmpty() && tipos.isNotEmpty()) {
            val t = tipos.first()
            tipoNombre = t.nombre
            tasa = Formato.decimal(t.tasaEA)
            tasaMora = Formato.decimal(t.tasaMoraEA)
        }
    }

    val montoValor = monto.toLongOrNull() ?: 0L
    val cuotasValor = cuotas.toIntOrNull() ?: 0
    val tasaValor = Formato.parsearDecimal(tasa)
    val moraValor = Formato.parsearDecimal(tasaMora)

    val errorMonto = if (montoValor > resumen.disponible) {
        "Supera el disponible en bóveda (${Formato.cop(resumen.disponible)})"
    } else null
    val avisoMonto = if (montoValor > 0L && errorMonto == null && montoValor > resumen.disponible * LIMITE_RECOMENDADO) {
        "⚠ Supera el 70% de la bóveda: rompe la regla de reserva"
    } else "Disponible en bóveda: ${Formato.cop(resumen.disponible)}"
    val errorCuotas = if (cuotas.isNotEmpty() && cuotasValor !in 1..1000) "Entre 1 y 1000 cuotas" else null
    val errorTasa = if (tasaValor == null || tasaValor < 0.0 || tasaValor > 300.0) "Tasa entre 0% y 300% E.A." else null
    val errorMora = if (moraValor == null || moraValor < 0.0 || moraValor > 300.0) "Tasa entre 0% y 300% E.A." else null

    val valido = concepto.isNotBlank() && montoValor > 0L && errorMonto == null && cuotasValor in 1..1000 &&
        tasaValor != null && errorTasa == null && moraValor != null && errorMora == null

    val tasaPeriodo = Tasas.periodoDesdeEA(tasaValor ?: 0.0, periodicidad)
    val tabla = remember(montoValor, cuotasValor, tasaPeriodo, valido) {
        if (valido) Amortizacion.tabla(montoValor, cuotasValor, tasaPeriodo) else emptyList()
    }
    val desembolso = Formato.aLocalDate(fechaDesembolso)
    val totalIntereses = tabla.sumOf { it.interes }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Nuevo autopréstamo", alVolver = alVolver)
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
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

                    ExposedDropdownMenuBox(expanded = menuTipos, onExpandedChange = { menuTipos = it }) {
                        OutlinedTextField(
                            value = tipoNombre.ifEmpty { "Personalizado" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de préstamo (tasa de usura)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuTipos) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = menuTipos, onDismissRequest = { menuTipos = false }) {
                            tipos.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text("${t.nombre} · ${Formato.tasa(t.tasaEA)} E.A.") },
                                    onClick = {
                                        tipoNombre = t.nombre
                                        tasa = Formato.decimal(t.tasaEA)
                                        tasaMora = Formato.decimal(t.tasaMoraEA)
                                        menuTipos = false
                                    }
                                )
                            }
                        }
                    }

                    CampoTasa(
                        valor = tasa,
                        alCambiar = { tasa = it },
                        etiqueta = "Tasa de interés",
                        error = errorTasa,
                        apoyo = "Equivale a ${Formato.tasa(tasaPeriodo)} por ${periodicidad.singular}"
                    )
                    CampoTasa(
                        valor = tasaMora,
                        alCambiar = { tasaMora = it },
                        etiqueta = "Tasa de mora (por atraso)",
                        error = errorMora,
                        apoyo = "Se cobra por cada día de atraso sobre la cuota vencida"
                    )

                    Text("Frecuencia de las cuotas", style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Periodicidad.entries.forEach { p ->
                            FilterChip(
                                selected = periodicidad == p,
                                onClick = { periodicidad = p },
                                label = { Text(p.etiqueta) }
                            )
                        }
                    }
                    CampoEntero(
                        valor = cuotas,
                        alCambiar = { cuotas = it },
                        etiqueta = "Plazo: número de cuotas (${periodicidad.plural})",
                        error = errorCuotas,
                        apoyo = if (cuotasValor > 0) "Una cuota cada ${periodicidad.singular}, ${periodicidad.cantidad(cuotasValor)} en total" else null
                    )
                    SelectorFecha("Desembolso", fechaDesembolso, { fechaDesembolso = it })
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
                            FilaResumen("Cuota fija cada ${periodicidad.singular}", Formato.cop(tabla.first().cuota), destacado = true)
                            FilaResumen("Tasa por ${periodicidad.singular}", Formato.tasa(tasaPeriodo))
                            FilaResumen("Autointereses que ganará tu banco", Formato.cop(totalIntereses))
                            FilaResumen("Total que te devolverás", Formato.cop(montoValor + totalIntereses))
                            FilaResumen("Primera cuota vence", Formato.fecha(periodicidad.vencimiento(desembolso, 1)))
                            FilaResumen("Última cuota vence", Formato.fecha(periodicidad.vencimiento(desembolso, cuotasValor)))
                            FilaResumen("Bóveda después del desembolso", Formato.cop(resumen.disponible - montoValor))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Tabla de amortización (pesos)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    EncabezadoTabla()
                }
                items(tabla, key = { it.numero }) { fila ->
                    FilaTabla(fila, vence = periodicidad.vencimiento(desembolso, fila.numero))
                }
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

    if (confirmar && tabla.isNotEmpty() && tasaValor != null && moraValor != null) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Aprobar autopréstamo?") },
            text = {
                Text(
                    "Saldrán ${Formato.cop(montoValor)} de la bóveda para «${concepto.trim()}». " +
                        "Te comprometes a pagar ${periodicidad.cantidad(cuotasValor)} de cuota de " +
                        "${Formato.cop(tabla.first().cuota)} al ${Formato.tasa(tasaValor)} E.A. " +
                        "Si te atrasas, pagarás mora al ${Formato.tasa(moraValor)} E.A. por día."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    vm.crearPrestamo(
                        concepto, montoValor, cuotasValor, periodicidad,
                        tipoNombre.ifEmpty { "Personalizado" }, tasaValor, moraValor, fechaDesembolso, alCrear
                    )
                }) { Text("Aprobar") }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}
