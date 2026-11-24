package com.mipropiobanco.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.util.Formato

/** Ahorro: Mi banco (guardar y prestar) y las metas, en un solo lugar. */
@Composable
fun AhorroScreen(
    vm: BancoViewModel,
    alGuardar: () -> Unit,
    alPrestarme: () -> Unit,
    alPrestarAOtro: () -> Unit,
    alVerBanco: () -> Unit,
    alAbrirPrestamo: (Long) -> Unit,
    alMetas: () -> Unit
) {
    val resumen by vm.resumen.collectAsStateWithLifecycle()
    val prestamos by vm.prestamos.collectAsStateWithLifecycle()
    val metas by vm.metas.collectAsStateWithLifecycle()
    val activos = prestamos.filter { !it.prestamo.pagado }
    val propios = activos.filter { it.prestamo.prestatario.isBlank() }
    val aOtros = activos.filter { it.prestamo.prestatario.isNotBlank() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Ahorro", style = MaterialTheme.typography.headlineSmall) }

        item {
            Surface(onClick = alVerBanco, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mi banco · capital", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formato.cop(resumen.capitalTotal), style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Disponible ${Formato.cop(resumen.disponible)} · prestado ${Formato.cop(resumen.porRecuperar)} · ganado ${Formato.cop(resumen.interesesGanados)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Button(onClick = alGuardar, modifier = Modifier.weight(1f)) { Text("Guardar plata") }
                        OutlinedButton(onClick = alPrestarme, modifier = Modifier.weight(1f)) { Text("Prestarme") }
                    }
                    TextButton(onClick = alPrestarAOtro) { Text("Prestarle a otra persona") }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Cuadro("Me debo a mí", Formato.cop(propios.sumOf { it.saldoPendiente }), "${propios.size} autopréstamo(s)", Modifier.weight(1f))
                Cuadro("Me deben", Formato.cop(aOtros.sumOf { it.saldoPendiente }), "${aOtros.size} préstamo(s) a otros", Modifier.weight(1f))
            }
        }

        if (activos.isNotEmpty()) {
            item { Text("Préstamos activos", style = MaterialTheme.typography.titleMedium) }
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        activos.forEachIndexed { i, p ->
                            Surface(onClick = { alAbrirPrestamo(p.prestamo.id) }, color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row {
                                        Text(
                                            if (p.prestamo.prestatario.isBlank()) p.prestamo.concepto else "${p.prestamo.prestatario} · ${p.prestamo.concepto}",
                                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        Text(Formato.cop(p.saldoPendiente), fontWeight = FontWeight.SemiBold)
                                    }
                                    Progreso(p.progreso)
                                    p.proximaCuota?.let { f ->
                                        Text(
                                            "Cuota ${f.numero} de ${p.tabla.size} · ${Formato.cop(f.cuota)} el ${Formato.fecha(p.vencimiento(f.numero))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (i < activos.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Metas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = alMetas) { Text(if (metas.isEmpty()) "Crear meta" else "Ver y abonar") }
            }
        }
        if (metas.isEmpty()) {
            item {
                Text(
                    "Ponle nombre y valor a lo que quieres (moto, viaje, fondo de emergencia) y te digo cuánto guardar cada quincena.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                Surface(onClick = alMetas, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        metas.forEachIndexed { i, m ->
                            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row {
                                    Text("${m.icono} ${m.nombre}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${Formato.cop(m.ahorrado)} de ${Formato.cop(m.objetivo)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Progreso(m.fraccion)
                                Text(
                                    if (m.cumplida) "¡Cumplida!" else "Te faltan ${Formato.cop(m.falta)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (i < metas.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cuadro(titulo: String, valor: String, detalle: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(detalle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Progreso(f: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
    }
}
