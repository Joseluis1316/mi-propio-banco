package com.mipropiobanco.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/** Guarda en el celular los errores y cierres inesperados para poder revisarlos (Ajustes › Exportar). */
object RegistroErrores {

    private var archivo: File? = null
    private const val MAXIMO = 200_000L

    fun iniciar(contexto: Context) {
        archivo = File(contexto.filesDir, "errores.log")
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            anotar("CIERRE INESPERADO en ${hilo.name}", error)
            anterior?.uncaughtException(hilo, error)
        }
    }

    @Synchronized
    fun anotar(mensaje: String, error: Throwable? = null) {
        val f = archivo ?: return
        runCatching {
            if (f.exists() && f.length() > MAXIMO) {
                val resto = f.readText().takeLast((MAXIMO / 2).toInt())
                f.writeText(resto)
            }
            val traza = error?.let { e ->
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                sw.toString().lines().take(25).joinToString("\n")
            }.orEmpty()
            f.appendText("[${LocalDateTime.now()}] $mensaje\n$traza\n")
        }
    }

    fun contenido(): String {
        val f = archivo ?: return ""
        val cabecera = "Mi Propio Banco · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n\n"
        return cabecera + (if (f.exists()) f.readText() else "Sin errores registrados.")
    }

    fun cantidad(): Int {
        val f = archivo ?: return 0
        return if (f.exists()) f.readLines().count { it.startsWith("[") } else 0
    }

    fun borrar() {
        archivo?.delete()
    }
}
