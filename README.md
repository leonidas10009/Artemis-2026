# Artemis 2026

Cliente Android de streaming para **Apollo/Sunshine**, basado en **Artemis (Moonlight Noir)** y **Moonlight**.

> **Versión:** `1.0.0` (versionCode `1`)  
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
- **Apollo (host):** <https://github.com/ClassicOldSong/Apollo>
- **Este fork:** <https://github.com/leonidas10009/Artemis-2026>
- **Licencia:** GPL-3.0. Consulta [`LICENSE.txt`](LICENSE.txt).

## Descargas e instalación

Los APKs se publican en [GitHub Releases](https://github.com/leonidas10009/Artemis-2026/releases) y están firmados con la clave de release del proyecto.

| APK | Uso recomendado |
|---|---|
| `Artemis-1.0.0-arm64-v8a.apk` | Móviles, tablets y Android TV modernos (recomendado) |
| `Artemis-1.0.0-armeabi-v7a.apk` | Dispositivos y TV de 32 bits más antiguos |
| `Artemis-1.0.0-x86_64.apk` | Chromebooks, emuladores y equipos x86_64 |

### SHA-256

| APK | SHA-256 |
|---|---|
| `Artemis-1.0.0-arm64-v8a.apk` | `586531A1771B9E9E0D93CA088208D0CFE679E2CB32FFBBB5A4A6644B160933F0` |
| `Artemis-1.0.0-armeabi-v7a.apk` | `4F341D51C02A3AC7042A454EE6B75BAC94AE034EFE6CC94F80765855081C33B8` |
| `Artemis-1.0.0-x86_64.apk` | `2923953624A8501582B1788C4B26792B6F93ED2DA72B01B8DE58D8FBC71C7D6C` |

### Android TV

El APK incluye la actividad **LEANBACK_LAUNCHER**, banner de TV y no exige pantalla táctil. Funciona en Android TV/Google TV con mando o gamepad; el APK `arm64-v8a` o `armeabi-v7a` es el adecuado según el dispositivo.

### Instalación

1. Descarga el APK correspondiente a la arquitectura del dispositivo.
2. Permite la instalación desde fuentes desconocidas para el navegador o gestor de archivos.
3. Instala y abre **Artemis**.
4. Añade o descubre tu PC con Apollo/Sunshine y empareja el cliente.

> El `applicationId` es distinto del Artemis oficial, así que puede convivir con él. No comparte datos ni emparejamientos con la app oficial.

## Novedades de la versión 1.0.0

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
- Apollo recomendado para pantalla virtual, portapapeles y comandos de energía.

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
