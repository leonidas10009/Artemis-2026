# Changelog

## Artemis 2026 1.0.3 - 2026-11-21

UI update for application cards and Apollo branding.

- Application cards now use a full-poster `fitCenter` foreground with a
  darkened, center-cropped copy behind it, matching the Apollo Web UI cards
  without cropping square or landscape artwork.
- Pairing and host-discovery help strings now refer to **Apollo** instead of
  Sunshine across the localized string resources. Launcher export labels are
  now generic or refer to Apollo.
- Version `1.0.3` (versionCode `4`); release APKs signed for `arm64-v8a`,
  `armeabi-v7a`, `x86_64` and universal.

## Artemis 2026 1.0.2 - 2026-11-21

Corrección definitiva del bloqueo de Play Protect.

- Eliminado el servicio de accesibilidad `KeyboardAccessibilityService` y su
  configuración `res/xml/keyboard_accessibility_service.xml`. Play Protect bloquea
  las apps instaladas desde Internet que declaran `ACCESSIBILITY`, aunque el servicio
  sea opcional y la app nunca lo active.
- Eliminadas las cadenas `keyboard_service_label` y `accessibility_description_text`
  de los idiomas que las tenían.
- El teclado físico sigue funcionando mediante `KeyEvent` y la API
  `SemWindowManager` de Samsung. La captura nativa de Android 16.1+ requiere
  `CAPTURE_KEYBOARD` (nivel normal, no incluido en la lista de Play Protect); se
  omite en 1.0.2 para minimizar permisos y puede restaurarse si hace falta.
- El APK final ya no contiene servicios de accesibilidad ni permisos sensibles.
  Solo conserva permisos habituales: red, notificaciones, vibración, wake lock,
  estado de Wi-Fi y EPG de TV.
- Versión `1.0.2` (versionCode `3`).
- APKs firmados para `arm64-v8a`, `armeabi-v7a`, `x86_64` y universal.

## Artemis 2026 1.0.1 - 2026-11-21

Corrección de instalación y Play Protect.

- Eliminado el permiso `CAPTURE_KEYBOARD`, que el Artemis original no declara. El
  código sí lo usa para la captura nativa de Android 16.1, pero se retiró para
  minimizar permisos; no está en la lista de Play Protect.
- Eliminados los permisos heredados de `org.tensorflow.lite.gpu.api`:
  `READ_PHONE_STATE`, `READ_EXTERNAL_STORAGE` y `WRITE_EXTERNAL_STORAGE`.
- Se conservó el servicio de accesibilidad para captura de teclado, igual que Artemis
  original. Esta decisión fue insuficiente: el servicio seguía disparando el bloqueo
  de Play Protect y se retiró en 1.0.2.
- APKs arm64, armv7, x86_64 y universal firmados.
- Verificado en Android 13 y 16 x86_64: instalación y arranque correctos.

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
