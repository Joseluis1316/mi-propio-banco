package com.mipropiobanco

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.navigation.AppNavegacion
import com.mipropiobanco.ui.theme.MiPropioBancoTheme
import com.mipropiobanco.ui.theme.esModoOscuro

class MainActivity : FragmentActivity() {

    /** true mientras la app espera la huella / PIN. */
    private val bloqueada = mutableStateOf(false)
    private var salioEn = 0L

    private val preferencias get() = (application as MiPropioBancoApp).preferencias

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) bloqueada.value = preferencias.bloqueo.value && puedeBloquear()
        enableEdgeToEdge()
        setContent {
            val vm: BancoViewModel = viewModel()
            val modo by vm.modoTema.collectAsStateWithLifecycle()
            val oscuro = esModoOscuro(modo)
            val estaBloqueada by bloqueada

            // Íconos de la barra de estado claros u oscuros según el tema elegido en la app.
            DisposableEffect(oscuro) {
                val estilo = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { oscuro }
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
                onDispose { }
            }

            MiPropioBancoTheme(oscuro = oscuro) {
                if (estaBloqueada) {
                    LaunchedEffect(Unit) { autenticar() }
                    PantallaBloqueo { autenticar() }
                } else {
                    AppNavegacion(vm)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        salioEn = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Si estuvo más de 1 minuto en segundo plano, vuelve a pedir la huella.
        if (salioEn > 0L && preferencias.bloqueo.value && puedeBloquear() &&
            SystemClock.elapsedRealtime() - salioEn > 60_000L
        ) {
            bloqueada.value = true
        }
    }

    private fun puedeBloquear(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun autenticar() {
        if (!puedeBloquear()) {
            bloqueada.value = false
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    bloqueada.value = false
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Mi Propio Banco")
            .setSubtitle("Desbloquea para ver tus finanzas")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}

@androidx.compose.runtime.Composable
private fun PantallaBloqueo(alDesbloquear: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔒", style = MaterialTheme.typography.displayMedium)
            Text("Mi Propio Banco", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Tus finanzas están protegidas. Usa tu huella, rostro o el PIN del celular.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(onClick = alDesbloquear) { Text("Desbloquear") }
        }
    }
}
