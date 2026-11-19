package com.mipropiobanco.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mipropiobanco.ui.BancoViewModel

/** "Mi banco": aportes de capital y autopréstamos en una sola pestaña. */
@Composable
fun BancoScreen(
    vm: BancoViewModel,
    pestanaInicial: Int,
    abrirAporte: Boolean,
    alNuevoPrestamo: () -> Unit,
    alAbrirPrestamo: (Long) -> Unit,
    alPrestarAOtro: () -> Unit = alNuevoPrestamo
) {
    var pestana by rememberSaveable { mutableIntStateOf(pestanaInicial) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pestana) {
            Tab(selected = pestana == 0, onClick = { pestana = 0 }, text = { Text("Aportes") })
            Tab(selected = pestana == 1, onClick = { pestana = 1 }, text = { Text("Autopréstamos") })
            Tab(selected = pestana == 2, onClick = { pestana = 2 }, text = { Text("Préstamos") })
        }
        Box(Modifier.weight(1f)) {
            when (pestana) {
                0 -> AportesScreen(vm = vm, abrirFormulario = abrirAporte)
                1 -> PrestamosScreen(vm = vm, alNuevo = alNuevoPrestamo, alAbrir = alAbrirPrestamo, aOtros = false)
                else -> PrestamosScreen(vm = vm, alNuevo = alPrestarAOtro, alAbrir = alAbrirPrestamo, aOtros = true)
            }
        }
    }
}
