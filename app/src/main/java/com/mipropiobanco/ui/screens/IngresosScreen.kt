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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.data.local.MovimientoEntity
import com.mipropiobanco.domain.Interprete
import com.mipropiobanco.domain.Propuesta
import com.mipropiobanco.domain.Recurrencia
import com.mipropiobanco.domain.ResumenMes
import com.mipropiobanco.domain.TipoMovimiento
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.EstadoVacio
import com.mipropiobanco.ui.components.GraficaDona
import com.mipropiobanco.ui.components.PaletaGraficas
import com.mipropiobanco.ui.components.Porcion
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.YearMonth

/** Módulo de ingresos: cuánto entró, de dónde, qué falta por llegar y registro rápido. */
@Composable
fun IngresosScreen(
    vm: BancoViewModel,
    alHablar: () -> Unit,
    alFoto: () -> Unit
) {
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val medios by vm.medios.collectAsStateWithLifecycle()
    val plantillas by vm.plantillas.collectAsStateWithLifecycle()
    val deudas by vm.deudas.collectAsStateWithLifecycle()

    var mesTexto by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val mes = YearMonth.parse(mesTexto)
    var nuevo by remember { mutableStateOf<Propuesta?>(null) }
    var editando by remember { mutableStateOf<MovimientoEntity?>(null) }

    val catMap = categorias.associateBy { it.id }
    val medMap = medios.associateBy { it.id }
    val resumen = ResumenMes.de(movimientos, mes)
    val anterior = ResumenMes.de(movimientos, mes.minusMonths(1))
    val delMes = movimientos
        .filter { it.tipo == TipoMovimiento.INGRESO.name && YearMonth.from(Formato.aLocalDate(it.fecha)) == mes }
        .sortedByDescending { it.fecha }
    val prestamosRecibidos = categorias.firstOrNull {
        val n = Interprete.normalizar(it.nombre); n.contains("prestamo") && n.contains("recib")
    }
    // Ingresos que no son plata tuya ganada (te los prestaron): se muestran aparte.
    val prestado = delMes.filter { it.categoriaId == prestamosRecibidos?.id }.sumOf { it.monto }
    val ganado = resumen.ingresos - prestado

    // Ingresos fijos (salario, arriendo…) que todavía deben llegar este mes y los próximos 30 días.
    val hoy = LocalDate.now()
    val proximos = plantillas
        .filter { it.tipo == TipoMovimiento.INGRESO.name && it.activa }
        .flatMap { Recurrencia.enRango(it, hoy, hoy.plusDays(30)) }
        .sortedBy { it.fecha }
    val esperadoMes = plantillas
        .filter { it.tipo == TipoMovimiento.INGRESO.name && it.activa }
        .flatMap { Recurrencia.enRango(it, mes.atDay(1), mes.atEndOfMonth()) }
        .sumOf { it.plantilla.monto }

    fun abrirNuevo(categoriaId: Long? = null) {
        nuevo = Propuesta(
            tipo = TipoMovimiento.INGRESO, monto = null, categoriaId = categoriaId,
            medioId = null, fecha = LocalDate.now(), nota = ""
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("Ingresos", style = MaterialTheme.typography.headlineSmall) }
            item { SelectorMes(mes) { mesTexto = it.toString() } }

            // ---- Tarjeta principal ----
            item {
                val degradado = Brush.linearGradient(listOf(Color(0xFF00A86B), Color(0xFF00796B), Color(0xFF004D40)))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                    Column(
                        Modifier.fillMaxWidth().background(degradado).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Entró este mes", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                        Text(Formato.cop(resumen.ingresos), color = Color.White, style = MaterialTheme.typography.displaySmall, maxLines = 1)
                        if (anterior.ingresos > 0L) {
                            val dif = resumen.ingresos - anterior.ingresos
                            val pct = kotlin.math.abs(dif) * 100 / anterior.ingresos
                            Text(
                                if (dif >= 0L) "▲ ${Formato.cop(dif)} más que el mes pasado (+$pct%)"
                                else "▼ ${Formato.cop(-dif)} menos que el mes pasado (−$pct%)",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipBlanco("💪 Ganado", Formato.cop(ganado), Modifier.weight(1f))
                            ChipBlanco("🤝 Prestado", Formato.cop(prestado), Modifier.weight(1f))
                            ChipBlanco("📅 Fijos del mes", Formato.cop(esperadoMes), Modifier.weight(1f))
                        }
                    }
                }
            }

            // ---- Acciones ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BotonAccion("➕", "Ingreso", Modifier.weight(1f)) { abrirNuevo() }
                    BotonAccion("🎤", "Hablar", Modifier.weight(1f), alHablar)
                    BotonAccion("📷", "Nómina", Modifier.weight(1f), alFoto)
                    BotonAccion("🤝", "Me prestaron", Modifier.weight(1f)) { abrirNuevo(prestamosRecibidos?.id) }
                }
            }

            // ---- Próximos ingresos fijos ----
            if (proximos.isNotEmpty()) {
                item { Text("Por llegar (próximos 30 días)", style = MaterialTheme.typography.titleMedium) }
                items(proximos.take(6), key = { "p-${it.plantilla.id}-${it.indice}" }) { o ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(catMap[o.plantilla.categoriaId]?.icono ?: "💰", style = MaterialTheme.typography.titleLarge)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(o.plantilla.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(Formato.fecha(o.fecha), style = MaterialTheme.typography.labelSmall)
                            }
                            Text("+" + Formato.cop(o.plantilla.monto), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ---- De dónde viene la plata ----
            if (resumen.ingresoPorCategoria.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("¿De dónde viene la plata?", style = MaterialTheme.typography.titleMedium)
                            GraficaDona(
                                resumen.ingresoPorCategoria.entries.sortedByDescending { it.value }.mapIndexed { i, (id, monto) ->
                                    val c = catMap[id]
                                    Porcion("${c?.icono ?: "•"} ${c?.nombre ?: "Otros"}", monto, PaletaGraficas[i % PaletaGraficas.size])
                                },
                                "Entró"
                            )
                        }
                    }
                }
            }

            // ---- Lista ----
            item { Text("Ingresos del mes (${delMes.size})", style = MaterialTheme.typography.titleMedium) }
            if (delMes.isEmpty()) {
                item {
                    EstadoVacio(
                        "Aún no hay ingresos este mes",
                        "Anota tu salario, ventas, horas extras o lo que te prestaron. Los correos del banco también los traen solos."
                    )
                }
            }
            items(delMes, key = { it.id }) { m ->
                val c = catMap[m.categoriaId]
                Surface(
                    onClick = { editando = m },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c?.icono ?: "💰", style = MaterialTheme.typography.titleLarge)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(c?.nombre ?: "Ingreso", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(Formato.fecha(m.fecha), medMap[m.medioId]?.nombre, m.nota.takeIf { it.isNotBlank() })
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text("+" + Formato.cop(m.monto), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { abrirNuevo() },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Ingreso") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }

    if (nuevo != null || editando != null) {
        val e = editando
        DialogoMovimiento(
            existente = e,
            propuesta = if (e == null) nuevo else null,
            deudas = deudas.filter { !it.pagada },
            deudaSugerida = null,
            tipoInicial = TipoMovimiento.INGRESO,
            categorias = categorias,
            medios = medios,
            alGuardar = { tipo, monto, cat, medio, fecha, nota, repetir, foto, _, destino ->
                vm.guardarMovimiento(e?.id, tipo, monto, cat, medio, fecha, nota, repetir, e?.plantillaId, foto, destino)
            },
            alEliminar = e?.let { m -> { vm.eliminarMovimiento(m) } },
            alCancelar = { },
            alCerrar = {
                nuevo = null
                editando = null
            }
        )
    }
}

@Composable
private fun ChipBlanco(titulo: String, valor: String, modifier: Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Text(titulo, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(valor, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BotonAccion(icono: String, texto: String, modifier: Modifier, alPulsar: () -> Unit) {
    Surface(
        onClick = alPulsar,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icono, style = MaterialTheme.typography.titleLarge)
            Text(texto, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
