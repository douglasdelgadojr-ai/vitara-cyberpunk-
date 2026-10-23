# Tema Cyberpunk – qué cambió

Archivos NUEVOS (copiar a app/src/main/java/com/douglas/temas/):
  Cyber.kt             paleta (un solo tono = todo el neón), paneles con esquinas cortadas, glifos
  CyberpunkTheme.kt    el tema + camioneta animada + reproductor + selector de color oculto

Archivos NUEVOS (copiar a app/src/main/assets/):
  truck.webp, wheel_f.webp, wheel_r.webp   (sacados de tu HTML)

Archivos MODIFICADOS (reemplazar):
  IconStyle.kt     nuevo estilo "Cyberpunk" (por defecto) + parámetro forceMode
  Theme.kt         ThemeHost.rebuildTheme() y NowPlaying.album
  MediaListener.kt toma la carátula de la notificación (Spotify/Bluetooth) como respaldo
  MainActivity.kt  tema Cyberpunk primero en la lista, carátula/álbum/Bluetooth, cajón en cyberpunk
  app/build.gradle versionCode 10 / versionName 1.0

No cambian: AndroidManifest.xml, settings.gradle, build.gradle raíz, build.yml, BjTheme, DashboardTheme, PanelTheme, CardsTheme, TransportIcon.

Uso:
  - Mantener pulsado un ícono (barra lateral, tarjetas o panel de la camioneta) = elegir otra app.
  - Tocar 5 veces la hora = mostrar / ocultar el color del HUD.
  - Mantener pulsado el botón de cuadrícula = cambiar de tema.

--- Ajuste v11: igualar a tema_cyberpunk_preview.html ---
CyberpunkTheme.kt: íconos de la barra lateral dentro de paneles con esquinas cortadas; tarjetas e íconos con el tamaño de la vista previa;
  frecuencia de radio grande ("98.5" + "FM" pequeño en neón); "GRAND VITARA" en ámbar y negrita; líneas de escaneo al 28 %;
  destellos (streaks) sobre la escena; barra inferior más alta; paso de física hasta 50 ms.

--- v12: intro de arranque ---
NUEVO  CyberIntro.kt   intro del diseño "Grand Vitara A": consola que escribe, destello, rinoceronte que corre y revela GRAND VITARA,
                        glitch y la camioneta entra desde la izquierda. Un toque la salta.
Cyber.kt               introStart(): la intro corre UNA vez por encendido (usa boot_count del sistema). Después solo queda el ciclo
                        de la camioneta cruzando la roca.
CyberpunkTheme.kt      pone la intro encima de la escena; la camioneta y las ruedas entran desplazadas al final.

--- v13: igualar EXACTO a "Tema Cyberpunk – vista previa con intro" (docs/preview.html) ---
CyberpunkTheme.kt  Todo el diseño ahora usa proporciones del ancho de pantalla (1 cq = 1 % del ancho, igual que cqw del HTML), así se ve igual en
                   cualquier resolución: margen 1.3, barra lateral 7.4, columnas 1 : 1.62, tarjetas 24 % del alto, barra inferior 9.4, huecos 1.1.
                   Textos en cq (frecuencia 5.4, título 2.1, artista 1.5, etc.), botones 3.6 / 4.4, glifos y paneles con las esquinas del diseño.
                   HUD de la camioneta (2.6 / 1.15 cq), barra de suspensión (11 x 0.7 cq, degradado sobre la parte llena), marcas de esquina 1.6 cq.
                   Resplandor del fondo y de la escena como elipses (igual que radial-gradient del CSS) y degradados que no oscurecen al desvanecerse.
Cyber.kt           CutDrawable con degradados CSS exactos (135° borde, 160° relleno); CyberBackdrop (fondo); glifo PLAY igual al diseño; textPx().
CyberIntro.kt      La intro espera 1.1 s con la escena normal y luego "enciende" (igual que el diseño). Fuente Racing Sans One opcional:
                   copia racing_sans_one.ttf a app/src/main/assets/ (si no está, usa una negrita cursiva del sistema).
IconStyle.kt       Placa de ícono como en el diseño: fondo plano, borde neón, líneas de escaneo cada 7 % y marcas de esquina ámbar.
app/build.gradle   versionCode 13 / versionName 1.2

No compilé en Android (no hay SDK aquí): si Gradle marca algún error de sintaxis, pásamelo y lo corrijo.

--- v13 (paquete completo para GitHub) ---
NUEVOS: AndroidManifest.xml (launcher HOME + servicio MediaListener + permisos), res/drawable/ic_launcher.xml, settings.gradle, build.gradle (raíz),
        gradle.properties, .github/workflows/build.yml (compila el APK en Actions), .gitignore, README.md, docs/preview.html.
Si ya tienes tus propios AndroidManifest.xml / build.yml, conserva los tuyos: el código Kotlin no depende de nada más.
