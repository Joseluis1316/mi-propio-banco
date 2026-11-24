@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mipropiobanco.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.mipropiobanco.domain.Propuesta
import com.mipropiobanco.util.Fotos
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.data.local.CategoriaEntity
import com.mipropiobanco.data.local.MedioPagoEntity
import com.mipropiobanco.data.local.MovimientoEntity
import com.mipropiobanco.data.repository.DatosRepeticion
import com.mipropiobanco.domain.AvancePresupuesto
import com.mipropiobanco.domain.Ocurrencia
import com.mipropiobanco.domain.Periodicidad
import com.mipropiobanco.domain.Recurrencia
import com.mipropiobanco.domain.ResumenMes
import com.mipropiobanco.domain.TipoMovimiento
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.CampoMonto
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.EstadoVacio
import com.mipropiobanco.ui.components.FilaResumen
import com.mipropiobanco.ui.components.SelectorFecha
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val formatoMesTitulo = DateTimeFormatter.ofPattern("MMMM yyyy", Formato.localeCO)
private val formatoDia = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Formato.localeCO)

fun YearMonth.titulo(): String = format(formatoMesTitulo).replaceFirstChar { it.uppercase() }

@Composable
fun SelectorMes(mes: YearMonth, alCambiar: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { alCambiar(mes.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
        }
        Text(
            mes.titulo(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .clickable { alCambiar(YearMonth.now()) },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = { alCambiar(mes.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
        }
    }
}

private enum class FiltroMov(val etiqueta: String) {
    TODOS("Todos"), GASTOS("Gastos"), INGRESOS("Ingresos"), POR_CLASIFICAR("🏷️ Por clasificar")
}

@Composable
fun MovimientosScreen(vm: BancoViewModel, nuevoInicial: String, alConfigurar: () -> Unit, alBandeja: () -> Unit = {}) {
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val medios by vm.medios.collectAsStateWithLifecycle()
    val plantillas by vm.plantillas.collectAsStateWithLifecycle()
    val deudas by vm.deudas.collectAsStateWithLifecycle()
    val deudasActivas = deudas.filter { !it.pagada }
    val bandeja by vm.bandeja.collectAsStateWithLifecycle()
    val puntoDeshacer by vm.puntoDeshacer.collectAsStateWithLifecycle()

    var mesTexto by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val mes = YearMonth.parse(mesTexto)
    var filtro by rememberSaveable { mutableStateOf(FiltroMov.TODOS) }
    var ajustandoSaldos by rememberSaveable { mutableStateOf(false) }
    val extracto by vm.extracto.collectAsStateWithLifecycle()
    if (extracto.isNotEmpty()) {
        DialogoExtracto(
            filas = extracto,
            yaRegistrado = { vm.yaRegistrado(it) },
            alGuardar = { destinos, acreedores -> vm.guardarExtracto(extracto, destinos, acreedores) },
            alCancelar = { vm.descartarExtracto() }
        )
    }
    val reportePdf = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { vm.exportarReporte(it, mes) } }
    var eligiendoInicio by rememberSaveable { mutableStateOf(false) }
    if (eligiendoInicio) {
        DialogoInicio(
            movimientos = movimientos,
            alCerrar = { eligiendoInicio = false },
            alConfirmar = { vm.empezarDesde(it); eligiendoInicio = false }
        )
    }
    if (ajustandoSaldos) {
        DialogoSaldos(
            medios = medios,
            alUnir = { vm.unirEnBancolombia(); ajustandoSaldos = false },
            saldos = com.mipropiobanco.domain.calcularSaldos(medios, movimientos),
            alCerrar = { ajustandoSaldos = false },
            alGuardar = { valores, comoMovimiento -> vm.ajustarSaldos(valores, comoMovimiento); ajustandoSaldos = false }
        )
    }
    var nuevoTipo by rememberSaveable { mutableStateOf(if (nuevoInicial == "GASTO" || nuevoInicial == "INGRESO") nuevoInicial else "") }
    var editando by remember { mutableStateOf<MovimientoEntity?>(null) }
    var registrando by remember { mutableStateOf<Ocurrencia?>(null) }
    var propuesta by remember { mutableStateOf<Propuesta?>(null) }
    var nomina by remember { mutableStateOf<com.mipropiobanco.domain.PropuestaNomina?>(null) }
    var escribir by remember { mutableStateOf(false) }
    var elegirFoto by remember { mutableStateOf(false) }
    val leyendo by vm.leyendoRecibo.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var archivoCamara by remember { mutableStateOf<File?>(null) }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val archivo = archivoCamara
        if (ok && archivo != null) {
            vm.leerRecibo(android.net.Uri.fromFile(archivo), archivo, { propuesta = it }, { nomina = it })
        } else {
            archivo?.delete()
        }
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.leerRecibo(uri, null, { propuesta = it }, { nomina = it })
    }
    val voz = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val texto = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!texto.isNullOrBlank()) propuesta = vm.interpretarFrase(texto)
    }
    val abrirCamara: () -> Unit = {
        val archivo = Fotos.archivoTemporal(contexto)
        archivoCamara = archivo
        camara.launch(Fotos.uriCompartible(contexto, archivo))
    }
    val abrirVoz: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ej.: gasté 45 mil en mercado con Nequi")
        }
        try {
            voz.launch(intent)
        } catch (e: ActivityNotFoundException) {
            escribir = true
        }
    }

    // Accesos directos desde Inicio.
    var atajoUsado by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (atajoUsado) return@LaunchedEffect
        atajoUsado = true
        when (nuevoInicial) {
            "VOZ" -> { nuevoTipo = ""; abrirVoz() }
            "FOTO" -> { nuevoTipo = ""; elegirFoto = true }
            "TEXTO" -> { nuevoTipo = ""; escribir = true }
        }
    }

    val catMap = categorias.associateBy { it.id }
    val medMap = medios.associateBy { it.id }
    val resumen = ResumenMes.de(movimientos, mes)
    val hoy = LocalDate.now()
    val pendientes = plantillas.flatMap { Recurrencia.pendientes(it, hoy) }.sortedBy { it.fecha }
    val avances = categorias
        .filter { it.tipo == TipoMovimiento.GASTO.name }
        .map { AvancePresupuesto(it, resumen.gastoPorCategoria[it.id] ?: 0L) }
        .filter { it.limite > 0L || it.gastado > 0L }
        .sortedByDescending { if (it.limite > 0L) it.fraccion else 0f }
    val catMapPorClasificar = categorias
        .filter { com.mipropiobanco.domain.Interprete.normalizar(it.nombre).contains("por clasificar") }
        .map { it.id }.toSet()
    val delMes = movimientos
        .filter { YearMonth.from(Formato.aLocalDate(it.fecha)) == mes }
        .filter {
            when (filtro) {
                FiltroMov.TODOS -> true
                FiltroMov.GASTOS -> it.tipo == TipoMovimiento.GASTO.name
                FiltroMov.INGRESOS -> it.tipo == TipoMovimiento.INGRESO.name
                FiltroMov.POR_CLASIFICAR -> catMapPorClasificar.contains(it.categoriaId)
            }
        }
    val porDia = delMes.groupBy { Formato.aLocalDate(it.fecha) }.toSortedMap(compareByDescending { it })

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Encabezado("Ingresos y gastos", acciones = {
                IconButton(onClick = alConfigurar) {
                    Icon(Icons.Filled.Tune, contentDescription = "Categorías, medios y plantillas")
                }
            })
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { SelectorMes(mes) { mesTexto = it.toString() } }
                if (bandeja.isNotEmpty()) {
                    item {
                        Card(
                            onClick = alBandeja,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Text(
                                "📥 Tienes ${bandeja.size} movimientos de tus correos por revisar",
                                modifier = Modifier.padding(14.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    item {
                        androidx.compose.material3.OutlinedButton(onClick = alBandeja, modifier = Modifier.fillMaxWidth()) {
                            Text("📥 Correos del banco (buscar / releer)")
                        }
                    }
                }
                puntoDeshacer?.let { motivo ->
                    item {
                        androidx.compose.material3.OutlinedButton(onClick = { vm.deshacer() }, modifier = Modifier.fillMaxWidth()) {
                            Text("↩️ Deshacer: $motivo")
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✨ Registrar con IA", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (leyendo) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                                    Text("  Leyendo el recibo…")
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BotonAsistente("Recibo", Icons.Filled.CameraAlt, Modifier.weight(1f)) { elegirFoto = true }
                                    BotonAsistente("Hablar", Icons.Filled.Mic, Modifier.weight(1f)) { abrirVoz() }
                                    BotonAsistente("Escribir", Icons.Filled.Keyboard, Modifier.weight(1f)) { escribir = true }
                                }
                            }
                        }
                    }
                }
                item { TarjetaResumenMes(resumen, medMap) }
                item {
                    val cuadre = com.mipropiobanco.domain.Cuadre.de(movimientos, medios, categorias, mes)
                    com.mipropiobanco.ui.components.TarjetaCuadre(cuadre, "🧮 Cuadre de ${mes.titulo()}", alCorregirInicio = { real, origen, acreedor -> vm.corregirInicio(mes, real, origen, acreedor) }) { medio, monto, origen, acreedor ->
                        vm.explicarFaltante(medio, monto, origen, acreedor)
                    }
                }
                if (resumen.gastoPorCategoria.isNotEmpty()) {
                    item {
                        val anterior = ResumenMes.de(movimientos, mes.minusMonths(1))
                        val porciones = resumen.gastoPorCategoria.entries
                            .sortedByDescending { it.value }
                            .mapIndexed { i, (id, monto) ->
                                val c = catMap[id]
                                com.mipropiobanco.ui.components.Porcion(
                                    "${c?.icono ?: "•"} ${c?.nombre ?: "Otros"}", monto,
                                    com.mipropiobanco.ui.components.PaletaGraficas[i % com.mipropiobanco.ui.components.PaletaGraficas.size]
                                )
                            }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Gastos por categoría", style = MaterialTheme.typography.titleMedium)
                                com.mipropiobanco.ui.components.GraficaDona(porciones, "Gastado")
                                val dif = resumen.gastos - anterior.gastos
                                if (anterior.gastos > 0L) {
                                    val pct = (kotlin.math.abs(dif) * 100 / anterior.gastos)
                                    Text(
                                        if (dif > 0L) "📈 Gastaste ${Formato.cop(dif)} más que el mes pasado (+$pct%)"
                                        else "📉 Gastaste ${Formato.cop(-dif)} menos que el mes pasado (−$pct%) 👏",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (dif > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { reportePdf.launch("reporte_${mes}.pdf") },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("📄 Descargar reporte del mes (PDF)") }
                            }
                        }
                    }
                }
                item {
                    val saldos = com.mipropiobanco.domain.calcularSaldos(medios, movimientos)
                    Card(onClick = { ajustandoSaldos = true }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "💰 Mis cuentas hoy",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { eligiendoInicio = true }) { Text("📅 Desde") }
                                TextButton(onClick = { ajustandoSaldos = true }) { Text("✏️ Ajustar") }
                            }
                            val conMovimiento = medios.filter { m -> (saldos[m.id] ?: 0L) != 0L || m.saldoInicial != 0L }
                            if (conMovimiento.isEmpty()) {
                                Text(
                                    "Toca ✏️ Ajustar y escribe cuánto tienes hoy en cada cuenta y en efectivo.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            conMovimiento.forEach { m -> FilaResumen("${m.icono} ${m.nombre}", Formato.cop(saldos[m.id] ?: 0L)) }
                            if (conMovimiento.size > 1) {
                                FilaResumen("Total", Formato.cop(conMovimiento.sumOf { saldos[it.id] ?: 0L }), destacado = true)
                            }
                        }
                    }
                }

                if (pendientes.isNotEmpty()) {
                    item {
                        Text("Fijos por registrar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(pendientes.take(20), key = { "${it.plantilla.id}-${it.indice}" }) { o ->
                        TarjetaPendiente(
                            o = o,
                            categoria = catMap[o.plantilla.categoriaId],
                            alRegistrar = { registrando = o },
                            alOmitir = { vm.omitirOcurrencia(o) }
                        )
                    }
                }

                if (avances.isNotEmpty()) {
                    item {
                        Text("Presupuesto por categoría", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(avances, key = { "p-${it.categoria.id}" }) { a -> FilaPresupuesto(a) }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    ) {
                        FiltroMov.entries.forEach { f ->
                            FilterChip(selected = filtro == f, onClick = { filtro = f }, label = { Text(f.etiqueta) })
                        }
                    }
                }
                if (porDia.isEmpty()) {
                    item {
                        EstadoVacio(
                            "Sin movimientos este mes",
                            "Toca «Movimiento» para registrar un gasto o un ingreso. Si se repite (arriendo, salario, " +
                                "servicios), marca «Repetir» y se vuelve plantilla fija."
                        )
                    }
                }
                porDia.forEach { (dia, lista) ->
                    item(key = "d-$dia") {
                        val neto = lista.sumOf {
                            when (it.tipo) {
                                TipoMovimiento.INGRESO.name -> it.monto
                                TipoMovimiento.GASTO.name -> -it.monto
                                else -> 0L
                            }
                        }
                        Row(Modifier.padding(top = 6.dp)) {
                            Text(
                                dia.format(formatoDia).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(Formato.cop(neto), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    items(lista, key = { "m-${it.id}" }) { m ->
                        FilaMovimiento(m, catMap[m.categoriaId], medMap[m.medioId], m.medioDestinoId?.let { medMap[it] }) { editando = m }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nuevoTipo = TipoMovimiento.GASTO.name },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Movimiento") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (nuevoTipo.isNotEmpty() || editando != null || propuesta != null) {
        val p = propuesta
        // Si el recibo o la frase mencionan a quién le debes (ej. "Sistecrédito"), se sugiere esa deuda.
        val deudaSugerida = p?.let { prop ->
            val texto = com.mipropiobanco.domain.Interprete.normalizar(prop.textoOriginal + " " + prop.nota)
            deudasActivas.firstOrNull { d ->
                com.mipropiobanco.domain.Interprete.normalizar(d.deuda.acreedor)
                    .split(" ").filter { it.length >= 4 }.any { texto.contains(it) }
            }?.deuda?.id
        }
        DialogoMovimiento(
            existente = editando,
            propuesta = p,
            deudas = deudasActivas,
            deudaSugerida = deudaSugerida,
            tipoInicial = TipoMovimiento.desde(editando?.tipo ?: p?.tipo?.name ?: nuevoTipo),
            categorias = categorias,
            medios = medios,
            alGuardar = { tipo, monto, cat, medio, fecha, nota, repetir, foto, deudaId, destino ->
                vm.guardarMovimiento(editando?.id, tipo, monto, cat, medio, fecha, nota, repetir, editando?.plantillaId, foto, destino)
                // Además baja el saldo de la deuda elegida (sin volver a anotar el gasto).
                if (deudaId != null && editando == null) vm.abonarDeuda(deudaId, monto, fecha, comoGasto = false)
            },
            alEliminar = editando?.let { m -> { vm.eliminarMovimiento(m) } },
            alCancelar = { if (editando == null) vm.descartarFoto(p?.foto) },
            alCerrar = {
                nuevoTipo = ""
                editando = null
                propuesta = null
            }
        )
    }
    nomina?.let { n ->
        DialogoNomina(
            nomina = n,
            medios = medios,
            alGuardar = { items, medio, fecha -> vm.guardarNomina(items, medio, fecha, n.periodo, n.foto) },
            alCancelar = { vm.descartarFoto(n.foto) },
            alCerrar = { nomina = null }
        )
    }
    if (escribir) {
        DialogoEscribir(
            alListo = { texto -> propuesta = vm.interpretarFrase(texto) },
            alCerrar = { escribir = false }
        )
    }
    if (elegirFoto) {
        AlertDialog(
            onDismissRequest = { elegirFoto = false },
            title = { Text("Foto del recibo") },
            text = { Text("La IA lee el total, la fecha y el comercio. Todo pasa en tu celular, sin internet.") },
            confirmButton = {
                TextButton(onClick = {
                    elegirFoto = false
                    abrirCamara()
                }) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text(" Tomar foto")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    elegirFoto = false
                    galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(" Galería")
                }
            }
        )
    }
    registrando?.let { o ->
        DialogoRegistrarFijo(o, alConfirmar = { monto -> vm.registrarOcurrencia(o, monto) }, alCerrar = { registrando = null })
    }
}

@Composable
private fun TarjetaResumenMes(r: ResumenMes, medios: Map<Long, MedioPagoEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FilaResumen("Ingresos", Formato.cop(r.ingresos))
            FilaResumen("Gastos", Formato.cop(r.gastos))
            FilaResumen(if (r.balance >= 0L) "Te sobra" else "Te faltó", Formato.cop(r.balance), destacado = true)
            if (r.ingresos > 0L) {
                Text(
                    "Gastaste el ${Formato.porcentaje(r.gastos * 100.0 / r.ingresos, 0)} de lo que ingresó",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val porMedio = (r.gastoPorMedio.keys + r.ingresoPorMedio.keys).distinct()
            if (porMedio.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Por medio de pago", style = MaterialTheme.typography.labelLarge)
                porMedio.forEach { id ->
                    val m = medios[id]
                    val entra = r.ingresoPorMedio[id] ?: 0L
                    val sale = r.gastoPorMedio[id] ?: 0L
                    FilaResumen(
                        "${m?.icono.orEmpty()} ${m?.nombre ?: "Otro"}",
                        (if (entra > 0L) "+${Formato.cop(entra)} " else "") + (if (sale > 0L) "−${Formato.cop(sale)}" else "")
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaPendiente(o: Ocurrencia, categoria: CategoriaEntity?, alRegistrar: () -> Unit, alOmitir: () -> Unit) {
    val esIngreso = o.plantilla.tipo == TipoMovimiento.INGRESO.name
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(categoria?.icono ?: "📌", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(o.plantilla.nombre, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Tocaba el ${Formato.fecha(o.fecha)}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    (if (esIngreso) "+" else "−") + Formato.cop(o.plantilla.monto),
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = alOmitir) { Text("Omitir") }
                FilledTonalButton(onClick = alRegistrar) { Text("Registrar") }
            }
        }
    }
}

@Composable
private fun FilaPresupuesto(a: AvancePresupuesto) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${a.categoria.icono} ${a.categoria.nombre}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (a.limite > 0L) "${Formato.cop(a.gastado)} / ${Formato.cop(a.limite)}" else Formato.cop(a.gastado),
                style = MaterialTheme.typography.bodySmall,
                color = if (a.excedido) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        if (a.limite > 0L) {
            LinearProgressIndicator(
                progress = { a.fraccion.coerceIn(0f, 1f) },
                color = if (a.excedido) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Text(
                if (a.excedido) "Te pasaste por ${Formato.cop(-a.restante)}" else "Te quedan ${Formato.cop(a.restante)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (a.excedido) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FilaMovimiento(
    m: MovimientoEntity,
    c: CategoriaEntity?,
    medio: MedioPagoEntity?,
    destino: MedioPagoEntity? = null,
    alTocar: () -> Unit
) {
    if (m.tipo == TipoMovimiento.TRASLADO.name) {
        Card(onClick = alTocar, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                com.mipropiobanco.ui.components.IconoCirculo("🔄")
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Traslado", fontWeight = FontWeight.Medium)
                    Text(
                        "${medio?.nombre ?: "?"} → ${destino?.nombre ?: "?"}" + if (m.nota.isNotBlank()) " · ${m.nota}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(Formato.cop(m.monto), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }
    val esIngreso = m.tipo == TipoMovimiento.INGRESO.name
    Card(onClick = alTocar, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.mipropiobanco.ui.components.IconoCirculo(c?.icono ?: "📦")
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(c?.nombre ?: "Sin categoría", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(medio?.let { "${it.icono} ${it.nombre}" }, m.nota.ifBlank { null }, if (m.plantillaId != null) "🔁 fijo" else null, if (m.foto != null) "📎 recibo" else null)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                (if (esIngreso) "+" else "−") + Formato.cop(m.monto),
                fontWeight = FontWeight.Bold,
                color = if (esIngreso) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun SelectorCategoria(categorias: List<CategoriaEntity>, tipo: TipoMovimiento, seleccion: Long, alElegir: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        categorias.filter { it.tipo == tipo.name }.forEach { c ->
            FilterChip(selected = seleccion == c.id, onClick = { alElegir(c.id) }, label = { Text("${c.icono} ${c.nombre}") })
        }
    }
}

@Composable
fun SelectorMedio(medios: List<MedioPagoEntity>, seleccion: Long, alElegir: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        medios.forEach { m ->
            FilterChip(selected = seleccion == m.id, onClick = { alElegir(m.id) }, label = { Text("${m.icono} ${m.nombre}") })
        }
    }
}

@Composable
fun SelectorPeriodicidad(seleccion: Periodicidad, alElegir: (Periodicidad) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Periodicidad.entries.forEach { p ->
            FilterChip(selected = seleccion == p, onClick = { alElegir(p) }, label = { Text("Cada ${p.singular}") })
        }
    }
}

@Composable
internal fun DialogoMovimiento(
    existente: MovimientoEntity?,
    propuesta: Propuesta?,
    deudas: List<com.mipropiobanco.domain.DeudaConAbonos>,
    deudaSugerida: Long?,
    tipoInicial: TipoMovimiento,
    categorias: List<CategoriaEntity>,
    medios: List<MedioPagoEntity>,
    alGuardar: (TipoMovimiento, Long, Long, Long, Long, String, DatosRepeticion?, String?, Long?, Long?) -> Unit,
    alEliminar: (() -> Unit)?,
    alCancelar: () -> Unit,
    alCerrar: () -> Unit
) {
    var tipo by rememberSaveable { mutableStateOf(tipoInicial) }
    var monto by rememberSaveable { mutableStateOf(existente?.monto?.toString() ?: propuesta?.monto?.toString().orEmpty()) }
    var categoriaId by rememberSaveable { mutableStateOf(existente?.categoriaId ?: propuesta?.categoriaId ?: 0L) }
    var medioId by rememberSaveable {
        mutableStateOf(existente?.medioId ?: propuesta?.medioId ?: (medios.firstOrNull()?.id ?: 0L))
    }
    var fecha by rememberSaveable {
        mutableStateOf(existente?.fecha ?: propuesta?.fecha?.let { Formato.aMillis(it) } ?: Formato.hoyMillis())
    }
    var nota by rememberSaveable { mutableStateOf(existente?.nota ?: propuesta?.nota.orEmpty()) }
    var medioDestinoId by rememberSaveable {
        mutableStateOf(
            existente?.medioDestinoId ?: propuesta?.medioDestinoId
                ?: medios.firstOrNull { com.mipropiobanco.domain.Interprete.normalizar(it.nombre).contains("efectivo") }?.id ?: 0L
        )
    }
    val foto = existente?.foto ?: propuesta?.foto
    var verFoto by remember { mutableStateOf(false) }
    var deudaId by rememberSaveable { mutableStateOf(deudaSugerida) }
    // Si la deuda sugerida existe y no hay categoría, se pone "Pago de deudas".
    if (deudaSugerida != null && categoriaId == 0L) {
        categorias.firstOrNull { it.tipo == TipoMovimiento.GASTO.name && com.mipropiobanco.domain.Interprete.normalizar(it.nombre).contains("deuda") }
            ?.let { categoriaId = it.id }
    }
    val esPagoDeDeuda = tipo == TipoMovimiento.GASTO && existente == null && deudas.isNotEmpty() &&
        (deudaId != null || categorias.firstOrNull { it.id == categoriaId }
            ?.let { com.mipropiobanco.domain.Interprete.normalizar(it.nombre).contains("deuda") } == true)
    var repetir by rememberSaveable { mutableStateOf(false) }
    var periodicidad by rememberSaveable { mutableStateOf(Periodicidad.MENSUAL) }
    var automatica by rememberSaveable { mutableStateOf(true) }
    var confirmarBorrar by remember { mutableStateOf(false) }

    val montoV = monto.toLongOrNull() ?: 0L
    val esTraslado = tipo == TipoMovimiento.TRASLADO
    val categoriaValida = esTraslado || categorias.any { it.id == categoriaId && it.tipo == tipo.name }
    val valido = montoV > 0L && categoriaValida && medioId > 0L &&
        (!esTraslado || (medioDestinoId > 0L && medioDestinoId != medioId))

    val cancelar: () -> Unit = {
        alCancelar()
        alCerrar()
    }
    AlertDialog(
        onDismissRequest = cancelar,
        title = {
            Text(
                when {
                    existente != null -> "Editar movimiento"
                    propuesta != null -> "✨ Revisa y confirma"
                    else -> "Nuevo movimiento"
                }
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (propuesta != null && existente == null && propuesta.foto == null) {
                    Text("Entendí: «${propuesta.textoOriginal}»", style = MaterialTheme.typography.bodySmall)
                }
                if (foto != null) {
                    val miniatura = remember(foto) { Fotos.miniatura(foto, 600) }
                    if (miniatura != null) {
                        Image(
                            bitmap = miniatura.asImageBitmap(),
                            contentDescription = "Foto del recibo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { verFoto = true }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TipoMovimiento.entries.forEach { t ->
                        FilterChip(
                            selected = tipo == t,
                            onClick = { tipo = t },
                            label = {
                                Text(
                                    when (t) {
                                        TipoMovimiento.GASTO -> "− Gasto"
                                        TipoMovimiento.INGRESO -> "+ Ingreso"
                                        TipoMovimiento.TRASLADO -> "🔄 Traslado"
                                    }
                                )
                            }
                        )
                    }
                }
                CampoMonto(valor = monto, alCambiar = { monto = it }, etiqueta = "Monto")
                if (esTraslado) {
                    Text(
                        "Dinero que pasa entre tus cuentas (retiro en cajero, recarga a tu Nequi…). No cuenta como gasto ni ingreso.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Sale de", style = MaterialTheme.typography.labelLarge)
                    SelectorMedio(medios, medioId) { medioId = it }
                    Text("Llega a", style = MaterialTheme.typography.labelLarge)
                    SelectorMedio(medios, medioDestinoId) { medioDestinoId = it }
                } else {
                    Text("Categoría", style = MaterialTheme.typography.labelLarge)
                    SelectorCategoria(categorias, tipo, categoriaId) { categoriaId = it }
                }
                if (esPagoDeDeuda) {
                    Text("¿A cuál deuda le abonas? (baja su saldo)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = deudaId == null, onClick = { deudaId = null }, label = { Text("Ninguna") })
                        deudas.forEach { d ->
                            FilterChip(
                                selected = deudaId == d.deuda.id,
                                onClick = { deudaId = d.deuda.id },
                                label = { Text("${d.deuda.acreedor} · ${Formato.cop(d.saldo)}") }
                            )
                        }
                    }
                }
                if (!esTraslado) {
                    Text(if (tipo == TipoMovimiento.GASTO) "¿Cómo pagaste?" else "¿Cómo lo recibiste?", style = MaterialTheme.typography.labelLarge)
                    SelectorMedio(medios, medioId) { medioId = it }
                }
                SelectorFecha("Fecha", fecha, { fecha = it })
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it.take(80) },
                    label = { Text("Nota (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existente == null && !esTraslado) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = repetir, onCheckedChange = { repetir = it })
                        Text("Repetir: crear plantilla fija")
                    }
                    if (repetir) {
                        SelectorPeriodicidad(periodicidad) { periodicidad = it }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Registrar automáticamente")
                                Text(
                                    if (automatica) "Se anota solo en cada fecha" else "Te lo recuerda para confirmar el monto",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(checked = automatica, onCheckedChange = { automatica = it })
                        }
                    }
                }
                if (alEliminar != null) {
                    TextButton(onClick = { confirmarBorrar = true }) {
                        Text("Eliminar movimiento", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valido, onClick = {
                alGuardar(
                    tipo, montoV, if (esTraslado) 0L else categoriaId, medioId, fecha, nota,
                    if (repetir && !esTraslado) DatosRepeticion(periodicidad, automatica) else null, foto,
                    if (esPagoDeDeuda) deudaId else null,
                    if (esTraslado) medioDestinoId else null
                )
                alCerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = cancelar) { Text("Cancelar") } }
    )

    if (verFoto && foto != null) {
        val grande = remember(foto) { Fotos.miniatura(foto, 1600) }
        AlertDialog(
            onDismissRequest = { verFoto = false },
            confirmButton = { TextButton(onClick = { verFoto = false }) { Text("Cerrar") } },
            text = {
                if (grande != null) {
                    Image(
                        bitmap = grande.asImageBitmap(),
                        contentDescription = "Recibo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("La foto ya no está en este celular.")
                }
            }
        )
    }

    if (confirmarBorrar && alEliminar != null) {
        DialogoConfirmar(
            titulo = "¿Eliminar este movimiento?",
            texto = "Se borrará ${Formato.cop(montoV)} del ${Formato.fecha(fecha)}.",
            accion = "Eliminar",
            alConfirmar = {
                alEliminar()
                alCerrar()
            },
            alCerrar = { confirmarBorrar = false }
        )
    }
}

@Composable
private fun DialogoRegistrarFijo(o: Ocurrencia, alConfirmar: (Long) -> Unit, alCerrar: () -> Unit) {
    var monto by rememberSaveable { mutableStateOf(o.plantilla.monto.toString()) }
    val montoV = monto.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Registrar «${o.plantilla.nombre}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fecha: ${Formato.fecha(o.fecha)}. Si el valor cambió este periodo (por ejemplo, un recibo de servicios), ajústalo.")
                CampoMonto(valor = monto, alCambiar = { monto = it }, etiqueta = "Monto")
            }
        },
        confirmButton = {
            TextButton(enabled = montoV > 0L, onClick = {
                alConfirmar(montoV)
                alCerrar()
            }) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun BotonAsistente(texto: String, icono: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, alPulsar: () -> Unit) {
    FilledTonalButton(onClick = alPulsar, modifier = modifier, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)) {
        Icon(icono, contentDescription = null)
        Text(" $texto", maxLines = 1)
    }
}

@Composable
private fun DialogoEscribir(alListo: (String) -> Unit, alCerrar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Escríbelo como se lo dirías a alguien") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(200) },
                    placeholder = { Text("gasté 45 mil en mercado con Nequi") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Text(
                    "Ejemplos:\n• me pagaron 2 millones de salario\n• ayer tanqueé 80 mil en efectivo\n" +
                        "• pagué la luz 135.400 por transferencia\n• almuerzo 18 mil el 15",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(enabled = texto.isNotBlank(), onClick = {
                alListo(texto)
                alCerrar()
            }) { Text("Interpretar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}

/** Desglose del desprendible: el usuario revisa cada concepto antes de guardarlo. */
@Composable
private fun DialogoNomina(
    nomina: com.mipropiobanco.domain.PropuestaNomina,
    medios: List<MedioPagoEntity>,
    alGuardar: (List<com.mipropiobanco.domain.ItemNomina>, Long, Long) -> Unit,
    alCancelar: () -> Unit,
    alCerrar: () -> Unit
) {
    val incluidos = remember { androidx.compose.runtime.mutableStateListOf(*nomina.items.map { true }.toTypedArray()) }
    var fecha by rememberSaveable { mutableStateOf(Formato.aMillis(nomina.fecha)) }
    var medioId by rememberSaveable {
        mutableStateOf(
            medios.firstOrNull { com.mipropiobanco.domain.Interprete.normalizar(it.nombre).contains("transferencia") }?.id
                ?: medios.firstOrNull()?.id ?: 0L
        )
    }
    val elegidos = nomina.items.filterIndexed { i, _ -> incluidos.getOrElse(i) { true } }
    val devengado = elegidos.filter { !it.esDeduccion }.sumOf { it.monto }
    val deducido = elegidos.filter { it.esDeduccion }.sumOf { it.monto }
    val cancelar: () -> Unit = {
        alCancelar()
        alCerrar()
    }
    AlertDialog(
        onDismissRequest = cancelar,
        title = { Text("✨ Desprendible de nómina") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (nomina.periodo.isNotBlank()) Text(nomina.periodo, style = MaterialTheme.typography.bodySmall)
                Text("Devengado (ingresos)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                nomina.items.forEachIndexed { i, item ->
                    if (!item.esDeduccion) FilaNomina(item, incluidos[i]) { incluidos[i] = it }
                }
                Text("Deducciones (gastos)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                nomina.items.forEachIndexed { i, item ->
                    if (item.esDeduccion) FilaNomina(item, incluidos[i]) { incluidos[i] = it }
                }
                Spacer(Modifier.height(4.dp))
                FilaResumen("Total devengado", Formato.cop(devengado))
                FilaResumen("Total deducciones", "−" + Formato.cop(deducido))
                FilaResumen("Neto", Formato.cop(devengado - deducido), destacado = true)
                if (nomina.neto != null) {
                    val cuadra = nomina.neto == devengado - deducido
                    Text(
                        if (cuadra) "✓ Cuadra con el neto del desprendible (${Formato.cop(nomina.neto)})"
                        else "⚠ El desprendible dice ${Formato.cop(nomina.neto)}: revisa los conceptos",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cuadra) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Text("¿Cómo te pagan?", style = MaterialTheme.typography.labelLarge)
                SelectorMedio(medios, medioId) { medioId = it }
                SelectorFecha("Fecha de pago", fecha, { fecha = it })
            }
        },
        confirmButton = {
            TextButton(enabled = elegidos.isNotEmpty() && medioId > 0L, onClick = {
                alGuardar(elegidos, medioId, fecha)
                alCerrar()
            }) { Text("Guardar ${elegidos.size}") }
        },
        dismissButton = { TextButton(onClick = cancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun FilaNomina(item: com.mipropiobanco.domain.ItemNomina, incluido: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = incluido, onCheckedChange = alCambiar)
        Column(Modifier.weight(1f)) {
            Text("${item.icono} ${item.concepto}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.categoria, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            (if (item.esDeduccion) "−" else "+") + Formato.cop(item.monto),
            fontWeight = FontWeight.SemiBold,
            color = if (item.esDeduccion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}


/** Escribe cuánto tienes hoy en cada cuenta y en efectivo; la app ajusta el punto de partida. */
@Composable
private fun DialogoSaldos(
    medios: List<com.mipropiobanco.data.local.MedioPagoEntity>,
    alUnir: () -> Unit,
    saldos: Map<Long, Long>,
    alCerrar: () -> Unit,
    alGuardar: (Map<Long, Long>, Boolean) -> Unit
) {
    var comoMovimiento by androidx.compose.runtime.remember { mutableStateOf(true) }
    val valores = androidx.compose.runtime.remember(medios) {
        androidx.compose.runtime.mutableStateMapOf<Long, String>().apply {
            medios.forEach { m -> put(m.id, "") }
        }
    }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("💰 ¿Cuánto tienes hoy?") },
        text = {
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fun n(t: String) = com.mipropiobanco.domain.Interprete.normalizar(t)
                val separadas = medios.count {
                    val x = n(it.nombre)
                    x.contains("transferencia") || x.contains("bancolombia") || (x.contains("debito") && !x.contains("credito"))
                } >= 2
                if (separadas) {
                    androidx.compose.material3.Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "¿Transferencia y Tarjeta débito son tu misma cuenta Bancolombia? Únelas para que la plata no salga repartida. También se borran los ajustes de saldo anteriores.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            FilledTonalButton(onClick = alUnir) { Text("🔗 Unir en Bancolombia") }
                        }
                    }
                }
                Text(
                    "Escribe lo que hay HOY solo en las cuentas que quieras corregir. Las que dejes vacías no se tocan.",
                    style = MaterialTheme.typography.bodySmall
                )
                medios.filter { (saldos[it.id] ?: 0L) != 0L }.takeIf { it.isNotEmpty() }?.let { conSaldo ->
                    Text(
                        "La app calcula hoy: " + conSaldo.joinToString(" · ") { "${it.icono} ${it.nombre} ${com.mipropiobanco.util.Formato.cop(saldos[it.id] ?: 0L)}" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = comoMovimiento, onCheckedChange = { comoMovimiento = it })
                    Text(
                        if (comoMovimiento) "Si la app cree que tienes más plata, la diferencia se anota hoy como gasto (Otros gastos). Si cree que tienes menos, solo se ajusta el punto de partida."
                        else "Solo ajustar el punto de partida, sin anotar gasto ni ingreso.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                medios.forEach { m ->
                    CampoMonto(
                        valor = valores[m.id] ?: "",
                        alCambiar = { valores[m.id] = it },
                        etiqueta = "${m.icono} ${m.nombre}"
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                alGuardar(
                    medios.mapNotNull { m ->
                        val v = valores[m.id] ?: ""
                        if (v.isBlank()) null else m.id to (v.toLongOrNull() ?: 0L)
                    }.toMap(),
                    comoMovimiento
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}


/** Elige desde qué fecha se aceptan los correos del banco. No borra movimientos. */
@Composable
private fun DialogoInicio(
    movimientos: List<com.mipropiobanco.data.local.MovimientoEntity>,
    alCerrar: () -> Unit,
    alConfirmar: (Long) -> Unit
) {
    var fecha by rememberSaveable {
        mutableStateOf(Formato.aMillis(java.time.LocalDate.now().withDayOfMonth(1)))
    }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("📅 Correos desde…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.mipropiobanco.ui.components.SelectorFecha("Desde", fecha, { fecha = it })
                Text(
                    "Los correos del banco anteriores a esta fecha se ignoran. Tus movimientos guardados NO se borran.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { FilledTonalButton(onClick = { alConfirmar(fecha) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}
