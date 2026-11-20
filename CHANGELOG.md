# Changelog

## Artemis 2026 1.0.0 - 2026-11-20

Primera release pública del fork Artemis 2026, basada en Artemis 20.2.7 y Moonlight v12.2.

### Identidad y publicación

- Nuevo `applicationId`: `com.leonidas10009.artemis2026`.
- Etiqueta unificada **Artemis** para debug y release.
- APKs de release firmados; keystore y contraseña fuera del repositorio.
- APKs publicados para `arm64-v8a`, `armeabi-v7a` y `x86_64`.
- Soporte Android TV mediante `LEANBACK_LAUNCHER` y banner de TV.

### Interfaz

- Tokens y estilo Apollo 2026.
- Toolbar, búsqueda, tarjetas de PCs/apps, estados vacíos y skeletons de carga.
- Hoja de acciones para apps (UI-05).
- Diseño responsive móvil/tablet con recursos `sw600dp` (UI-22).
- Perfiles como tarjetas con estado activo y cuadrícula en tablet.
- Icono Artemis 2026 con fondo original y foreground adaptativo al 78%.

### Funciones

- Energía remota desde la página del PC: apagar, reiniciar y cancelar apagado.
  - Requiere Apollo 1.0.1 o posterior y permiso `server_cmd`.
- Acciones de app: iniciar/reanudar, cerrar, cerrar e iniciar, ocultar/mostrar,
  detalles, acceso directo y exportación `.art`.

### Base y correcciones

- Base Artemis 20.2.7 + Moonlight v12.2.
- Núcleo `moonlight-common-c` fusionado y vendorizado.
- Toolchain: AGP 9.4.0, Gradle 9.7.1, compileSdk 37, targetSdk 36,
  NDK 29.0.14206865 y Java 17.
- Corrección del resumen español de deadzone.
- Exportación automática de APK a `Release/`.

### Limitaciones conocidas

- La tarea de tests unitarios está pendiente de estabilizar en Windows/Gradle
  (fallo del worker: `ClassNotFoundException: Files`).
- Falta prueba en tablet/TV físico y la prueba end-to-end de energía.
- El APK release no se publica en Google Play; solo GitHub Releases.
