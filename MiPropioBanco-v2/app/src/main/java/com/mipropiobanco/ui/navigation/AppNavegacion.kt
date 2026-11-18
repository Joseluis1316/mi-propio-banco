package com.mipropiobanco.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.screens.AjustesScreen
import com.mipropiobanco.ui.screens.AportesScreen
import com.mipropiobanco.ui.screens.DashboardScreen
import com.mipropiobanco.ui.screens.DeudasScreen
import com.mipropiobanco.ui.screens.DetalleDeudaScreen
import com.mipropiobanco.ui.screens.DetallePrestamoScreen
import com.mipropiobanco.ui.screens.NuevoPrestamoScreen
import com.mipropiobanco.ui.screens.PrestamosScreen

private object Rutas {
    const val INICIO = "inicio"
    const val APORTES = "aportes"
    const val PRESTAMOS = "prestamos"
    const val AJUSTES = "ajustes"
    const val NUEVO_PRESTAMO = "nuevo_prestamo"
    const val DETALLE_PRESTAMO = "prestamo"
    const val DEUDAS = "deudas"
    const val DETALLE_DEUDA = "deuda"
}

private data class Destino(val ruta: String, val etiqueta: String, val icono: ImageVector)

private val destinosPrincipales = listOf(
    Destino(Rutas.INICIO, "Inicio", Icons.Filled.Home),
    Destino(Rutas.APORTES, "Aportes", Icons.Filled.Savings),
    Destino(Rutas.PRESTAMOS, "Préstamos", Icons.Filled.RequestQuote),
    Destino(Rutas.DEUDAS, "Deudas", Icons.Filled.CreditCard),
    Destino(Rutas.AJUSTES, "Ajustes", Icons.Filled.Settings)
)

private fun NavHostController.irAPestana(ruta: String) {
    navigate(ruta) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavegacion(vm: BancoViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.mensajes.collect { snackbar.showSnackbar(it) }
    }

    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route.orEmpty()
    val pestanaActual = destinosPrincipales.firstOrNull { d ->
        rutaActual == d.ruta || rutaActual.startsWith(d.ruta + "?")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (pestanaActual != null) {
                NavigationBar {
                    destinosPrincipales.forEach { d ->
                        NavigationBarItem(
                            selected = d == pestanaActual,
                            onClick = { nav.irAPestana(d.ruta) },
                            icon = { Icon(d.icono, contentDescription = null) },
                            label = { Text(d.etiqueta, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { relleno ->
        NavHost(
            navController = nav,
            startDestination = Rutas.INICIO,
            modifier = Modifier.padding(relleno)
        ) {
            composable(Rutas.INICIO) {
                DashboardScreen(
                    vm = vm,
                    alNuevoAporte = { nav.navigate("${Rutas.APORTES}?nuevo=true") },
                    alNuevoPrestamo = { nav.navigate(Rutas.NUEVO_PRESTAMO) },
                    alRegistrarPago = { nav.irAPestana(Rutas.PRESTAMOS) },
                    alRespaldo = { nav.irAPestana(Rutas.AJUSTES) },
                    alAbrirPrestamo = { id -> nav.navigate("${Rutas.DETALLE_PRESTAMO}/$id") },
                    alVerDeudas = { nav.irAPestana(Rutas.DEUDAS) }
                )
            }
            composable(
                route = "${Rutas.APORTES}?nuevo={nuevo}",
                arguments = listOf(navArgument("nuevo") {
                    type = NavType.BoolType
                    defaultValue = false
                })
            ) { e ->
                AportesScreen(vm = vm, abrirFormulario = e.arguments?.getBoolean("nuevo") ?: false)
            }
            composable(Rutas.PRESTAMOS) {
                PrestamosScreen(
                    vm = vm,
                    alNuevo = { nav.navigate(Rutas.NUEVO_PRESTAMO) },
                    alAbrir = { id -> nav.navigate("${Rutas.DETALLE_PRESTAMO}/$id") }
                )
            }
            composable(Rutas.DEUDAS) {
                DeudasScreen(vm = vm, alAbrir = { id -> nav.navigate("${Rutas.DETALLE_DEUDA}/$id") })
            }
            composable(
                route = "${Rutas.DETALLE_DEUDA}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                DetalleDeudaScreen(
                    vm = vm,
                    deudaId = e.arguments?.getLong("id") ?: 0L,
                    alVolver = { nav.popBackStack() }
                )
            }
            composable(Rutas.AJUSTES) {
                AjustesScreen(vm = vm)
            }
            composable(Rutas.NUEVO_PRESTAMO) {
                NuevoPrestamoScreen(
                    vm = vm,
                    alVolver = { nav.popBackStack() },
                    alCrear = { id ->
                        nav.navigate("${Rutas.DETALLE_PRESTAMO}/$id") {
                            popUpTo(Rutas.NUEVO_PRESTAMO) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = "${Rutas.DETALLE_PRESTAMO}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                DetallePrestamoScreen(
                    vm = vm,
                    prestamoId = e.arguments?.getLong("id") ?: 0L,
                    alVolver = { nav.popBackStack() }
                )
            }
        }
    }
}
