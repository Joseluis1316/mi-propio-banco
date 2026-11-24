@file:OptIn(ExperimentalMaterial3Api::class)

package com.mipropiobanco.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.domain.DeudaConAbonos
import com.mipropiobanco.domain.PlanBolaNieve
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.BarraProgreso
import com.mipropiobanco.ui.components.CampoEntero
import com.mipropiobanco.ui.components.CampoMonto
import com.mipropiobanco.ui.components.CampoTasa
import com.mipropiobanco.ui.components.Cronometro
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.EstadoVacio
import com.mipropiobanco.ui.components.FilaResumen
import com.mipropiobanco.ui.components.rememberAhora
import com.mipropiobanco.util.Formato
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val formatoMes = DateTimeFormatter.ofPattern("MMMM yyyy", Formato.localeCO)

/** Mes calendario en que se liquida algo, contando el mes 1 como el actual. */
fun mesDelPlan(mes: Int): String = YearMonth.now().plusMonths((mes - 1).toLong()).format(formatoMes)

/** Mes en que se liquida una deuda: el mes 1 del plan es el de su próximo pago. */
fun mesLiquidacionDe(d: DeudaConAbonos, mes: Int): String =
    YearMonth.from(d.proximoVencimiento(java.time.LocalDate.now())).plusMonths((mes - 1).toLong()).format(formatoMes)

@Composable
fun DeudasScreen(vm: BancoViewModel, alAbrir: (Long) -> Unit) {
    val deudas by vm.deudas.collectAsStateWithLifecycle()
    val plan by vm.planDeudas.collectAsStateWithLifecycle()
    val presupuesto by vm.presupuestoDeudas.collectAsStateWithLifecycle()
    val ahora = rememberAhora()
    var editarPresupuesto by remember { mutableStateOf(false) }
    var nuevaDeuda by remember { mutableStateOf(false) }
    val pagadas = deudas.filter { it.pagada }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Encabezado("Deudas · Bola de nieve")
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    TarjetaPlan(plan, presupuesto, alEditarPresupuesto = { editarPresupuesto = true })
                }
                if (plan.orden.isEmpty()) {
                    item {
                        EstadoVacio(
                            if (pagadas.isEmpty()) "Sin deudas registradas" else "¡Estás libre de deudas!",
                            "Agrega cada deuda con su saldo, pago mínimo y día de pago. La app ordena el ataque: " +
                                "la más pequeña primero, mínimos en las demás, y cuando liquidas una su pago se suma a la siguiente."
                        )
                    }
                } else {
                    item {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                                Text("¿A quién le debo?", style = MaterialTheme.typography.titleMedium)
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                                com.mipropiobanco.ui.components.GraficaDona(
                                    plan.orden.mapIndexed { i, d ->
                                        com.mipropiobanco.ui.components.Porcion(
                                            d.deuda.acreedor, d.saldo,
                                            com.mipropiobanco.ui.components.PaletaGraficas[(i + 3) % com.mipropiobanco.ui.components.PaletaGraficas.size]
                                        )
                                    },
                                    "Debes"
                                )
                            }
                        }
                    }
                    item {
                        Text("Orden de ataque", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(plan.orden, key = { it.deuda.id }) { d ->
                        TarjetaDeuda(
                            posicion = plan.orden.indexOf(d) + 1,
                            item = d,
                            plan = plan,
                            ahora = ahora,
                            alAbrir = { alAbrir(d.deuda.id) }
                        )
                    }
                }
                if (pagadas.isNotEmpty()) {
                    item {
                        Text("Deudas pagadas ✓", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(pagadas, key = { it.deuda.id }) { d ->
                        Card(onClick = { alAbrir(d.deuda.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp)) {
                                Text(d.deuda.acreedor, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Text(
                                    "Pagado ${Formato.cop(d.totalAbonado)}",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nuevaDeuda = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nueva deuda") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (editarPresupuesto) {
        DialogoPresupuesto(
            actual = presupuesto,
            sumaMinimos = plan.sumaMinimos,
            alGuardar = { vm.cambiarPresupuestoDeudas(it) },
            alCerrar = { editarPresupuesto = false }
        )
    }
    if (nuevaDeuda) {
        DialogoDeuda(
            existente = null,
            alGuardar = { acreedor, saldo, minimo, tasa, dia, proximo ->
                vm.guardarDeuda(null, acreedor, saldo, minimo, tasa, dia, proximo) { nuevaDeuda = false }
            },
            alCerrar = { nuevaDeuda = false }
        )
    }
}

@Composable
private fun TarjetaPlan(plan: PlanBolaNieve, presupuesto: Long, alEditarPresupuesto: () -> Unit) {
    val total = plan.orden.sumOf { it.saldo }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Total que debes", style = MaterialTheme.typography.labelLarge)
            Text(Formato.cop(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            FilaResumen("Presupuesto mensual para deudas", Formato.cop(presupuesto))
            FilaResumen("Suma de pagos mínimos", Formato.cop(plan.sumaMinimos))
            if (plan.orden.isNotEmpty()) {
                if (presupuesto < plan.sumaMinimos) {
                    Text(
                        "⚠ Tu presupuesto no cubre los mínimos. El plan usa al menos ${Formato.cop(plan.sumaMinimos)}.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    FilaResumen("Bola de nieve extra", Formato.cop(presupuesto - plan.sumaMinimos))
                }
                val meses = plan.mesesTotales
                if (meses != null) {
                    FilaResumen("Libre de deudas en", "$meses meses (${mesDelPlan(meses)})", destacado = true)
                    FilaResumen("Intereses que pagarás (aprox.)", Formato.cop(plan.interesesProyectados))
                } else {
                    Text(
                        "⚠ Con este presupuesto los intereses crecen más rápido que tus pagos. Súbelo.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = alEditarPresupuesto, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Text("  Cambiar presupuesto mensual")
            }
        }
    }
}

@Composable
private fun TarjetaDeuda(
    posicion: Int,
    item: DeudaConAbonos,
    plan: PlanBolaNieve,
    ahora: Long,
    alAbrir: () -> Unit
) {
    val esObjetivo = plan.objetivoId == item.deuda.id
    val pagoMes = plan.pagoEsteMes[item.deuda.id] ?: 0L
    val estado = item.estado(ahora)
    Card(
        onClick = alAbrir,
        modifier = Modifier.fillMaxWidth(),
        colors = if (esObjetivo) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (esObjetivo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (esObjetivo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$posicion", fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        item.deuda.acreedor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (esObjetivo) "🎯 Objetivo: todo el excedente va aquí" else "Paga solo el mínimo por ahora",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Text(Formato.cop(item.saldo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            BarraProgreso(item.progreso)
            FilaResumen("Paga este mes", Formato.cop(pagoMes), destacado = true)
            FilaResumen("Pago mínimo · día de pago", "${Formato.cop(item.deuda.pagoMinimo)} · día ${item.deuda.diaPago}")
            plan.mesLiquidacion[item.deuda.id]?.let { mes ->
                FilaResumen("La liquidas en", mesLiquidacionDe(item, mes))
            }
            if (estado != null) {
                Cronometro(
                    titulo = if (estado.atrasado) "Atrasado ${estado.diasAtraso} ${if (estado.diasAtraso == 1L) "día" else "días"}"
                    else "Próximo pago: ${Formato.fecha(estado.vence)}",
                    detalle = if (estado.atrasado) "Paga ya para evitar más intereses de mora" else "Faltan ${Formato.duracion(estado.restanteMs)}",
                    atrasado = estado.atrasado
                )
            }
        }
    }
}

@Composable
private fun DialogoPresupuesto(actual: Long, sumaMinimos: Long, alGuardar: (Long) -> Unit, alCerrar: () -> Unit) {
    var valor by rememberSaveable { mutableStateOf(if (actual > 0L) actual.toString() else sumaMinimos.toString()) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Presupuesto mensual para deudas") },
        text = {
            Column {
                Text(
                    "Lo que puedes pagar en total cada mes entre todas tus deudas. Lo que sobre después de " +
                        "los mínimos (${Formato.cop(sumaMinimos)}) es la bola de nieve.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                CampoMonto(valor = valor, alCambiar = { valor = it }, etiqueta = "Presupuesto mensual")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                alGuardar(valor.toLongOrNull() ?: 0L)
                alCerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}

/** Crear o editar una deuda. Al editar, el saldo que escribas es el saldo actual. */
@Composable
fun DialogoDeuda(
    existente: DeudaConAbonos?,
    alGuardar: (acreedor: String, saldo: Long, minimo: Long, tasaEA: Double, dia: Int, proximoPago: Long) -> Unit,
    alCerrar: () -> Unit
) {
    var acreedor by rememberSaveable { mutableStateOf(existente?.deuda?.acreedor.orEmpty()) }
    var saldo by rememberSaveable { mutableStateOf(existente?.saldo?.toString().orEmpty()) }
    var minimo by rememberSaveable { mutableStateOf(existente?.deuda?.pagoMinimo?.toString().orEmpty()) }
    var tasa by rememberSaveable { mutableStateOf(existente?.deuda?.tasaEA?.let { Formato.decimal(it) } ?: "0") }
    var proximo by rememberSaveable {
        mutableStateOf(
            existente?.let { Formato.aMillis(it.proximoVencimiento(java.time.LocalDate.now())) }
                ?: Formato.aMillis(java.time.LocalDate.now().plusMonths(1))
        )
    }
    val dia = Formato.aLocalDate(proximo).dayOfMonth.toString()

    val saldoV = saldo.toLongOrNull() ?: 0L
    val minimoV = minimo.toLongOrNull() ?: 0L
    val tasaV = Formato.parsearDecimal(tasa)
    val diaV = dia.toIntOrNull() ?: 0
    val valido = acreedor.isNotBlank() && saldoV > 0L && minimoV > 0L &&
        tasaV != null && tasaV in 0.0..300.0 && diaV in 1..31

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(if (existente == null) "Nueva deuda" else "Editar deuda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = acreedor,
                    onValueChange = { acreedor = it.take(40) },
                    label = { Text("¿A quién le debes?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CampoMonto(valor = saldo, alCambiar = { saldo = it }, etiqueta = "Saldo actual")
                CampoMonto(valor = minimo, alCambiar = { minimo = it }, etiqueta = "Pago mínimo mensual")
                CampoTasa(
                    valor = tasa,
                    alCambiar = { tasa = it },
                    etiqueta = "Tasa de interés",
                    apoyo = "Si es sin intereses (familia, amigos), deja 0"
                )
                com.mipropiobanco.ui.components.SelectorFecha("Próximo pago", proximo, { proximo = it })
                Text(
                    "Después se repite el día $dia de cada mes. Cuando pagas la cuota, la fecha pasa sola al mes siguiente.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valido, onClick = {
                alGuardar(acreedor, saldoV, minimoV, tasaV ?: 0.0, diaV, proximo)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}
