package com.mipropiobanco

import android.app.Application
import com.mipropiobanco.data.backup.BackupManager
import com.mipropiobanco.data.local.BancoDatabase
import com.mipropiobanco.data.prefs.AjustesPreferencias
import com.mipropiobanco.data.repository.BancoRepository
import com.mipropiobanco.data.repository.FinanzasRepository
import com.mipropiobanco.data.sync.SincronizacionNube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Contenedor de dependencias sencillo (sin librerías de inyección). */
class MiPropioBancoApp : Application() {
    private val alcanceApp = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val baseDatos: BancoDatabase by lazy { BancoDatabase.crear(this) }
    val preferencias: AjustesPreferencias by lazy { AjustesPreferencias(this) }
    val repositorio: BancoRepository by lazy { BancoRepository(baseDatos) }
    val finanzas: FinanzasRepository by lazy { FinanzasRepository(baseDatos) }
    val respaldo: BackupManager by lazy { BackupManager(baseDatos, preferencias) }
    val nube: SincronizacionNube by lazy { SincronizacionNube(respaldo, repositorio, finanzas, preferencias, alcanceApp) }

    override fun onCreate() {
        super.onCreate()
        nube.iniciarAutoGuardado()
        runCatching { com.mipropiobanco.avisos.Recordatorios.programar(this) }
    }
}
