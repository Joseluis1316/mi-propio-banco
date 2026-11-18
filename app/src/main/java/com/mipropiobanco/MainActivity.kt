package com.mipropiobanco

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.navigation.AppNavegacion
import com.mipropiobanco.ui.theme.MiPropioBancoTheme
import com.mipropiobanco.ui.theme.esModoOscuro

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: BancoViewModel = viewModel()
            val modo by vm.modoTema.collectAsStateWithLifecycle()
            val oscuro = esModoOscuro(modo)

            // Íconos de la barra de estado claros u oscuros según el tema elegido en la app.
            DisposableEffect(oscuro) {
                val estilo = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { oscuro }
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
                onDispose { }
            }

            MiPropioBancoTheme(oscuro = oscuro) {
                AppNavegacion(vm)
            }
        }
    }
}
