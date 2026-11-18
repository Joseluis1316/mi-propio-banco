# Mi Propio Banco (Android · Kotlin · Jetpack Compose · Room)

App para administrar tu fondo de autofinanciamiento y salir de deudas. Funciona sin internet y, con cuenta, respalda todo en la nube.

## Funciones

- **Bóveda y aportes:** capital total, disponible, prestado y autointereses ganados.
- **Autopréstamos:** tipos de préstamo con tasas editables (por defecto, la tasa de usura vigente de la Superfinanciera), cuotas diarias, semanales, quincenales, mensuales o anuales, cuota fija (sistema francés) y tabla con fechas de vencimiento.
- **Cronómetro y mora:** cuenta regresiva hasta cada vencimiento; si pagas tarde se cobra interés de mora por día de atraso, que también entra a la bóveda.
- **Deudas con terceros (bola de nieve):** orden de ataque de menor a mayor saldo, pago mínimo en las demás y excedente al objetivo; cuando liquidas una, su pago se suma a la siguiente. Muestra cuánto pagar este mes, cuándo quedas libre y el cronómetro de cada fecha de pago.
- **Respaldo:** exportar/importar JSON y exportar CSV.

## Cuenta y nube (Firebase)

La app funciona sin internet con su base local. Si inicias sesión con correo, cada cambio se sube solo a Firestore (documento privado `usuarios/{tu-uid}`), y al instalarla en otro celular basta con iniciar sesión.

Configuración (una sola vez):
1. En console.firebase.google.com crea un proyecto y agrega una app **Android** con paquete `com.mipropiobanco`.
2. Descarga `google-services.json` y ponlo en la carpeta `app/`.
3. En *Authentication › Sign-in method* activa **Correo electrónico/contraseña**.
4. En *Firestore Database* crea la base (modo producción) y en *Reglas* pega el contenido de `firestore.rules`.

## Actualización automática

Cada `git push` a `main` compila la app en GitHub Actions y publica una versión en *Releases* con `MiPropioBanco.apk`. Todas las versiones se firman con la misma llave (`app/keystore`), así que se instalan encima de la anterior sin perder datos.

Para que el celular se actualice solo, instala **Obtainium** (github.com/ImranR98/Obtainium/releases), toca *Add App*, pega la URL de este repositorio y pulsa *Install*. Obtainium revisa las nuevas versiones y te avisa o las instala.

## Estructura del proyecto

```
MiPropioBanco/
├── settings.gradle.kts            # Repositorios y módulos
├── build.gradle.kts               # Versiones de plugins (AGP 8.7.3, Kotlin 2.0.21, KSP)
├── gradle.properties
├── gradlew / gradlew.bat          # Gradle Wrapper (Gradle 8.9)
├── gradle/wrapper/
├── PLAN_FINANCIERO.md             # Reglamento, tabla y comparativo
└── app/
    ├── build.gradle.kts           # Dependencias: Compose BOM, Material 3, Room, Navigation
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                   # Ícono adaptable, colores, tema base
        └── java/com/mipropiobanco/
            ├── MiPropioBancoApp.kt          # Contenedor de dependencias
            ├── MainActivity.kt              # Tema claro/oscuro + navegación
            ├── data/
            │   ├── local/Entidades.kt       # Aporte, TipoPréstamo, Préstamo, Pago, Deuda, Abono (Room)
            │   ├── local/Daos.kt
            │   ├── local/BancoDatabase.kt
            │   ├── repository/BancoRepository.kt   # Reglas de negocio y validaciones
            │   ├── backup/BackupManager.kt  # Exportar/importar JSON, exportar CSV
            │   └── prefs/AjustesPreferencias.kt    # Modo de tema
            ├── domain/Modelos.kt            # Periodicidad, tasas, mora, amortización y resumen
            ├── domain/BolaDeNieve.kt        # Plan de pago de deudas (bola de nieve)
            ├── util/Formato.kt              # $ COP sin decimales, fechas, tasas
            └── ui/
                ├── BancoViewModel.kt
                ├── theme/Tema.kt
                ├── components/Componentes.kt   # Tarjetas, tabla, campo de pesos
                ├── navigation/AppNavegacion.kt
                └── screens/
                    ├── DashboardScreen.kt
                    ├── AportesScreen.kt
                    ├── PrestamosScreen.kt
                    ├── NuevoPrestamoScreen.kt   # Simulador + aprobación
                    ├── DetallePrestamoScreen.kt # Pagos, mora y tabla
                    ├── DeudasScreen.kt          # Bola de nieve
                    ├── DetalleDeudaScreen.kt    # Pagos a cada deuda
                    └── AjustesScreen.kt         # Tema, tipos/tasas, respaldo, reglamento
```

## Cómo funcionan los saldos

Todo se calcula a partir de los movimientos, así nunca se descuadra:

- **Disponible en bóveda** = aportes − préstamos desembolsados + capital recuperado + intereses cobrados
- **Prestado por recuperar** = préstamos desembolsados − capital recuperado
- **Capital total** = disponible + por recuperar (= aportes + autointereses)

Al pagar una cuota, el capital vuelve a la bóveda y el interés se suma a la bóveda y al contador de autointereses.

## Guía: compilar el APK e instalarlo

1. **Instala Android Studio** (versión Ladybug 2024.2 o más reciente) desde developer.android.com/studio. Trae su propio JDK; no necesitas instalar Java aparte.
2. **Abre el proyecto:** descomprime el .zip, y en Android Studio usa *File › Open* y elige la carpeta `MiPropioBanco` (la que contiene `settings.gradle.kts`).
3. **Espera la sincronización de Gradle** (barra inferior). La primera vez descarga librerías (necesita internet solo para compilar; la app no lo usa). Si Android Studio ofrece actualizar el "Android Gradle Plugin", puedes aceptar o ignorarlo.
4. **Si falta el SDK:** cuando pida "Android SDK Platform 35", acepta la instalación (o *Tools › SDK Manager › Android 15.0 (API 35)*).
5. **Genera el APK:** *Build › Build App Bundle(s) / APK(s) › Build APK(s)*. Al terminar, toca *locate*: el archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.
   - APK más liviano (optimizado): en la pestaña *Terminal* de Android Studio ejecuta `./gradlew assembleRelease` (en Windows: `gradlew.bat assembleRelease`). Queda en `app/build/outputs/apk/release/app-release.apk`, firmado con la llave de depuración para uso personal.
6. **Instálalo en tu teléfono**, de una de estas dos formas:
   - **Por cable (recomendado):** en el teléfono ve a *Ajustes › Acerca del teléfono* y toca 7 veces *Número de compilación* para activar las Opciones de desarrollador; luego activa *Depuración USB*. Conecta el teléfono, acepta la huella del computador y pulsa ▶ *Run* en Android Studio.
   - **Copiando el APK:** pásalo al teléfono (cable, Drive o correo), ábrelo con el explorador de archivos y permite *Instalar apps de origen desconocido* para esa app cuando lo pida.
7. **Primer uso:** en *Aportes* registra tu capital inicial; luego crea tu primer autopréstamo desde *Préstamos › Nuevo préstamo*.

### Problemas frecuentes

- **"Gradle JDK" o versión de Java incompatible:** *Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK* → elige el JDK incluido (jbr-17 o jbr-21).
- **"SDK location not found":** abre el proyecto desde Android Studio (crea `local.properties` solo) o verifica la ruta en *SDK Manager*.
- **Play Protect avisa al instalar:** es normal en apps instaladas fuera de la Play Store; elige *Instalar de todas formas*.

## Respaldo

*Ajustes › Respaldo de datos*: exporta un JSON completo (para restaurar en otro teléfono) o un CSV de movimientos separado por punto y coma (se abre directo en Excel en español). Importar un JSON reemplaza todos los datos actuales.
