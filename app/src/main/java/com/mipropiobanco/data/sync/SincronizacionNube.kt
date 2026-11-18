package com.mipropiobanco.data.sync

import android.os.Build
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.mipropiobanco.data.backup.BackupManager
import com.mipropiobanco.data.prefs.AjustesPreferencias
import com.mipropiobanco.data.repository.BancoRepository
import com.mipropiobanco.data.repository.FinanzasRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Correo del banco que el script de Gmail dejó en la nube, pendiente de revisar. */
data class CorreoBandeja(val id: String, val texto: String, val fecha: Long)

sealed interface EstadoNube {
    data object SinCuenta : EstadoNube
    data object Sincronizando : EstadoNube
    data class AlDia(val cuando: Long) : EstadoNube
    data class Pendiente(val motivo: String) : EstadoNube
}

/**
 * Cuenta con correo (Firebase Auth) y copia en la nube (Firestore).
 * La base local (Room) sigue siendo la principal: la app funciona sin internet y
 * cada cambio se sube solo unos segundos después. Cada usuario tiene un documento
 * privado usuarios/{uid} con el respaldo completo en JSON.
 */
class SincronizacionNube(
    private val respaldo: BackupManager,
    private val repo: BancoRepository,
    private val finanzas: FinanzasRepository,
    private val prefs: AjustesPreferencias,
    private val alcance: CoroutineScope
) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val candado = Mutex()

    @Volatile
    private var importando = false

    private val _correo = MutableStateFlow(auth.currentUser?.email)
    val correo: StateFlow<String?> = _correo.asStateFlow()

    private val _estado = MutableStateFlow<EstadoNube>(
        if (auth.currentUser == null) EstadoNube.SinCuenta
        else if (prefs.ultimaSincronizacion() > 0L) EstadoNube.AlDia(prefs.ultimaSincronizacion())
        else EstadoNube.Pendiente("Aún no se ha sincronizado")
    )
    val estado: StateFlow<EstadoNube> = _estado.asStateFlow()

    init {
        auth.addAuthStateListener { a ->
            _correo.value = a.currentUser?.email
            if (a.currentUser == null) _estado.value = EstadoNube.SinCuenta
        }
    }

    private fun documento(uid: String) = firestore.collection("usuarios").document(uid)

    /** Código del usuario en la nube (lo necesita el script de Gmail). */
    val uid: String? get() = auth.currentUser?.uid

    /** Correos pendientes que dejó el script de Gmail en usuarios/{uid}/bandeja. */
    fun bandeja(): Flow<List<CorreoBandeja>> = callbackFlow {
        var registro: ListenerRegistration? = null
        val oyente = FirebaseAuth.AuthStateListener { a ->
            registro?.remove()
            registro = null
            val usuario = a.currentUser
            if (usuario == null) {
                trySend(emptyList())
            } else {
                registro = documento(usuario.uid).collection("bandeja").addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        trySend(
                            snap.documents.map {
                                CorreoBandeja(it.id, it.getString("texto").orEmpty(), it.getLong("fecha") ?: 0L)
                            }.sortedByDescending { it.fecha }
                        )
                    }
                }
            }
        }
        auth.addAuthStateListener(oyente)
        awaitClose {
            auth.removeAuthStateListener(oyente)
            registro?.remove()
        }
    }

    suspend fun quitarDeBandeja(id: String) {
        val usuario = auth.currentUser ?: return
        // Sin internet, Firestore guarda el borrado y lo envía después.
        withTimeoutOrNull(10_000L) { documento(usuario.uid).collection("bandeja").document(id).delete().await() }
    }

    /** Observa la base de datos y sube los cambios 2 segundos después del último. */
    @OptIn(FlowPreview::class)
    fun iniciarAutoGuardado() {
        alcance.launch {
            val fuentes: List<Flow<Any>> = listOf(
                repo.aportes, repo.prestamosConPagos, repo.deudas, repo.tipos, prefs.presupuestoDeudas,
                finanzas.movimientos, finanzas.categorias, finanzas.medios, finanzas.plantillas
            )
            combine(fuentes) { valores -> valores.toList() }
                .distinctUntilChanged()
                .drop(1)
                .onEach { if (!importando) prefs.marcarModificado() }
                .debounce(2_000L)
                .collect { if (!importando) subirSiHaySesion() }
        }
        // Si quedaron cambios sin subir (por ejemplo, se usó sin internet), se suben al abrir.
        alcance.launch {
            if (auth.currentUser != null && prefs.localModificado() > prefs.ultimaSincronizacion()) {
                subirSiHaySesion()
            }
        }
    }

    suspend fun subirSiHaySesion() {
        val usuario = auth.currentUser ?: return
        subir(usuario.uid)
    }

    private suspend fun subir(uid: String) = candado.withLock {
        _estado.value = EstadoNube.Sincronizando
        try {
            val datos = mapOf(
                "app" to "MiPropioBanco",
                "datos" to respaldo.exportarJson(),
                "modificado" to prefs.localModificado(),
                "dispositivo" to "${Build.MANUFACTURER} ${Build.MODEL}"
            )
            // Sin internet, Firestore guarda la escritura y la envía cuando vuelva la conexión.
            val ok = withTimeoutOrNull(15_000L) {
                documento(uid).set(datos).await()
                true
            }
            if (ok == true) {
                val ahora = System.currentTimeMillis()
                prefs.marcarSincronizado(ahora)
                _estado.value = EstadoNube.AlDia(ahora)
            } else {
                _estado.value = EstadoNube.Pendiente("Sin conexión: se subirá cuando haya internet")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _estado.value = EstadoNube.Pendiente(e.message ?: "No se pudo sincronizar")
        }
    }

    /**
     * Al iniciar sesión: si la nube tiene datos y este celular está vacío (o la nube es más
     * reciente), se descargan; si no, se sube lo que hay en el celular.
     */
    private suspend fun reconciliar(uid: String) {
        _estado.value = EstadoNube.Sincronizando
        val doc = documento(uid).get().await()
        val remoto = doc.getString("datos")
        val remotoModificado = doc.getLong("modificado") ?: 0L
        if (remoto != null && (respaldo.estaVacio() || remotoModificado > prefs.localModificado())) {
            importando = true
            try {
                respaldo.importarJson(remoto)
                delay(2_500L) // deja pasar los avisos de cambio que produce la importación
            } finally {
                importando = false
            }
            prefs.fijarModificado(remotoModificado)
            val ahora = System.currentTimeMillis()
            prefs.marcarSincronizado(ahora)
            _estado.value = EstadoNube.AlDia(ahora)
        } else {
            subir(uid)
        }
    }

    suspend fun iniciarSesion(correo: String, clave: String) {
        require(correo.isNotBlank() && clave.isNotBlank()) { "Escribe tu correo y tu contraseña" }
        val resultado = auth.signInWithEmailAndPassword(correo.trim(), clave).await()
        prefs.cambiarModoLocal(false)
        reconciliar(resultado.user?.uid ?: throw IllegalStateException("No se pudo iniciar sesión"))
    }

    suspend fun registrar(correo: String, clave: String) {
        require(correo.isNotBlank() && clave.isNotBlank()) { "Escribe tu correo y una contraseña" }
        val resultado = auth.createUserWithEmailAndPassword(correo.trim(), clave).await()
        prefs.cambiarModoLocal(false)
        reconciliar(resultado.user?.uid ?: throw IllegalStateException("No se pudo crear la cuenta"))
    }

    suspend fun recuperarClave(correo: String) {
        require(correo.isNotBlank()) { "Escribe tu correo para enviarte el enlace" }
        auth.sendPasswordResetEmail(correo.trim()).await()
    }

    /** Sube los datos, cierra la sesión y deja este celular limpio. */
    suspend fun cerrarSesion() {
        val usuario = auth.currentUser ?: return
        subir(usuario.uid)
        check(_estado.value is EstadoNube.AlDia) {
            "No se pudo guardar en la nube. Conéctate a internet antes de cerrar sesión."
        }
        importando = true
        try {
            auth.signOut()
            respaldo.borrarTodo()
            prefs.reiniciarSincronizacion()
            delay(2_500L)
        } finally {
            importando = false
        }
    }

    companion object {
        fun mensajeError(e: Throwable): String = when (e) {
            is FirebaseAuthWeakPasswordException -> "La contraseña debe tener al menos 6 caracteres"
            is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo. Inicia sesión."
            is FirebaseAuthInvalidUserException -> "No existe una cuenta con ese correo"
            is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos"
            is FirebaseNetworkException -> "Sin conexión a internet"
            else -> e.message ?: "Ocurrió un error inesperado"
        }
    }
}
