# Mi Propio Banco · v9.5

App Android personal (Kotlin · Jetpack Compose · Room · Firebase) para manejar **todo mi dinero en un solo lugar**: ingresos, gastos, deudas, metas y un fondo de autofinanciamiento ("Mi banco") que presta a mí mismo y a otras personas. Funciona sin internet (local-first) y respalda todo en la nube. Moneda: pesos colombianos sin decimales.

> Uso personal. Se distribuye por GitHub Releases + Obtainium (no está en Play Store).

---

## Pestañas

| Pestaña | Qué hace |
|---|---|
| **Inicio** | Lo que tengo hoy (cuentas + Mi banco − deudas = patrimonio, con modo privado 👁), **🧮 Cuadre del mes**, acciones rápidas, **⚡ En 1 toque**, avisos, gasto por categoría, metas, préstamos activos y deudas. |
| **Ingresos** | Cuánto entró (ganado vs. prestado), comparación con el mes pasado, ingresos fijos por llegar, dona de fuentes y lista. Botones: Ingreso, Hablar, Nómina (foto), Me prestaron. |
| **Gastos** | Registro con IA (foto, voz, texto), resumen y cuadre del mes, saldos por cuenta (Ajustar · Desde), dona por categoría, comparación, **reporte PDF**, presupuesto, filtros (Por clasificar), deshacer y correos del banco. |
| **Agenda** | Calendario con ingresos, gastos, fijos programados, cuotas de préstamos y pagos de deudas. |
| **Banco** | **Aportes**, **Autopréstamos** (a mí) y **Préstamos** (a otras personas), con cuota fija (sistema francés), cronómetro y mora diaria. |
| **Deudas** | Bola de nieve: menor saldo primero, mínimos en las demás, **fecha exacta del próximo pago** (calendario) que avanza sola al pagar, dona de a quién le debo. |

## Funciones principales

**Dinero que siempre cuadra**
- Cuadre del mes: *empecé con + gané + me prestaron + saqué de Mi banco − gasté − pagué deudas − ahorré = tengo hoy*.
- Si una cuenta queda en negativo, la app pregunta de dónde salió la plata: ingreso no anotado, préstamo (crea la deuda) o saldo que ya tenía.
- "¿No empezaste con eso?": corrige con cuánto empezó el mes y explica la diferencia.
- Ingreso en categoría **🤝 Préstamos recibidos** = ingreso + deuda nueva automáticamente.
- Traslados entre cuentas propias (Bancolombia ↔ Nequi/Daviplata) se detectan y no cuentan doble.
- Retiros de cajero = gasto en *Efectivo (retiros)*.

**Mi banco**
- Bóveda con aportes; capital total, disponible, prestado y autointereses ganados.
- Autopréstamos y préstamos a terceros: tasas E.A. editables por tipo (base: usura Superfinanciera), plazos diarios, semanales, quincenales, mensuales o anuales, tabla de amortización y **mora diaria compuesta** `cuota·((1+EA)^(días/365)−1)`.
- Opcional "Conectar Mi banco": aporte y cuota de autopréstamo salen de la cuenta como gasto; el autopréstamo entra como ingreso. Los préstamos a terceros solo mueven la bóveda.

**Registro automático**
- **Correos de Bancolombia** vía Google Apps Script (`scripts/LectorCorreosBancolombia.gs`): cada 15 min o con el botón **🔄 Buscar correos ahora**. Entiende Pagaste, Compraste, Transferiste (incl. Botón Bancolombia), Recibiste, Retiraste y un formato genérico.
- **Notificaciones de Nequi y Daviplata** (NotificationListenerService).
- **Capturas del extracto**: una imagen con varios movimientos se separa uno por uno (Gasto · Ingreso · Me prestaron · Omitir).
- Toda salida de plata se anota sola como gasto (🏷️ *Por clasificar* si no conoce la categoría). Duplicados exactos se descartan; los parecidos quedan para revisar.
- La app aprende cómo clasificas cada comercio o cuenta.

**Registro manual**
- Botón, voz ("gasté 45 mil en mercado con Nequi"), texto, foto de recibo (OCR ML Kit en el celular) y desprendible de nómina por conceptos.
- **⚡ En 1 toque**: tus movimientos frecuentes como botones.
- Plantillas fijas (diarias a anuales), categorías con presupuesto y medios de pago con saldo.

**Metas y reportes**
- Metas de ahorro con objetivo, fecha, progreso y cuánto separar al mes.
- Reporte mensual en **PDF**.
- Recordatorios: pagos de deudas y cuotas 2 días antes, atrasos y presupuesto excedido.

**Seguridad y respaldos**
- Bloqueo con huella/rostro/PIN, modo privado.
- **↩️ Deshacer** hasta 5 pasos antes de cualquier cambio grande.
- Copia automática diaria en el celular (7 días) y en una **carpeta elegida** (ej. Documentos).
- Nube: respaldo JSON comprimido (gzip) en Firestore `usuarios/{uid}`.
- Registro de errores exportable (Ajustes › Diagnóstico).

## Cuenta y nube (Firebase)

1. Proyecto en console.firebase.google.com con app Android `com.mipropiobanco`; `google-services.json` en `app/`.
2. *Authentication*: Correo/contraseña. *Firestore*: modo producción con las reglas de `firestore.rules`.

## Correos de Bancolombia (una sola vez)

1. Gmail: filtro de `notificacionesbancolombia.com OR bancolombia.com.co OR bancolombia.com` → etiqueta **MiBanco**.
2. script.google.com: nuevo proyecto con `scripts/LectorCorreosBancolombia.gs` y `scripts/appsscript.json`; poner el **UID** (Ajustes › Correos de Bancolombia › Copiar código) y ejecutar `instalar`.
3. *Implementar › Aplicación web* (Ejecutar como: yo · Acceso: cualquier usuario) y pegar la URL `/exec` en Ajustes para el botón **Buscar correos ahora**.

## Actualización automática

Cada `git push` a `main` corre las pruebas (informativas) y compila en GitHub Actions; publica un Release con `MiPropioBanco.apk` firmado siempre con la misma llave (`app/keystore`). En el celular, **Obtainium** apunta a este repositorio e instala cada versión sin perder datos.

## Estructura

```
app/src/main/java/com/mipropiobanco/
├── MainActivity.kt / MiPropioBancoApp.kt   # Bloqueo biométrico, contenedor de dependencias
├── avisos/        LectorNotificaciones (Nequi/Daviplata) · Recordatorios (WorkManager)
├── data/local/    Entidades, Daos, BancoDatabase (Room v7, migraciones sin pérdida)
├── data/repository/ BancoRepository (banco y deudas) · FinanzasRepository (ingresos/gastos)
├── data/backup/   BackupManager (JSON v3 + CSV)
├── data/sync/     SincronizacionNube (Auth + Firestore, bandeja de correos)
├── data/prefs/    AjustesPreferencias
├── domain/        Modelos, BolaDeNieve, Presupuesto, Cuadre, Metas,
│                  Interprete (voz/texto/recibo/nómina), InterpreteCorreo, InterpreteExtracto
├── ui/            BancoViewModel · theme · navigation · components (Gráficas, TarjetaCuadre…)
│   └── screens/   Dashboard, Ingresos, Movimientos (Gastos), Calendario, Banco, Aportes,
│                  Prestamos, NuevoPrestamo, DetallePrestamo, Deudas, DetalleDeuda, Metas,
│                  Bandeja, DialogoExtracto, ConfiguracionFinanzas, Ajustes, Login
└── util/          Formato, Fotos, ReportePdf, RegistroErrores
app/src/test/      Pruebas de intérpretes y saldos
scripts/           Google Apps Script para Gmail
```

**Stack:** Kotlin 2.0.21 · AGP 8.7.3 · Compose BOM 2024.12 · Material 3 · Room 2.6.1 · Firebase BoM 33.7 · ML Kit Text Recognition · Biometric · WorkManager · minSdk 26 · targetSdk 35.

## Cómo se calculan los saldos

Todo sale de los movimientos, así que nada se descuadra:
- **Saldo de una cuenta** = saldo inicial + ingresos − gastos ± traslados.
- **Disponible en bóveda** = aportes − préstamos desembolsados + capital recuperado + intereses y mora cobrados.
- **Capital total** = disponible + prestado por recuperar.
- **Patrimonio** = cuentas + Mi banco − deudas.

## Historial de versiones

| Versión | Cambios |
|---|---|
| 9.5 | Mi banco presta a otras personas; fechas exactas de pago en deudas (Room v6–v7). |
| 9.3 | Corregir con cuánto empezó el mes en el cuadre. |
| 9.2 | Cuadre del mes y plata sin origen. |
| 9.1 | Módulo de Ingresos. |
| 9.0 | Nube comprimida, deshacer de 5 pasos, copia en carpeta, registro de errores, En 1 toque, traslados automáticos, pruebas. |
| 8.x | Extracto uno por uno, préstamos recibidos = deuda, diseño premium, metas, PDF, notificaciones Nequi/Daviplata, recordatorios, huella, modo privado. |
| 6.x–7.x | Correos de Bancolombia, bandeja, traslados, saldos por cuenta, unir cuentas, Desde, botón Buscar correos. |
| 1–5 | Autopréstamos, mora, bola de nieve, presupuesto, calendario, nube, IA (voz, texto, recibos, nómina). |
