# Artemis 2026

[![Release](https://img.shields.io/github/v/release/leonidas10009/Artemis-2026?label=release)](https://github.com/leonidas10009/Artemis-2026/releases)
[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE.txt)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Android%20TV-green)](https://github.com/leonidas10009/Artemis-2026/releases)

Cliente Android de streaming para **Apollo/Sunshine**, basado en **Artemis (Moonlight Noir)** y **Moonlight**.

> **Versión:** `1.0.2` (versionCode `3`)  
> **Paquete release:** `com.leonidas10009.artemis2026`  
> **Paquete debug:** `com.leonidas10009.artemis2026.debug`  
> **Base:** Artemis `20.2.7` + Moonlight `v12.2` (`b48494c`)  
> **Repositorio:** <https://github.com/leonidas10009/Artemis-2026>  
> **Releases:** <https://github.com/leonidas10009/Artemis-2026/releases>  
> **Memoria técnica:** [`MEMORIA_ARTEMIS.md`](MEMORIA_ARTEMIS.md)

**Este es un fork no oficial.** No está afiliado a ClassicOldSong, Moonlight Stream ni LizardByte.

## Origen y atribución

- **Moonlight original:** <https://github.com/moonlight-stream/moonlight-android>
- **Artemis / Moonlight Noir:** <https://github.com/ClassicOldSong/moonlight-android>
- **Apollo 2026 (host de este fork):** <https://github.com/leonidas10009/Apollo_2026>
- **Apollo original:** <https://github.com/ClassicOldSong/Apollo>
- **Este fork:** <https://github.com/leonidas10009/Artemis-2026>
- **Licencia:** GPL-3.0. Consulta [`LICENSE.txt`](LICENSE.txt).

## Descargas e instalación

Los APKs se publican en [GitHub Releases](https://github.com/leonidas10009/Artemis-2026/releases) y están firmados con la clave de release del proyecto.

| APK | Uso recomendado |
|---|---|
| `Artemis-1.0.2-arm64-v8a.apk` | Móviles, tablets y Android TV modernos (recomendado) |
| `Artemis-1.0.2-armeabi-v7a.apk` | Dispositivos y TV de 32 bits más antiguos |
| `Artemis-1.0.2-x86_64.apk` | Chromebooks, emuladores y equipos x86_64 |
| `Artemis-1.0.2-universal.apk` | Fallback si el instalador rechaza el APK de una ABI; incluye todas |

### SHA-256

| APK | SHA-256 |
|---|---|
| `Artemis-1.0.2-arm64-v8a.apk` | `D32CBFBA09195CA2C5074A88B8C037D6FCB519BEB8DB674862246F6EFB1D0827` |
| `Artemis-1.0.2-armeabi-v7a.apk` | `7917886148AB03A3CB67728DF0345C31A5BC3BC38F4BBDF81DED5224637055F2` |
| `Artemis-1.0.2-x86_64.apk` | `254FA552A87A792B6BAFBC75AF091C40703C33823802975F748892DA98124524` |
| `Artemis-1.0.2-universal.apk` | `35406A456825C4063B36804CD178D21F96F79FD567182E45637C6FD53E132CAF` |

El APK `arm64-v8a` y `artemis.apk` son idénticos
(`D32CBFBA09195CA2C5074A88B8C037D6FCB519BEB8DB674862246F6EFB1D0827`).

### Android TV

El APK incluye la actividad **LEANBACK_LAUNCHER**, banner de TV y no exige pantalla táctil. Funciona en Android TV/Google TV con mando o gamepad; el APK `arm64-v8a` o `armeabi-v7a` es el adecuado según el dispositivo.

### Instalación

1. Descarga el APK correspondiente a la arquitectura del dispositivo.
2. Permite la instalación desde fuentes desconocidas para el navegador o gestor de archivos.
3. Instala y abre **Artemis**.
4. Añade o descubre tu PC con Apollo/Sunshine y empareja el cliente.

> El `applicationId` es distinto del Artemis oficial, así que puede convivir con él. No comparte datos ni emparejamientos con la app oficial.


### Play Protect

Si Android muestra **“Aplicación bloqueada para proteger tu dispositivo”** con el texto
“Esta aplicación puede solicitar acceso a datos sensibles”, el aviso lo genera Play
Protect y no la firma del APK.

Google bloquea las apps instaladas desde Internet (navegador, mensajería o gestor de
archivos) que declaran alguno de estos permisos: `READ_SMS`, `RECEIVE_SMS`, acceso a
notificaciones o **accesibilidad**. Artemis no usa SMS ni notificaciones. El detonante
era el servicio de accesibilidad `KeyboardAccessibilityService`, que se conservaba desde
el fork original aunque el código no lo activa.

- **1.0.2** elimina por completo el servicio de accesibilidad y su configuración.
  El APK final ya no declara `ACCESSIBILITY` ni permisos sensibles.
- **1.0.1** solo eliminó los permisos heredados de LiteRT y `CAPTURE_KEYBOARD`; el
  servicio de accesibilidad seguía presente y Play Protect podía seguir bloqueando.
- Si Play Protect todavía muestra el aviso con el APK 1.0.2, puede deberse a la caché
  de reputación del paquete anterior: desinstala la versión bloqueada, reinicia el
  dispositivo e instala `Artemis-1.0.2-arm64-v8a.apk` (o el universal). Como último
  recurso, desactiva temporalmente **Play Store → perfil → Play Protect → ajustes →
  Escanear apps con Play Protect**, instala y vuelve a activarlo.
- En Samsung, revisa también **Auto Blocker**; en Android 16, **Protección avanzada**.
- Alternativa: instalar por ADB con `verifier_verify_adb_installs=0` y restaurarlo después.
- Si el APK 1.0.2 sigue bloqueado pese a estar limpio, se puede presentar una
  apelación siguiendo la
  [guía oficial de Play Protect](https://developers.google.com/android/play-protect/warning-dev-guidance).

El teclado físico no depende de accesibilidad: Artemis usa `KeyEvent` y la API
`SemWindowManager` de Samsung. La captura nativa de Android 16.1+ requiere
`CAPTURE_KEYBOARD` (permiso normal, no incluido en la lista de Play Protect); se
omite en 1.0.2 para minimizar permisos y puede restaurarse si hace falta.


## Novedades de la versión 1.0.2

- **1.0.2:** eliminado el servicio de accesibilidad que disparaba el bloqueo de Play
  Protect. El APK ya no declara `ACCESSIBILITY` ni permisos sensibles.
- **1.0.1:** eliminados `CAPTURE_KEYBOARD` y los permisos heredados de LiteRT
  (`READ_PHONE_STATE`, almacenamiento externo).
- Teclado físico mediante `KeyEvent` y Samsung `SemWindowManager`. La captura nativa
  de Android 16.1+ requiere `CAPTURE_KEYBOARD`, omitido en 1.0.2 para minimizar permisos.
- Base actualizada a Artemis `20.2.7` + Moonlight `v12.2`.
- Interfaz Apollo 2026: tokens, toolbar, tarjetas, búsqueda, estados vacíos y skeletons de carga.
- Hoja de acciones para apps: iniciar/reanudar, cerrar, ocultar/mostrar, detalles, acceso directo y exportar `.art`.
- Gestión remota de energía desde la página del PC: apagar, reiniciar y cancelar apagado.
  - Requiere Apollo `1.0.1` o posterior y el permiso `server_cmd` en el cliente.
- Diseño responsive para móvil y tablet, sin variantes excluyentes:
  - móvil: tarjetas de 176 dp;
  - tablet: tarjetas de 220 dp, hojas centradas y ajustes con ancho máximo.
- Icono Artemis 2026 con fondo original y foreground adaptativo.
- APKs firmados y nueva identidad de paquete.

Consulta [`CHANGELOG.md`](CHANGELOG.md) para el detalle.

## Requisitos

- Android 5.0+ (API 21).
- OpenGL ES 3.0.
- `arm64-v8a`, `armeabi-v7a` o `x86_64`.
- Host Apollo/Sunshine accesible por red.
- **Apollo 2026** recomendado para pantalla virtual, portapapeles y comandos de energía:
  <https://github.com/leonidas10009/Apollo_2026>.

## Compilación

Requisitos:

- JDK 17+
- Android SDK Platform 37
- Android NDK `29.0.14206865`
- Gradle wrapper `9.7.1`

El núcleo `moonlight-common-c` está vendorizado; **no** hace falta ejecutar `git submodule update`.

```powershell
cd Artemis\moonlight-android-moonlight-noir
.\gradlew.bat :app:assembleNonRoot_gameRelease --no-daemon
```

Salidas en `app/build/outputs/apk/nonRoot_game/release/`. La tarea también copia el APK arm64 a `../Release/artemis.apk`.

### Firma de release

La firma se lee de `keystore.properties` en la raíz del repo. Ese archivo y la keystore **no se versionan**.

```properties
storeFile=E:/ruta/segura/artemis-release.jks
storePassword=...
keyAlias=artemis
keyPassword=...
```

Si el archivo existe, Gradle firma automáticamente el release. Para verificar:

```powershell
apksigner verify --print-certs app\build\outputs\apk\nonRoot_game\release\app-nonRoot_game-arm64-v8a-release.apk
```

**Importante:** guarda la keystore y su contraseña en un lugar seguro. Sin ellas no se podrán publicar actualizaciones compatibles.

## Tests

```powershell
.\gradlew.bat :app:testNonRoot_gameDebugUnitTest --no-daemon
```

Estado actual: la compilación de tests pasa, pero el worker de tests de Gradle falla al arrancar en este entorno Windows (`ClassNotFoundException: Files`). La app se valida con build de release/debug y pruebas manuales en emulador; queda pendiente estabilizar la tarea de tests.

## Seguridad

- Los comandos de energía usan `server_cmd` de Apollo y requieren un cliente emparejado con ese permiso.
- No concedas `server_cmd` a clientes no confiables.
- No expongas Apollo a Internet sin HTTPS/VPN.
- El APK de release está firmado; no publiques builds locales sin firmar.

## Características heredadas de Artemis

1. Botones virtuales personalizados con importación/exportación.
2. Resoluciones y bitrates personalizados.
3. Varios modos de ratón: normal, multitáctil, trackpad, desactivado y cursor local.
4. Skins de gamepad virtual y joystick libre.
5. Modo monitor externo.
6. Soporte de D-pad de Joy-Con.
7. Información de rendimiento simplificada.
8. Menú de juego con botón Atrás.
9. Comandos de acceso directo personalizados.
10. Cambio rápido de teclado en pantalla.
11. Modo retrato.
12. Visualización en la parte superior para plegables.
13. Ajuste de sensibilidad del trackpad virtual.
14. Vibración del dispositivo como alternativa a la del mando.
15. Página de depuración de mandos.
16. Soporte de trackpad táctil y desplazamiento.
17. Modo trackpad natural.
18. Distribuciones de teclado no QWERTY.
19. Tecla Meta rápida con el botón Atrás.
20. Corrección del bloqueo de FPS en algunos dispositivos.
21. Modos de escala de vídeo: ajustar, rellenar y estirar.
22. Pan/zoom de la vista.
23. Rotación de pantalla en juego.
24. Salida directa de la app.
25. Soporte de scroll en Samsung DeX.
26. Clic/scroll/click derecho correctos en tablets con cursor local.
27. Pantalla virtual con Apollo.
28. Comandos de servidor con Apollo.
29. Sincronización de portapapeles con Apollo.
30. SBS 3D para pantallas externas.

## Créditos

- **Moonlight original:** Cameron Gutman, Diego Waxemberg, Aaron Neyer, Andrew Hennessy y colaboradores de Moonlight Stream.
- **Artemis / Moonlight Noir:** ClassicOldSong y colaboradores.
- **Apollo:** ClassicOldSong y colaboradores.
- **Este fork Artemis 2026:** leonidas10009.

Moonlight nació como proyecto de estudiantes de [Case Western](http://case.edu) en [MHacks](http://mhacks.org).
