# Tema Cyberpunk para launcher Android (headunit)

Tema "Cyberpunk" para el launcher `com.douglas.temas`: HUD de neón con un solo tono configurable,
paneles con esquinas cortadas, reproductor con visualizador, camioneta animada (Grand Vitara) y
una intro de arranque que corre una vez por encendido de la radio.

**Vista previa (referencia de diseño):** abre `docs/preview.html` en el navegador; el código Kotlin replica ese diseño con las mismas proporciones.

## Contenido
- `app/src/main/java/com/douglas/temas/` — código Kotlin (`CyberpunkTheme.kt`, `Cyber.kt`, `CyberIntro.kt`, …)
- `app/src/main/assets/` — `truck.webp`, `wheel_f.webp`, `wheel_r.webp`
- `app/src/main/AndroidManifest.xml`, `app/src/main/res/` — manifest (launcher HOME + servicio de notificaciones) e ícono
- `app/build.gradle`, `build.gradle`, `settings.gradle`, `gradle.properties` — proyecto Gradle (AGP 8.2.2, Kotlin 1.9.22, compileSdk 34, minSdk 24, targetSdk 27)
- `.github/workflows/build.yml` — compila el APK en GitHub Actions (pestaña Actions → Artifacts)
- `docs/preview.html` — vista previa de diseño de referencia
- `CAMBIOS.md` — historial de cambios (v10 → v13)

## Compilar
- **En GitHub:** sube el repo; Actions genera `temas-debug-apk`.
- **Local:** `gradle assembleDebug` (Gradle 8.5 + JDK 17) o abrir la carpeta en Android Studio.
- Instala el APK, ábrelo y activa el acceso a notificaciones para «Temas» (para ver la música que suena).

## Uso
- Mantener pulsado un ícono: elegir otra app.
- 5 toques sobre la hora: mostrar/ocultar el selector de color del HUD.
- Mantener pulsado el botón de cuadrícula: cambiar de tema.
- Un toque durante la intro: la salta.

## Fuente opcional
La palabra "GRAND VITARA" de la intro usa *Racing Sans One* (licencia OFL). Copia `racing_sans_one.ttf` a `app/src/main/assets/`
para que se vea igual que la vista previa; sin el archivo se usa una fuente del sistema parecida.
