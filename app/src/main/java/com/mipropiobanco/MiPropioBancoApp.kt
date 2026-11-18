package com.mipropiobanco

import android.app.Application
import com.mipropiobanco.data.backup.BackupManager
import com.mipropiobanco.data.local.BancoDatabase
import com.mipropiobanco.data.prefs.AjustesPreferencias
import com.mipropiobanco.data.repository.BancoRepository

/** Contenedor de dependencias sencillo (sin librerías de inyección). */
class MiPropioBancoApp : Application() {
    val baseDatos: BancoDatabase by lazy { BancoDatabase.crear(this) }
    val repositorio: BancoRepository by lazy { BancoRepository(baseDatos) }
    val respaldo: BackupManager by lazy { BackupManager(baseDatos) }
    val preferencias: AjustesPreferencias by lazy { AjustesPreferencias(this) }
}
