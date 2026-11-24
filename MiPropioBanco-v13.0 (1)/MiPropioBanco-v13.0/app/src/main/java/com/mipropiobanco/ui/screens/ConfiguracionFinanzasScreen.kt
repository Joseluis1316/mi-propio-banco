@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mipropiobanco.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.mipropiobanco.data.local.CategoriaEntity
import com.mipropiobanco.data.local.MedioPagoEntity
import com.mipropiobanco.data.local.PlantillaEntity
import com.mipropiobanco.domain.Periodicidad
import com.mipropiobanco.domain.Recurrencia
import com.mipropiobanco.domain.TipoMovimiento
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.CampoMonto
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.EstadoVacio
import com.mipropiobanco.ui.components.SelectorFecha
import com.mipropiobanco.util.Formato

private val EMOJIS = listOf(
    "🏠", "🛒", "💡", "💧", "🔥", "📱", "🌐", "🚌", "🚕", "⛽", "🚗", "🏍️", "🍔", "☕", "🍺", "🏥", "💊", "🦷",
    "📚", "🎓", "👕", "👟", "💇", "🎉", "🎬", "🎮", "⚽", "✈️", "🧳", "🐾", "🎁", "👶", "👪", "🧹", "🔧", "🛠️",
    "💳", "🏦", "💵", "💰", "💼", "🧾", "📈", "🏘️", "🛍️", "📦", "❤️", "🙏", "⚡", "📲", "🔁", "➕"
)

/** Sugerencias para crear plantillas fijas comunes con un toque. */
private data class Sugerencia(val nombre: String, val tipo: TipoMovimiento, val categoria: String, val periodicidad: Periodicidad)

private val SUGERENCIAS = listOf(
    Sugerencia("Salario", TipoMovimiento.INGRESO, "Salario", Periodicidad.MENSUAL),
    Sugerencia("Salario quincenal", TipoMovimiento.INGRESO, "Salario", Periodicidad.QUINCENAL),
    Sugerencia("Arriendo", TipoMovimiento.GASTO, "Arriendo", Periodicidad.MENSUAL),
    Sugerencia("Servicios públicos", TipoMovimiento.GASTO, "Servicios públicos", Periodicidad.MENSUAL),
    Sugerencia("Plan de celular", TipoMovimiento.GASTO, "Celular e internet", Periodicidad.MENSUAL),
    Sugerencia("Mercado semanal", TipoMovimiento.GASTO, "Mercado", Periodicidad.SEMANAL),
    Sugerencia("Transporte diario", TipoMovimiento.GASTO, "Transporte", Periodicidad.DIARIA),
    Sugerencia("Aporte a mi banco", TipoMovimiento.GASTO, "Otros gastos", Periodicidad.MENSUAL)
)

@Composable
fun ConfiguracionFinanzasScreen(vm: BancoViewModel, alVolver: () -> Unit) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    val titulos = listOf("Plantillas fijas", "Categorías", "Medios de pago")
    Column(Modifier.fillMaxSize()) {
        Encabezado("Plantillas y categorías", alVolver = alVolver)
        TabRow(selectedTabIndex = pestana) {
            titulos.forEachIndexed { i, t ->
                Tab(selected = pestana == i, onClick = { pestana = i }, text = { Text(t, maxLines = 1) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (pestana) {
                0 -> PestanaPlantillas(vm)
                1 -> PestanaCategorias(vm)
                else -> PestanaMedios(vm)
            }
        }
    }
}

// ---------------- Plantillas fijas ----------------

@Composable
private fun PestanaPlantillas(vm: BancoViewModel) {
    val plantillas by vm.plantillas.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val medios by vm.medios.collectAsStateWithLifecycle()
    var editando by remember { mutableStateOf<PlantillaEntity?>(null) }
    var nueva by remember { mutableStateOf<Sugerencia?>(null) }
    var nuevaVacia by remember { mutableStateOf(false) }
    val catMap = categorias.associateBy { it.id }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Ingresos y gastos que se repiten. Los automáticos se anotan solos en cada fecha; " +
                        "los demás aparecen en «Fijos por registrar» para que confirmes el monto.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Text("Crear rápido", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SUGERENCIAS.forEach { s -> AssistChip(onClick = { nueva = s }, label = { Text("+ ${s.nombre}") }) }
                }
            }
            if (plantillas.isEmpty()) {
                item { EstadoVacio("Sin plantillas fijas", "Crea tu salario, arriendo, servicios o cualquier pago que se repita.") }
            }
            items(plantillas, key = { it.id }) { p ->
                val c = catMap[p.categoriaId]
                val esIngreso = p.tipo == TipoMovimiento.INGRESO.name
                val per = Periodicidad.desde(p.periodicidad)
                Card(onClick = { editando = p }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c?.icono ?: "📌", style = MaterialTheme.typography.titleLarge)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(p.nombre, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (p.activa) "Cada ${per.singular} · próxima ${Formato.fecha(Recurrencia.proxima(p))}" +
                                    if (p.automatica) " · automática" else ""
                                else "Pausada",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            (if (esIngreso) "+" else "−") + Formato.cop(p.monto),
                            fontWeight = FontWeight.Bold,
                            color = if (esIngreso) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nuevaVacia = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nueva plantilla") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (editando != null || nueva != null || nuevaVacia) {
        val s = nueva
        DialogoPlantilla(
            existente = editando,
            sugerencia = s,
            categoriaSugerida = s?.let { sug -> categorias.firstOrNull { it.nombre == sug.categoria && it.tipo == sug.tipo.name }?.id },
            categorias = categorias,
            medios = medios,
            vm = vm,
            alCerrar = {
                editando = null
                nueva = null
                nuevaVacia = false
            }
        )
    }
}

@Composable
private fun DialogoPlantilla(
    existente: PlantillaEntity?,
    sugerencia: Sugerencia?,
    categoriaSugerida: Long?,
    categorias: List<CategoriaEntity>,
    medios: List<MedioPagoEntity>,
    vm: BancoViewModel,
    alCerrar: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf(existente?.nombre ?: sugerencia?.nombre.orEmpty()) }
    var tipo by rememberSaveable { mutableStateOf(TipoMovimiento.desde(existente?.tipo ?: sugerencia?.tipo?.name)) }
    var monto by rememberSaveable { mutableStateOf(existente?.monto?.toString().orEmpty()) }
    var categoriaId by rememberSaveable { mutableStateOf(existente?.categoriaId ?: categoriaSugerida ?: 0L) }
    var medioId by rememberSaveable { mutableStateOf(existente?.medioId ?: (medios.getOrNull(1)?.id ?: medios.firstOrNull()?.id ?: 0L)) }
    var periodicidad by rememberSaveable { mutableStateOf(Periodicidad.desde(existente?.periodicidad ?: sugerencia?.periodicidad?.name)) }
    var inicio by rememberSaveable { mutableStateOf(existente?.fechaInicio ?: Formato.hoyMillis()) }
    var automatica by rememberSaveable { mutableStateOf(existente?.automatica ?: false) }
    var activa by rememberSaveable { mutableStateOf(existente?.activa ?: true) }
    var confirmarBorrar by remember { mutableStateOf(false) }

    val montoV = monto.toLongOrNull() ?: 0L
    val valido = nombre.isNotBlank() && montoV > 0L &&
        categorias.any { it.id == categoriaId && it.tipo == tipo.name } && medioId > 0L

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(if (existente == null) "Nueva plantilla fija" else "Editar plantilla") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TipoMovimiento.BASICOS.forEach { t ->
                        FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t.etiqueta) })
                    }
                }
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it.take(40) },
                    label = { Text("Nombre (ej. Arriendo apto)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CampoMonto(valor = monto, alCambiar = { monto = it }, etiqueta = "Monto de cada vez")
                Text("Se repite", style = MaterialTheme.typography.labelLarge)
                SelectorPeriodicidad(periodicidad) { periodicidad = it }
                SelectorFecha("Primera vez", inicio, { inicio = it })
                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                SelectorCategoria(categorias, tipo, categoriaId) { categoriaId = it }
                Text("Medio de pago", style = MaterialTheme.typography.labelLarge)
                SelectorMedio(medios, medioId) { medioId = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Registrar automáticamente")
                        Text(
                            "Si está apagado, te aparece para confirmar el monto",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = automatica, onCheckedChange = { automatica = it })
                }
                if (existente != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Activa", modifier = Modifier.weight(1f))
                        Switch(checked = activa, onCheckedChange = { activa = it })
                    }
                    TextButton(onClick = { confirmarBorrar = true }) {
                        Text("Eliminar plantilla", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (existente == null && Formato.aLocalDate(inicio).isBefore(java.time.LocalDate.now())) {
                    Text(
                        "La primera fecha ya pasó: las repeticiones desde esa fecha quedarán por registrar" +
                            if (automatica) " (y se anotarán solas)." else ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valido, onClick = {
                vm.guardarPlantilla(existente?.id, nombre, tipo, montoV, categoriaId, medioId, periodicidad, inicio, automatica, activa)
                alCerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )

    if (confirmarBorrar && existente != null) {
        DialogoConfirmar(
            titulo = "¿Eliminar «${existente.nombre}»?",
            texto = "Dejará de repetirse. Los movimientos que ya registró se conservan.",
            accion = "Eliminar",
            alConfirmar = {
                vm.eliminarPlantilla(existente)
                alCerrar()
            },
            alCerrar = { confirmarBorrar = false }
        )
    }
}

// ---------------- Categorías ----------------

@Composable
private fun PestanaCategorias(vm: BancoViewModel) {
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    var editando by remember { mutableStateOf<CategoriaEntity?>(null) }
    var nueva by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TipoMovimiento.BASICOS.forEach { tipo ->
                item(key = "t-${tipo.name}") {
                    Text(
                        if (tipo == TipoMovimiento.GASTO) "Gastos" else "Ingresos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(categorias.filter { it.tipo == tipo.name }, key = { it.id }) { c ->
                    Card(onClick = { editando = c }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.icono, style = MaterialTheme.typography.titleLarge)
                            Text(c.nombre, modifier = Modifier.weight(1f).padding(start = 10.dp))
                            if (c.presupuestoMensual > 0L) {
                                Text("Límite ${Formato.cop(c.presupuestoMensual)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nueva = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nueva categoría") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (nueva || editando != null) {
        val c = editando
        var nombre by rememberSaveable { mutableStateOf(c?.nombre.orEmpty()) }
        var tipo by rememberSaveable { mutableStateOf(TipoMovimiento.desde(c?.tipo)) }
        var icono by rememberSaveable { mutableStateOf(c?.icono ?: "📦") }
        var presupuesto by rememberSaveable { mutableStateOf(c?.presupuestoMensual?.takeIf { it > 0L }?.toString().orEmpty()) }
        var confirmarBorrar by remember { mutableStateOf(false) }
        val cerrar = {
            nueva = false
            editando = null
        }
        AlertDialog(
            onDismissRequest = cerrar,
            title = { Text(if (c == null) "Nueva categoría" else "Editar categoría") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TipoMovimiento.BASICOS.forEach { t ->
                            FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t.etiqueta) })
                        }
                    }
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it.take(30) },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SelectorEmoji(icono) { icono = it }
                    if (tipo == TipoMovimiento.GASTO) {
                        CampoMonto(
                            valor = presupuesto,
                            alCambiar = { presupuesto = it },
                            etiqueta = "Presupuesto mensual (opcional)",
                            apoyo = "Te avisa cuando te pases de este valor en el mes"
                        )
                    }
                    if (c != null) {
                        TextButton(onClick = { confirmarBorrar = true }) {
                            Text("Eliminar categoría", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = nombre.isNotBlank(), onClick = {
                    vm.guardarCategoria(c?.id, nombre, tipo, icono, presupuesto.toLongOrNull() ?: 0L)
                    cerrar()
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } }
        )
        if (confirmarBorrar && c != null) {
            DialogoConfirmar(
                titulo = "¿Eliminar «${c.nombre}»?",
                texto = "Solo se puede borrar si no tiene movimientos ni plantillas.",
                accion = "Eliminar",
                alConfirmar = {
                    vm.eliminarCategoria(c)
                    cerrar()
                },
                alCerrar = { confirmarBorrar = false }
            )
        }
    }
}

// ---------------- Medios de pago ----------------

@Composable
private fun PestanaMedios(vm: BancoViewModel) {
    val medios by vm.medios.collectAsStateWithLifecycle()
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val saldos = com.mipropiobanco.domain.calcularSaldos(medios, movimientos)
    var editando by remember { mutableStateOf<MedioPagoEntity?>(null) }
    var nuevo by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    "Efectivo, cuentas, billeteras y tarjetas con las que pagas o recibes dinero.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            items(medios, key = { it.id }) { m ->
                Card(onClick = { editando = m }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.icono, style = MaterialTheme.typography.titleLarge)
                        Text(m.nombre, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        Text(Formato.cop(saldos[m.id] ?: 0L), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nuevo = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nuevo medio") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (nuevo || editando != null) {
        val m = editando
        var nombre by rememberSaveable { mutableStateOf(m?.nombre.orEmpty()) }
        var icono by rememberSaveable { mutableStateOf(m?.icono ?: "💵") }
        var saldo by rememberSaveable { mutableStateOf(m?.let { (saldos[it.id] ?: 0L).takeIf { v -> v > 0L }?.toString() }.orEmpty()) }
        var confirmarBorrar by remember { mutableStateOf(false) }
        val cerrar = {
            nuevo = false
            editando = null
        }
        AlertDialog(
            onDismissRequest = cerrar,
            title = { Text(if (m == null) "Nuevo medio de pago" else "Editar medio de pago") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it.take(30) },
                        label = { Text("Nombre (ej. Bancolombia ahorros)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CampoMonto(
                        valor = saldo,
                        alCambiar = { saldo = it },
                        etiqueta = "Saldo actual (opcional)",
                        apoyo = "Lo que tienes hoy ahí. Desde este valor se suman y restan tus movimientos."
                    )
                    SelectorEmoji(icono) { icono = it }
                    if (m != null) {
                        TextButton(onClick = { confirmarBorrar = true }) {
                            Text("Eliminar medio de pago", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = nombre.isNotBlank(), onClick = {
                    vm.guardarMedio(m?.id, nombre, icono, saldo.toLongOrNull())
                    cerrar()
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } }
        )
        if (confirmarBorrar && m != null) {
            DialogoConfirmar(
                titulo = "¿Eliminar «${m.nombre}»?",
                texto = "Solo se puede borrar si no tiene movimientos ni plantillas.",
                accion = "Eliminar",
                alConfirmar = {
                    vm.eliminarMedio(m)
                    cerrar()
                },
                alCerrar = { confirmarBorrar = false }
            )
        }
    }
}

@Composable
private fun SelectorEmoji(actual: String, alElegir: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ícono: $actual", style = MaterialTheme.typography.labelLarge)
        FlowRow {
            EMOJIS.forEach { e ->
                TextButton(onClick = { alElegir(e) }) {
                    Text(e, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        OutlinedTextField(
            value = actual,
            onValueChange = { if (it.length <= 4) alElegir(it) },
            label = { Text("O escribe un emoji") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
