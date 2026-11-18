@file:OptIn(ExperimentalMaterial3Api::class)

package com.mipropiobanco.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.ui.components.EstadoVacio
import com.mipropiobanco.ui.components.TarjetaPrestamo

private enum class FiltroPrestamos(val etiqueta: String) { ACTIVOS("Activos"), PAGADOS("Pagados"), TODOS("Todos") }

@Composable
fun PrestamosScreen(vm: BancoViewModel, alNuevo: () -> Unit, alAbrir: (Long) -> Unit) {
    val prestamos by vm.prestamos.collectAsStateWithLifecycle()
    var filtro by rememberSaveable { mutableStateOf(FiltroPrestamos.ACTIVOS) }
    val visibles = when (filtro) {
        FiltroPrestamos.ACTIVOS -> prestamos.filter { !it.prestamo.pagado }
        FiltroPrestamos.PAGADOS -> prestamos.filter { it.prestamo.pagado }
        FiltroPrestamos.TODOS -> prestamos
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Encabezado("Autopréstamos")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                FiltroPrestamos.entries.forEach { f ->
                    FilterChip(
                        selected = filtro == f,
                        onClick = { filtro = f },
                        label = { Text(f.etiqueta) }
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (visibles.isEmpty()) {
                    item {
                        EstadoVacio(
                            titulo = "No hay préstamos aquí",
                            detalle = "Crea un autopréstamo: el simulador te muestra la cuota y la tabla " +
                                "antes de sacar el dinero de la bóveda. Toca un préstamo para registrar sus pagos."
                        )
                    }
                }
                items(visibles, key = { it.prestamo.id }) { item ->
                    TarjetaPrestamo(item = item, alAbrir = { alAbrir(item.prestamo.id) })
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = alNuevo,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nuevo préstamo") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}
