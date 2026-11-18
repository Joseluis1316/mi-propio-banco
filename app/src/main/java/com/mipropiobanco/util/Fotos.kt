package com.mipropiobanco.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Manejo de fotos de recibos: se guardan reducidas y derechas en la memoria privada de la app. */
object Fotos {
    private const val LADO_MAXIMO = 1600

    fun carpeta(context: Context): File = File(context.filesDir, "recibos").apply { mkdirs() }

    /** Archivo temporal donde la cámara deja la foto original. */
    fun archivoTemporal(context: Context): File = File(carpeta(context), "tmp_${System.currentTimeMillis()}.jpg")

    fun uriCompartible(context: Context, archivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)

    /** Copia la imagen (de la cámara o la galería) reducida, girada según EXIF, y devuelve su ruta. */
    fun guardarReducida(context: Context, origen: Uri): String {
        val resolver = context.contentResolver
        // Con inJustDecodeBounds, decodeStream siempre devuelve null: solo llena las medidas.
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val entrada = resolver.openInputStream(origen) ?: throw IllegalStateException("No se pudo abrir la imagen")
        entrada.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) {
            throw IllegalStateException("El archivo no es una imagen compatible")
        }
        var muestra = 1
        while (max(limites.outWidth, limites.outHeight) / (muestra * 2) >= LADO_MAXIMO) muestra *= 2

        val original = resolver.openInputStream(origen)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
        } ?: throw IllegalStateException("No se pudo leer la imagen")

        val orientacion = runCatching {
            resolver.openInputStream(origen)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val grados = when (orientacion) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        var imagen = original
        if (grados != 0f) {
            imagen = Bitmap.createBitmap(original, 0, 0, original.width, original.height, Matrix().apply { postRotate(grados) }, true)
        }
        val lado = max(imagen.width, imagen.height)
        if (lado > LADO_MAXIMO) {
            val escala = LADO_MAXIMO.toFloat() / lado
            imagen = Bitmap.createScaledBitmap(imagen, (imagen.width * escala).toInt(), (imagen.height * escala).toInt(), true)
        }
        val destino = File(carpeta(context), "recibo_${System.currentTimeMillis()}.jpg")
        FileOutputStream(destino).use { imagen.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return destino.absolutePath
    }

    /** Carga una versión pequeña para mostrar en pantalla. */
    fun miniatura(ruta: String, ladoMaximo: Int = 900): Bitmap? {
        val archivo = File(ruta)
        if (!archivo.exists()) return null
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, limites)
        var muestra = 1
        while (max(limites.outWidth, limites.outHeight) / (muestra * 2) >= ladoMaximo) muestra *= 2
        return BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = muestra })
    }
}
