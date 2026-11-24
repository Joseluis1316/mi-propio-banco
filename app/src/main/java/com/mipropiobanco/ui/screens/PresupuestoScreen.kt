@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mipropiobanco.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mipropiobanco.domain.EstadoPago
import com.mipropiobanco.domain.IngresoEsperado
import com.mipropiobanco.domain.Obligacion
import com.mipropiobanco.domain.PresupuestoQuincenal
import com.mipropiobanco.domain.Quincena
import com.mipropiobanco.domain.ResumenPresupuestoMes
import com.mipropiobanco.domain.ResumenQuincena
import com.mipropiobanco.domain.Semaforo
import com.mipropiobanco.domain.TipoObligacion
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.CampoMonto
import com.mipropiobanco.ui.components.EstadoVacio
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.YearMonth

/** Vista: el mes completo o una de las dos quincenas. */
private enum class VistaPlan(val etiqueta: String) { MES("Mes"), Q1("Quincena 1"), Q2("Quincena 2") }

/**
 * Presupuesto del mes por quincenas: qué días me pagan, qué debo pagar en cada quincena
 * y cuánto me queda libre (o cuánto me falta).
 */
@Composable
fun PresupuestoScreen(
    vm: BancoViewModel,
    alAbrirDeuda: (Long) -> Unit,
    alAbrirPrestamo: (Long) -> Unit,
    alMetas: () -> Unit,
    alCalendario: () -> Unit
) {
    var mesTexto by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val mes = YearMonth.parse(mesTexto)
    val hoy = LocalDate.now()
    var vista by rememberSaveable {
        mutableStateOf(if (PresupuestoQuincenal.quincenaDe(hoy) == Quincena.Q1) VistaPlan.Q1 else VistaPlan.Q2)
    }
    val flujo = remember(mesTexto) { vm.presupuestoDe(mes) }
    val resumen by flujo.collectAsState(initial = null)
    var configurandoSalario by remember { mutableStateOf(false) }
    var registrandoIngreso by remember { mutableStateOf<IngresoEsperado?>(null) }
    var pagandoFijo by remember { mutableStateOf<Obligacion?>(null) }

    fun abrir(o: Obligacion) {
        when (o.tipo) {
            TipoObligacion.DEUDA -> alAbrirDeuda(o.refId)
            TipoObligacion.CUOTA_BANCO -> alAbrirPrestamo(o.refId)
            TipoObligacion.META -> alMetas()
            TipoObligacion.FIJO -> if (o.estado != EstadoPago.PAGADO) pagandoFijo = o
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Presupuesto", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = alCalendario) { Text("📅 Calendario") }
            }
        }
        item { SelectorMes(mes) { mesTexto = it.toString() } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VistaPlan.entries.forEach { v ->
                    FilterChip(selected = vista == v, onClick = { vista = v }, label = { Text(v.etiqueta) })
                }
            }
        }

        val r = resumen
        if (r == null) {
            item { Text("Calculando…", style = MaterialTheme.typography.bodyMedium) }
        } else {
            if (r.ingresosPlaneados == 0L) {
                item {
                    Surface(
                        onClick = { configurandoSalario = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("💼 Configura tus días de pago", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Dime cuánto te pagan y qué días (ej. 15 y 30). Así la app sabe con qué plata cuentas en cada quincena.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            when (vista) {
                VistaPlan.MES -> {
                    item { TarjetaMes(r) }
                    item { TarjetaQuincenaCompacta(r.q1) }
                    item { TarjetaQuincenaCompacta(r.q2) }
                }
                VistaPlan.Q1, VistaPlan.Q2 -> {
                    val q = r.de(if (vista == VistaPlan.Q1) Quincena.Q1 else Quincena.Q2)
                    item { TarjetaQuincena(q) }
                    if (q.ingresosEsperados.isNotEmpty()) {
                        item { Text("💰 Días de pago", style = MaterialTheme.typography.titleMedium) }
                        items(q.ingresosEsperados, key = { "i-${it.ocurrencia.plantilla.id}-${it.ocurrencia.indice}" }) { ing ->
                            FilaIngreso(ing) { if (!ing.recibido) registrandoIngreso = ing }
                        }
                    }
                    item { Text("🗓️ Agenda de pagos", style = MaterialTheme.typography.titleMedium) }
                    if (q.obligaciones.isEmpty()) {
                        item {
                            EstadoVacio(
                                "Nada que pagar en esta quincena",
                                "Aquí salen tus deudas, cuotas de Mi banco, gastos fijos y metas con su fecha."
                            )
                        }
                    }
                    items(q.obligaciones, key = { "o-${it.tipo}-${it.refId}-${it.fecha}" }) { o ->
                        FilaObligacion(o) { abrir(o) }
                    }
                    if (q.variables.isNotEmpty()) {
                        item { Text("🛒 Gastos variables", style = MaterialTheme.typography.titleMedium) }
                        items(q.variables, key = { "v-${it.categoria.id}" }) { v ->
                            FilaBarra("${v.categoria.icono} ${v.categoria.nombre}", v.planeado, v.real)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { configurandoSalario = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("💼 Mis pagos (salario por quincenas)")
                }
            }
        }
    }

    if (configurandoSalario) {
        DialogoSalario(
            alCerrar = { configurandoSalario = false },
            alGuardar = { p1, d1, p2, d2 -> vm.configurarSalario(p1, d1, p2, d2); configurandoSalario = false }
        )
    }
    registrandoIngreso?.let { ing ->
        DialogoMontoReal(
            titulo = "¿Ya te pagaron «${ing.nombre}»?",
            detalle = "Esperado: ${Formato.cop(ing.monto)}. Si llegó otro valor (horas extras, descuentos), escríbelo.",
            montoInicial = ing.monto,
            textoBoton = "Registrar ingreso",
            alCerrar = { registrandoIngreso = null },
            alGuardar = { monto -> vm.registrarOcurrencia(ing.ocurrencia, monto); registrandoIngreso = null }
        )
    }
    pagandoFijo?.let { o ->
        val oc = o.ocurrencia
        if (oc != null) {
            DialogoMontoReal(
                titulo = "Pagar «${o.nombre}»",
                detalle = "Vence el ${Formato.fecha(Formato.aMillis(o.fecha))}. Se anota como gasto con el monto que escribas.",
                montoInicial = o.monto,
                textoBoton = "Registrar pago",
                alCerrar = { pagandoFijo = null },
                alGuardar = { monto -> vm.registrarOcurrencia(oc, monto); pagandoFijo = null }
            )
        }
    }
}

@Composable
private fun colorSemaforo(s: Semaforo): Color = when (s) {
    Semaforo.VERDE -> MaterialTheme.colorScheme.primary
    Semaforo.AMARILLO -> MaterialTheme.colorScheme.tertiary
    Semaforo.ROJO -> MaterialTheme.colorScheme.error
}

private fun textoSemaforo(q: ResumenQuincena): String = when (q.semaforo) {
    Semaforo.VERDE -> "🟢 Lo que te entra cubre todo lo de la quincena"
    Semaforo.AMARILLO -> "🟡 Alcanza solo con lo que venía de antes"
    Semaforo.ROJO -> "🔴 No alcanza: te faltan ${Formato.cop(q.faltante)}"
}

@Composable
private fun TarjetaQuincena(q: ResumenQuincena) {
    val color = colorSemaforo(q.semaforo)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${q.quincena.etiqueta} · ${q.desde.dayOfMonth} al ${q.hasta.dayOfMonth}",
                style = MaterialTheme.typography.titleMedium
            )
            Text("Libre de la quincena", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Formato.cop(q.libre), style = MaterialTheme.typography.displaySmall, color = color, maxLines = 1)
            Text(textoSemaforo(q), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Fila("Venía de antes", q.saldoQueViene)
            Fila("+ Entró", q.ingresosReales)
            if (q.porRecibir > 0L) Fila("+ Por recibir", q.porRecibir)
            Fila("− Ya salió", q.salidasReales)
            Fila("− Pendiente por pagar", q.pendiente)
            Text(
                "Pagos de la quincena: ${Formato.cop(q.totalObligaciones)} · pagado ${Formato.cop(q.pagado)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TarjetaQuincenaCompacta(q: ResumenQuincena) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(q.quincena.etiqueta, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(Formato.cop(q.libre), fontWeight = FontWeight.Bold, color = colorSemaforo(q.semaforo))
            }
            Fila("Ingresos planeados / reales", q.ingresosPlaneados, q.ingresosReales)
            Fila("Pagos con fecha (pendiente)", q.totalObligaciones, q.pendiente)
            Text(textoSemaforo(q), style = MaterialTheme.typography.bodySmall, color = colorSemaforo(q.semaforo))
        }
    }
}

@Composable
private fun TarjetaMes(r: ResumenPresupuestoMes) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mes completo", style = MaterialTheme.typography.titleMedium)
            Fila("Ingresos planeados", r.ingresosPlaneados)
            Fila("Ingresos reales", r.ingresosReales)
            Fila("Asignado (pagos + variables + metas)", r.asignado)
            val color = when {
                r.porAsignar == 0L -> MaterialTheme.colorScheme.primary
                r.porAsignar > 0L -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
            Row {
                Text("Por asignar", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(Formato.cop(r.porAsignar), color = color, fontWeight = FontWeight.Bold)
            }
            Text(
                when {
                    r.ingresosPlaneados == 0L -> "Configura tus días de pago para planear el mes."
                    r.porAsignar > 0L -> "Te sobran ${Formato.cop(r.porAsignar)} sin destino: ponlos en una meta o en Mi banco."
                    r.porAsignar < 0L -> "Planeaste ${Formato.cop(-r.porAsignar)} más de lo que vas a ganar."
                    else -> "Cada peso tiene destino. 👌"
                },
                style = MaterialTheme.typography.bodySmall
            )
            Fila("Ahorro real (metas y Mi banco)", r.ahorroReal)
            Text("Tasa de ahorro: ${r.tasaAhorro}%", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Fila(texto: String, valor: Long, segundo: Long? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (segundo == null) Formato.cop(valor) else "${Formato.cop(valor)} / ${Formato.cop(segundo)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FilaIngreso(i: IngresoEsperado, alPulsar: () -> Unit) {
    Surface(onClick = alPulsar, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(String.format("%02d", i.fecha.dayOfMonth), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(i.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (i.recibido) "Recibido ✓" else "Toca cuando te paguen",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i.recibido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("+" + Formato.cop(i.monto), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FilaObligacion(o: Obligacion, alPulsar: () -> Unit) {
    val (texto, color) = when (o.estado) {
        EstadoPago.PAGADO -> "Pagado ✓" to MaterialTheme.colorScheme.primary
        EstadoPago.VENCIDO -> "Vencido" to MaterialTheme.colorScheme.error
        EstadoPago.PENDIENTE -> "Pendiente" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = alPulsar,
        shape = RoundedCornerShape(16.dp),
        color = if (o.estado == EstadoPago.VENCIDO) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(String.format("%02d", o.fecha.dayOfMonth), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("${o.tipo.icono} ${o.nombre}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${o.tipo.etiqueta} · $texto", style = MaterialTheme.typography.labelSmall, color = color)
            }
            Text(Formato.cop(o.monto), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FilaBarra(texto: String, planeado: Long, real: Long) {
    val fraccion = if (planeado > 0L) (real.toFloat() / planeado).coerceIn(0f, 1f) else 0f
    val pasado = real > planeado
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                "${Formato.cop(real)} de ${Formato.cop(planeado)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (pasado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                Modifier.fillMaxWidth(fraccion).height(6.dp).clip(RoundedCornerShape(50))
                    .background(if (pasado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun DialogoSalario(alCerrar: () -> Unit, alGuardar: (Long, Int, Long, Int) -> Unit) {
    var mensual by remember { mutableStateOf("") }
    var dia1 by remember { mutableStateOf("15") }
    var dia2 by remember { mutableStateOf("30") }
    val total = mensual.toLongOrNull() ?: 0L
    val (p1, p2) = PresupuestoQuincenal.partir(total)
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("💼 Mis pagos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Salario quincenal: pon lo que te pagan al mes y los dos días de pago.", style = MaterialTheme.typography.bodySmall)
                CampoMonto(valor = mensual, alCambiar = { mensual = it }, etiqueta = "Salario mensual (neto)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = dia1, onValueChange = { dia1 = it.filter(Char::isDigit).take(2) },
                        label = { Text("Día 1er pago") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = dia2, onValueChange = { dia2 = it.filter(Char::isDigit).take(2) },
                        label = { Text("Día 2º pago") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                if (total > 0L) {
                    Text("Se crean dos ingresos fijos: ${Formato.cop(p1)} y ${Formato.cop(p2)}. Puedes editarlos en Plantillas.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val d1 = dia1.toIntOrNull() ?: 0
            val d2 = dia2.toIntOrNull() ?: 0
            FilledTonalButton(
                onClick = { alGuardar(p1, d1, p2, d2) },
                enabled = total > 0L && d1 in 1..31 && d2 in 1..31
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoMontoReal(
    titulo: String,
    detalle: String,
    montoInicial: Long,
    textoBoton: String,
    alCerrar: () -> Unit,
    alGuardar: (Long) -> Unit
) {
    var monto by remember { mutableStateOf(montoInicial.toString()) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detalle, style = MaterialTheme.typography.bodySmall)
                CampoMonto(valor = monto, alCambiar = { monto = it }, etiqueta = "Monto")
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { alGuardar(monto.toLongOrNull() ?: 0L) }, enabled = (monto.toLongOrNull() ?: 0L) > 0L) {
                Text(textoBoton)
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}
