package com.mipropiobanco.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.mipropiobanco.ui.screens.BancoScreen
import com.mipropiobanco.ui.screens.BandejaScreen
import com.mipropiobanco.ui.screens.CalendarioScreen
import com.mipropiobanco.ui.screens.ConfiguracionFinanzasScreen
import com.mipropiobanco.ui.screens.DashboardScreen
import com.mipropiobanco.ui.screens.DetalleDeudaScreen
import com.mipropiobanco.ui.screens.DetallePrestamoScreen
import com.mipropiobanco.ui.screens.DeudasScreen
import com.mipropiobanco.ui.screens.LoginScreen
import com.mipropiobanco.ui.screens.MovimientosScreen
import com.mipropiobanco.ui.screens.NuevoPrestamoScreen

private object Rutas {
    const val INICIO = "inicio"
    const val MOVIMIENTOS = "movimientos"
    const val CALENDARIO = "calendario"
    const val BANCO = "banco"
    const val DEUDAS = "deudas"
    const val AJUSTES = "ajustes"
    const val CONFIG_FINANZAS = "config_finanzas"
    const val BANDEJA = "bandeja"
    const val METAS = "metas"
    const val NUEVO_PRESTAMO = "nuevo_prestamo"
    const val DETALLE_PRESTAMO = "prestamo"
    const val DETALLE_DEUDA = "deuda"
}

private data class Destino(val ruta: String, val etiqueta: String, val icono: ImageVector)

private val destinosPrincipales = listOf(
    Destino(Rutas.INICIO, "Inicio", Icons.Filled.Home),
    Destino(Rutas.MOVIMIENTOS, "Gastos", Icons.Filled.AccountBalanceWallet),
    Destino(Rutas.CALENDARIO, "Calendario", Icons.Filled.CalendarMonth),
    Destino(Rutas.BANCO, "Mi banco", Icons.Filled.Savings),
    Destino(Rutas.DEUDAS, "Deudas", Icons.Filled.CreditCard)
)

private fun NavHostController.irAPestana(ruta: String) {
    navigate(ruta) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Abre una pestaña con argumentos nuevos (sin restaurar el estado anterior). */
private fun NavHostController.irAPestanaCon(ruta: String) {
    navigate(ruta) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

@Composable
fun AppNavegacion(vm: BancoViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.mensajes.collect { snackbar.showSnackbar(it) }
    }

    val correo by vm.correo.collectAsStateWithLifecycle()
    val modoLocal by vm.modoLocal.collectAsStateWithLifecycle()
    val pedirLogin = correo == null && !modoLocal

    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route.orEmpty()
    val pestanaActual = destinosPrincipales.firstOrNull { d ->
        rutaActual == d.ruta || rutaActual.startsWith(d.ruta + "?")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (pestanaActual != null && !pedirLogin) {
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
        if (pedirLogin) {
            Box(Modifier.padding(relleno)) { LoginScreen(vm) }
        } else NavHost(
            navController = nav,
            startDestination = Rutas.INICIO,
            modifier = Modifier.padding(relleno)
        ) {
            composable(Rutas.INICIO) {
                DashboardScreen(
                    vm = vm,
                    alNuevoMovimiento = { tipo -> nav.irAPestanaCon("${Rutas.MOVIMIENTOS}?nuevo=$tipo") },
                    alNuevoAporte = { nav.irAPestanaCon("${Rutas.BANCO}?pestana=0&nuevo=true") },
                    alNuevoPrestamo = { nav.navigate(Rutas.NUEVO_PRESTAMO) },
                    alRegistrarPago = { nav.irAPestanaCon("${Rutas.BANCO}?pestana=1&nuevo=false") },
                    alAbrirPrestamo = { id -> nav.navigate("${Rutas.DETALLE_PRESTAMO}/$id") },
                    alVerDeudas = { nav.irAPestana(Rutas.DEUDAS) },
                    alVerMovimientos = { nav.irAPestana(Rutas.MOVIMIENTOS) },
                    alAjustes = { nav.navigate(Rutas.AJUSTES) },
                    alBandeja = { nav.navigate(Rutas.BANDEJA) },
                    alMetas = { nav.navigate(Rutas.METAS) }
                )
            }
            composable(Rutas.METAS) {
                MetasScreen(vm = vm, alVolver = { nav.popBackStack() })
            }
            composable(
                route = "${Rutas.MOVIMIENTOS}?nuevo={nuevo}",
                arguments = listOf(navArgument("nuevo") {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { e ->
                MovimientosScreen(
                    vm = vm,
                    nuevoInicial = e.arguments?.getString("nuevo").orEmpty(),
                    alConfigurar = { nav.navigate(Rutas.CONFIG_FINANZAS) },
                    alBandeja = { nav.navigate(Rutas.BANDEJA) }
                )
            }
            composable(Rutas.CALENDARIO) {
                CalendarioScreen(vm = vm)
            }
            composable(
                route = "${Rutas.BANCO}?pestana={pestana}&nuevo={nuevo}",
                arguments = listOf(
                    navArgument("pestana") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("nuevo") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { e ->
                BancoScreen(
                    vm = vm,
                    pestanaInicial = e.arguments?.getInt("pestana") ?: 0,
                    abrirAporte = e.arguments?.getBoolean("nuevo") ?: false,
                    alNuevoPrestamo = { nav.navigate(Rutas.NUEVO_PRESTAMO) },
                    alAbrirPrestamo = { id -> nav.navigate("${Rutas.DETALLE_PRESTAMO}/$id") }
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
                AjustesScreen(
                    vm = vm,
                    alVolver = { nav.popBackStack() },
                    alConfigFinanzas = { nav.navigate(Rutas.CONFIG_FINANZAS) }
                )
            }
            composable(Rutas.BANDEJA) {
                BandejaScreen(
                    vm = vm,
                    alVolver = { nav.popBackStack() },
                    alComoConectar = { nav.navigate(Rutas.AJUSTES) }
                )
            }
            composable(Rutas.CONFIG_FINANZAS) {
                ConfiguracionFinanzasScreen(vm = vm, alVolver = { nav.popBackStack() })
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
