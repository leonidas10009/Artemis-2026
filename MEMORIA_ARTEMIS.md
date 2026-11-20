# MEMORIA ARTEMIS 2026

> Documento permanente de trabajo para el cliente **Artemis** de Apollo 2026.
> Última actualización: **2026-11-20**.
> Ruta del cliente: `E:\ProyectoProgramacion\apolo_V2026\artemis_V2026\Artemis\moonlight-android-moonlight-noir`
> Versión desplegada: **Artemis 20.2.7 (versionCode 58)**, base **Moonlight v12.2**.
>
> Este documento sustituye notas sueltas: aquí queda el estado actual, la arquitectura,
> las decisiones de la fusión, los comandos de compilación, el diagnóstico de mandos y
> el procedimiento para futuras actualizaciones.

---

## 0. Resumen ejecutivo

- **Estado:** actualización completada y compilada correctamente.
- **Base Artemis:** commit `c5cf27f4dc822db0e863c4691e7a70c74bea977a` (2026-09-09), versión 20.2.6 (57).
- **Base Moonlight:** tag **v12.2**, commit `b48494cb96bff23d8886c4775cc4f39a1075495d` (2026-09-12).
- **Punto de bifurcación:** `27ded2add8c36b3130115e8a3fd1f89967bc0114` (2024-11-15).
- **Núcleo `moonlight-common-c`:** fork de ClassicOldSong `c999436858471dfefa7617af3b7dc03ec1644ce4` fusionado con upstream `874ac9548f1bd6f095ef2b435c42cdde460e7821` (v12.2). Merge local de referencia: `cb182ee904c378f99b01ce25c1bc1a82f49d6751`.
- **Resultado:** APK release y debug generados; el release queda **sin firmar** y el debug firmado con la clave de Android Studio.
- **Copia automática:** cada compilación de `nonRoot_game` deja en `E:\ProyectoProgramacion\apolo_V2026\artemis_V2026\Release` solo `artemis.apk` (release arm64 sin firmar) y `artemis-debug.apk` (debug arm64 firmado), más `LEEME_APK.txt` (ver 6.5).
- **Incidencia abierta:** “drift fantasma” con un mando Xbox que **no** tiene drift real, solo en **algunos juegos y frontends de emulación**. Ver sección 8.

---

## 1. Identidad, versiones y rutas

### 1.1 Versiones

| Elemento | Valor |
|---|---|
| Producto | Artemis (cliente Android de Apollo/Sunshine) |
| Versión Artemis actual | **20.2.7** |
| versionCode | **58** |
| Base Moonlight | **v12.2** (`b48494c`) |
| Paquete release | `com.limelight.noir` |
| Paquete debug | `com.limelight.noirdebug` |
| Paquete root | `com.limelight.root` |
| Label release | `Artemis` |
| Label debug | `Diana` |
| minSdk | 21 |
| targetSdk | 36 |
| compileSdk | 37 |
| NDK | 29.0.14206865 |
| Java source/target | 17 |
| Gradle | 9.7.1 |
| AGP | 9.4.0 |

### 1.2 Rutas

| Qué | Ruta |
|---|---|
| Repositorio GitHub | `https://github.com/leonidas10009/Artemis-2026` |
| Cliente Artemis | `E:\ProyectoProgramacion\apolo_V2026\artemis_V2026\Artemis\moonlight-android-moonlight-noir` |
| Referencia Moonlight v12.2 | `E:\ProyectoProgramacion\apolo_V2026\artemis_V2026\moonlight-android-master\moonlight-android-master` |
| Host Apollo (repo) | `E:\ProyectoProgramacion\apolo_V2026\_update\Apollo-git` |
| Host Apollo (release) | `E:\ProyectoProgramacion\apolo_V2026\_update\Release` |
| Config del host | `E:\ProyectoProgramacion\apolo_V2026\_update\Release\config\sunshine.conf` |
| Log del host | `E:\ProyectoProgramacion\apolo_V2026\_update\Release\config\sunshine.log` |
| SDK Android usado | `C:\Users\Leonidas\AppData\Local\Android\Sdk` |
| Android Studio | `C:\Program Files\Android\Android Studio` (2026.2.2.9) |
| JDK de compilación | JBR de Android Studio (OpenJDK 25.0.4) |
| `local.properties` | `...\moonlight-android-moonlight-noir\local.properties` |

> El SDK de `C:\Program Files (x86)\Android\android-sdk` también tiene platform 37 y
> build-tools 37, pero **no es escribible** sin administrador y no tiene NDK. Por eso se
> instaló el NDK 29 en el SDK de `AppData\Local` y se apuntó `local.properties` allí.

---

## 2. Estructura del proyecto

```
moonlight-android-moonlight-noir/
├─ app/
│  ├─ build.gradle
│  ├─ src/
│  │  ├─ main/
│  │  │  ├─ java/com/limelight/...          # cliente Android
│  │  │  ├─ jni/
│  │  │  │  ├─ Android.mk
│  │  │  │  └─ moonlight-core/
│  │  │  │     ├─ Android.mk
│  │  │  │     ├─ simplejni.c
│  │  │  │     ├─ callbacks.c
│  │  │  │     ├─ minisdl.c / minisdl.h
│  │  │  │     ├─ controller_list.h
│  │  │  │     ├─ controller_type.h
│  │  │  │     ├─ usb_ids.h
│  │  │  │     ├─ opus/                     # libopus 1.6.1
│  │  │  │     ├─ openssl/                  # OpenSSL 4.0.2, solo libcrypto
│  │  │  │     └─ moonlight-common-c/       # core vendorizado
│  │  │  ├─ res/
│  │  │  └─ assets/
│  │  ├─ game/AndroidManifest.xml
│  │  ├─ nonRoot_game/AndroidManifest.xml
│  │  └─ test/
│  └─ ...
├─ gradle/wrapper/
├─ build.gradle
├─ gradle.properties
├─ settings.gradle
├─ gradlew / gradlew.bat
├─ local.properties
└─ ...
```

### 2.1 Source sets y flavors

- `root`: build con `com.limelight.root`, `PRODUCT_FLAVOR=root`, `maxSdk 25`.
- `nonRoot_game`: build normal, `com.limelight`, `PRODUCT_FLAVOR=nonRoot`.
- `app/src/game/AndroidManifest.xml`: manifiesto de app de juego para el flavor de juego.
- `app/src/nonRoot_game/AndroidManifest.xml`: label `@string/app_label` y `isGame=true`.
- `splits.abi`: genera APK por ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) en lugar de un APK universal.

### 2.2 Núcleo vendorizado

`app/src/main/jni/moonlight-core/moonlight-common-c` **ya contiene los fuentes** del core.
No hace falta ejecutar `git submodule update` para compilar. Para el repositorio de
GitHub se eliminó `.gitmodules`: el core queda vendorizado como archivos normales y
el repo es autocontenido.

Incluye:
- `src/` del core.
- `enet/` con sus fuentes.
- `nanors/` con `deps/obl/`.
- `CMakeLists.txt`, `LICENSE.txt`, `README.md`.

No incluye `.git`, `.github` ni `.gitmodules` internos.

---

## 3. Arquitectura del cliente Artemis

### 3.1 Pipeline de entrada de mando

```
InputDevice / MotionEvent / KeyEvent (Android)
        │
        ▼
ControllerHandler
  ├─ detección de tipo (VID/PID → SDL DB)
  ├─ mapeo de ejes
  ├─ deadzone
  ├─ fusión multi-dispositivo
  └─ botones / gatillos / touchpad / sensores
        │
        ▼
NvConnection.sendControllerInput(...)
        │
        ▼
MoonBridge.sendMultiControllerInput(...)  [JNI]
        │
        ▼
moonlight-common-c: LiSendMultiControllerInput(...)
        │
        ▼
control stream cifrado → Apollo
```

Archivos clave:

| Archivo | Función |
|---|---|
| `ControllerHandler.java` | Toda la lógica de mandos del cliente |
| `PreferenceConfiguration.java` | Preferencias, incluida `deadzonePercentage` |
| `preferences.xml` | UI de ajustes de gamepad |
| `MoonBridge.java` | Puente Java ↔ JNI |
| `simplejni.c` | Implementación JNI |
| `minisdl.c`, `controller_list.h` | Base de datos SDL recortada para VID/PID |
| `controller_type.h`, `usb_ids.h` | Tipos y VID/PID de mandos |
| `ControlStream.c` (core) | Empaquetado del paquete de mando |

### 3.2 Detección de tipo de mando

- `ControllerHandler` llama a `MoonBridge.guessControllerType(vendorId, productId)`.
- `simplejni.c` usa `SDL_IsJoystickXboxOne`, `SDL_IsJoystickXboxSeriesX`,
  `SDL_IsJoystickPS4`, `SDL_IsJoystickPS5`, etc.
- `controller_list.h` es una tabla de VID/PID basada en la base de datos de SDL.
- El resultado se envía al host en `sendControllerArrivalEvent`:
  - `LI_CTYPE_XBOX`
  - `LI_CTYPE_PS`
  - `LI_CTYPE_NINTENDO`
  - `LI_CTYPE_STEAM`
  - `LI_CTYPE_UNKNOWN`
- También se envían capacidades: gatillos analógicos, rumble, gatillos con rumble,
  touchpad, acelerómetro, giroscopio, batería, LED RGB, doble touchpad.
- **Importante:** la base SDL **no lee los ejes**. Solo clasifica el mando por VID/PID.
  SDL3 `SDL_gamepad.h` no está en el camino de entrada de Artemis.

### 3.3 Mapeo de ejes

Valores por defecto:

| Eje | Uso |
|---|---|
| `AXIS_X` / `AXIS_Y` | Stick izquierdo |
| `AXIS_RX` / `AXIS_RY` | Stick derecho si existen |
| `AXIS_Z` / `AXIS_RZ` | Stick derecho si no hay RX/RY; o gatillos |
| `AXIS_LTRIGGER` / `AXIS_RTRIGGER` | Gatillos |
| `AXIS_BRAKE` / `AXIS_GAS` | Gatillos alternativos |
| `AXIS_BRAKE` / `AXIS_THROTTLE` | Gatillos alternativos |
| `AXIS_HAT_X` / `AXIS_HAT_Y` | D-pad tipo hat |

Heurística principal en `createControllerContext()`:

1. Stick izquierdo = X/Y.
2. Si existen `RX/RY`:
   - Si es Sony (`0x054c`) y tiene `KEYCODE_BUTTON_C`: se asume **DS4 no estándar**:
     gatillos = RX/RY y el stick derecho se resuelve luego con Z/RZ.
   - Si no: stick derecho = RX/RY; gatillos = Z/RZ si existen.
3. Si el stick derecho sigue sin asignar:
   - Si existen Z/RZ: stick derecho = Z/RZ.
   - Si no, RX/RY.
4. Los gatillos usan el primer par disponible (`LTRIGGER/RTRIGGER`, `BRAKE/GAS`,
   `BRAKE/THROTTLE`).
5. El deadzone de gatillos usa `MotionRange.getFlat()` y se limita a 13–30 %.

**Limitación conocida:** un mando clon o no estándar que no sea Sony puede caer en la
heurística equivocada (por ejemplo, RX/RY como gatillos y el código interpretarlos como
stick derecho). Eso puede producir un “drift” que en realidad es un eje de gatillo o
auxiliar mal mapeado.

### 3.4 Deadzone

- Constante: `PreferenceConfiguration.DEFAULT_DEADZONE = 5`.
- Rango UI: `-20` a `+20`.
- Se aplica en `ControllerHandler.handleDeadZone(Vector2d, float)`.
- Es **radial**: `sqrt(x² + y²) <= deadzone`.
- **Positivo:** zona muerta normal; dentro del radio se envía 0,0.
- **Negativo:** compensación de zona muerta; expande el valor y amplifica el ruido.
  Mantiene un 1 % de zona muerta real para centrar.
- No se reescala al salir de la zona muerta (a propósito, para no agrandar la deadzone
  que luego aplica el juego).
- Los sticks usan solo la deadzone del usuario. **No se usa `MotionRange.getFlat()`**
  para sticks (solo para gatillos). Un mando que en Android reposa al 6–10 % puede
  colar valores con el 5 % por defecto.
- El resumen en **español** de esta opción está desactualizado: no menciona que los
  valores negativos amplifican. El texto inglés sí lo advierte.

### 3.5 Fusión multi-dispositivo

`sendControllerInputPacket()` agrupa todos los contextos con el mismo `controllerNumber`
(por ejemplo, un mando partido en varios dispositivos Android o el controlador en
pantalla) y fusiona:

```java
inputMap |= context.inputMap;
leftTrigger |= maxByMagnitude(leftTrigger, context.leftTrigger);
leftStickX |= maxByMagnitude(leftStickX, context.leftStickX);
...
```

- Para botones, el `OR` es correcto.
- Para ejes y gatillos, el `OR` con `maxByMagnitude` puede **amplificar** el valor
  cuando hay más de un contexto. Ejemplo: 3000 | 2000 = 4088, mayor que ambos.
- `defaultContext` (estado del controlador en pantalla, OSC) tiene `controllerNumber = 0`
  por defecto. Si el mando físico también recibe el número 0, ambos se fusionan.
- Con un solo mando y sin OSC, el `OR` no tiene efecto (el acumulador empieza en 0).

### 3.6 Mando por USB (driver propio)

Artemis puede reclamar mandos USB y leerlos con sus propios drivers
(`Xbox360Controller`, `XboxOneController`, `ProConController`, etc.) en lugar de usar
Android `InputDevice`.

Ajustes:
- `checkbox_usb_driver`: activa el driver USB.
- `checkbox_usb_bind_all`: reemplaza el soporte nativo de Xbox/Nintendo.

En esta ruta los ejes llegan por `reportControllerState(...)`, que también aplica
`handleDeadZone()` y escala a `0x7FFE`. Es una alternativa si el mapeo nativo de Android
de un mando USB es problemático.

### 3.7 Controlador en pantalla (OSC)

- `VirtualController` mantiene un `ControllerInputContext` y llama a
  `controllerHandler.reportOscState(...)`.
- Ese estado se guarda en `defaultContext`.
- El OSC puede aportar botones, sticks, gatillos y vibración.
- Si está activo y sus sticks no están exactamente a cero, puede contribuir al drift
  fusionado con el mando físico (ver 3.5).

### 3.8 Vídeo

Archivo principal: `MediaCodecDecoderRenderer.java`.

Puntos propios de Artemis que **se conservaron**:
- Frame pacing adaptativo con `preferLowerDelays`, `releaseWithPolicy`, EWMA de jitter,
  `lateStreak`, `recentDrops`, `lastDropNs`, `lastPresentNs`.
- Estadísticas de latencia de decodificación (`updateDecodeLatencyStats`).
- Modos de frame pacing: equilibrio, máxima suavidad, límite de FPS.
- Ajustes de H.264 (`constraint_set`, `level_idc`) para decodificadores antiguos.
- Diagnóstico de formatos de entrada/salida del decodificador.

Cambios de Moonlight v12.2 incorporados:
- Timestamps de decodificación en **microsegundos** (`receiveTimeUs`, `enqueueTimeUs`).
- `baseTimestampUs` para normalizar los PTS a uptime.
- Eliminación de rutas pre-Lollipop.
- `setProducerThrottlingEnabled(false)` en Android 16+.
- Ajustes de constraints H.264 desactivados en Oreo+.

### 3.9 Audio

`AndroidAudioRenderer.java`:
- AudioTrack de baja latencia.
- 5.1 / 7.1 surround.
- Se adoptó la limpieza de rutas pre-Lollipop de Moonlight v12.2.
- Artemis no tenía cambios funcionales propios en este archivo (solo CRLF/modo).

### 3.10 Teclado

- `KeyboardTranslator.java`: traducción de keycodes Android → GFS.
  - Artemis añadió el parámetro `scancode` para layouts no QWERTY.
  - Moonlight v12.2 añadió F13–F24, PRINT y SCREENSHOT.
  - Se conservaron ambos.
- `KeyboardAccessibilityService.java`: captura de teclas física cuando Android las
  intercepta.
- Teclado virtual completo y teclado en pantalla propio de Artemis.
- En Android 16.1 se usa la captura nativa de teclado
  (`CAPTURE_KEYBOARD` + `setKeyboardCaptureEnabled`).

### 3.11 Perfiles

- `ProfilesManager`, `SettingsProfile`, `ProfilesActivity`, `ProfilesAdapter`.
- Los perfiles son un mapa `clave → valor` que se superpone a `SharedPreferences`
  mediante `OverlaySharedPreferences`.
- Cualquier preferencia nueva funciona sin cambiar el perfil, pero **un perfil puede
  sobrescribir** `seekbar_deadzone`, `checkbox_gamepad_motion_fallback`, etc.
- Si el drift aparece “a veces”, comprobar el perfil activo.

### 3.12 Funciones Apollo

- **Virtual Display**: `useVirtualDisplay`, `enforceDisplayMode`.
- **Server Commands**: `MoonBridge.sendExecServerCmd`, menú de comandos.
- **Clipboard sync**: `sendClipboard`, `getClipboard`, `sendUtf8Text`.
- **File transfer / nonce**.
- **SBS 3D**: `Stereo3DRenderer` + MiDaS v2 Lite (`midas-midas-v2-w8a8.tflite`).
- **Pantalla externa**: `ExternalDisplayControlActivity`,
  `StartExternalDisplayControlReceiver`.
- **HUD / rendimiento**: `PerformanceDataTracker`, overlay Lite, gráficas.
- **Pan/zoom, sensibilidad táctil, modos de ratón, trackpad**.
- **Teclado virtual, perfiles, resolución/bitrate/refresco personalizados**.

---

## 4. Arquitectura del host Apollo (para depurar mandos)

### 4.1 Recorrido

```
control stream
  → src/input.cpp (parseo de NV_MULTI_CONTROLLER_PACKET)
  → platf::gamepad_state_t
  → platf::gamepad_update(...)
  → src/platform/windows/input.cpp
  → ViGEmBus (Xbox 360 / DS4) o libvirtualhid
  → Windows / juego
```

### 4.2 Configuración actual del host

`_update\Release\config\sunshine.conf`:

```ini
gamepad_driver = vigembus
server_cmd = [{"cmd":"bubbles.scr","elevated":false,"name":"Bubbles"}]
```

`apps.json` tiene `"gamepad": "auto"` en la mayoría de apps.

Log (`sunshine.log`):

```
ViGEmBus is selected as the only virtual gamepad driver
Gamepad 0 will be Xbox 360 controller (auto-selected by client-reported type)
```

Por tanto, ahora mismo:
- El cliente reporta Xbox.
- Apollo crea un **Xbox 360 virtual** con ViGEmBus.
- El host **no aplica deadzone** a los sticks.

### 4.3 ViGEmBus: Xbox 360 vs DS4

`src/platform/windows/input.cpp`:

- Xbox 360:
  ```cpp
  report.sThumbLX = gamepad_state.lsX;
  report.sThumbLY = gamepad_state.lsY;
  report.sThumbRX = gamepad_state.rsX;
  report.sThumbRY = gamepad_state.rsY;
  ```
  Es un **paso directo de int16**, sin deadzone ni reescalado.

- DualShock 4:
  - Convierte a 0–255 con `to_ds4_triggerX/Y`.
  - El centro cae en **127/129**, no en 128 exacto.
  - Solo se usa si el cliente reporta PS, si `gamepad = ds4` o si
    `motion_as_ds4`/`touchpad_as_ds4` están activos.

### 4.4 ViGEmBus vs Virtual HID

Apollo 2026 soporta:

| Driver | Perfiles | Estado |
|---|---|---|
| `vigembus` | Xbox 360, DS4 | Instalado y en uso |
| `virtualhid` | x360, xone, xseries, ds4, ds5, switch, generic | Broker instalado y corriendo |
| `all` | Usa Virtual HID si está licenciado; si no, ViGEmBus | Disponible |
| `none` | Sin gamepad virtual | — |

- `libvirtualhid_broker` está **Running** y el dispositivo
  `ROOT\LIBVIRTUALHID\0000` está OK.
- La ruta Virtual HID normaliza int16 a float y usa perfiles HID.
- Tampoco aplica deadzone: el valor que llega del cliente es el que se expone.
- La selección automática de perfil Virtual HID:
  - PS → DualSense.
  - Nintendo → Switch Pro.
  - Xbox → Xbox Series.
  - `motion_as_ds4` + sensores → DualSense.
  - `touchpad_as_ds4` + touchpad → DualSense.
  - Por defecto → Xbox Series.
- ViGEmBus solo admite `auto`, `x360` y `ds4`; si se configura otro perfil, Apollo
  avisa y vuelve a `auto`.

### 4.5 SDL3 en Apollo

- Apollo **no usa SDL3** para el gamepad virtual.
- `libs/apollo-input` documenta SDL3 solo como backend opcional para pruebas/previews;
  el backend validado en Windows es ViGEmBus.
- El `apollo-input` actual expone ViGEmBus (Xbox 360/DS4) y uinput (Linux).
- Por tanto, el drift no se corrige cambiando SDL3.

---

## 5. Actualización realizada (2026-11-20)

### 5.1 Objetivo

Actualizar Artemis con Moonlight **v12.2** conservando las implementaciones únicas de
Artemis y actualizándolas cuando el nuevo código lo requería.

### 5.2 Método

Se hizo un **merge real de 3 vías con Git**:

1. Se verificó que la carpeta local de Artemis era exactamente el commit `c5cf27f`.
2. Se verificó que la carpeta local de Moonlight era exactamente el tag `v12.2`
   (`b48494c`).
3. Se clonaron ambos repositorios upstream y se calculó el merge base:
   `27ded2a` (2024-11-15).
4. Artemis tenía **568 commits** por delante del base; Moonlight, **68**.
5. Se normalizaron los archivos con CRLF de Artemis para que Git no viera conflictos
   falsos de archivo completo.
6. Se resolvieron los conflictos a mano conservando la lógica de Artemis.
7. Se fusionó el submódulo `moonlight-common-c` con upstream v12.2.
8. Se vendorizó el core dentro del proyecto.
9. Se compiló release y debug.

### 5.3 Conflictos y decisiones

| Archivo | Decisión |
|---|---|
| `app/build.gradle` | Se conservaron flavors `root`/`nonRoot_game`, labels Artemis/Diana, splits ABI y tests. Se adoptaron NDK 29, compileSdk 37, targetSdk 36, Java 17, desugaring, packaging y versiones nuevas de dependencias. |
| `build.gradle` (raíz) | AGP **9.4.0**. |
| `gradle-wrapper.properties` | Gradle **9.7.1**. |
| `Game.java` | Se conservaron `prefConfig.fullScreen`, bloqueo de orientación y lógica immersive/multiwindow. Se adoptaron captura de teclado Android 16.1, `getModifierState(keyCode)` y Shift para `+`. |
| `MediaCodecDecoderRenderer.java` | Se conservaron frame pacing, `preferLowerDelays`, ULL y estadísticas Artemis. Se adoptaron `receiveTimeUs`/`enqueueTimeUs` y `baseTimestampUs`. |
| `ControllerHandler.java` | Se conservaron sesión LED, `hasRgbLight` y lógica de vibración Artemis. Se adoptaron `setLedStateRunnable`, cierre seguro de `LightsSession`, `VibratorManager` y el bloque de `hasRgbLed` de Moonlight. |
| `KeyboardTranslator.java` | Se conservó `translate(keycode, scancode, deviceId)`. Se añadieron F13–F24, PRINT y SCREENSHOT. |
| `UiHelper.java` | Se conservó el aviso/null de `GameManager` de Artemis. Se añadió el `try/catch` para Meta Quest. |
| `XboxOneController.java` | Se conservó GameSir; se añadieron 8BitDo, Xbox Series S/X y sus paquetes de init. |
| `AndroidManifest.xml` | Solo se añadió `android.permission.CAPTURE_KEYBOARD`. |
| `arrays.xml` / `locales_config.xml` | Se añadieron `eo`, `lv` y `ta`. |
| Traducciones | Merge por clave: se conservaron las claves propias de Artemis y se actualizaron las que Artemis no había tocado. |
| `app/build.gradle` debug | Se cambió `proguard-android.txt` por `proguard-android-optimize.txt` porque AGP 9.4 ya no admite el primero. |
| `values/strings.xml` | `local_network_rationale` se adaptó a “Artemis requires local network permissions…”. |

### 5.4 Traducciones

Se hizo un merge por entrada (`name="..."`) en lugar de un merge de líneas:

- Se partió del archivo de Artemis.
- Si Artemis no había modificado una clave y Moonlight sí, se tomó la traducción nueva.
- Si Artemis la había modificado, se conservó la de Artemis.
- Las claves solo de Moonlight se añadieron al final.
- No se perdió ninguna clave de Artemis ni de Moonlight.

Ejemplos:
- `values-fr/strings.xml`: se conservaron **292 claves propias** de Artemis.
- `values-bg/strings.xml`: se actualizaron 72 y se añadieron 119 de Moonlight.
- `values-nl/strings.xml`: se actualizaron 45 y se añadieron 64.
- `values-hu/strings.xml`: se actualizaron 134 y se añadieron 13.
- Se añadieron los idiomas `lv` y `ta`; `eo` ya existía.

### 5.5 Núcleo `moonlight-common-c`

Se fusionó el fork de ClassicOldSong con upstream v12.2.

Cambios upstream principales:
- Timestamps de `DECODE_UNIT` en microsegundos.
- `nanors` para FEC Reed-Solomon.
- LTR ACK.
- Estadísticas de alta resolución.
- OpenSSL 4.0.2 (solo `libcrypto`).
- libopus 1.6.1.
- Soporte de páginas de 16 KB.
- Actualización de ENet.
- Tipos de mando Steam.
- Soporte de doble touchpad.

Parches Apollo conservados:
- `LiSendExecServerCmd`.
- `LiSendEmptyPayload`.
- Clipboard (`0x3001`).
- File transfer nonce (`0x3002`).
- DS adaptive triggers (`0x5503`, índice 15).
- `IDX_EXEC_SERVER_CMD` y tabla de packet types de Apollo.

### 5.6 Ajustes propios de esta actualización

- `versionName` 20.2.6 → **20.2.7**.
- `versionCode` 57 → **58**.
- `proguard-android.txt` → `proguard-android-optimize.txt` en debug (AGP 9.4).
- `local_network_rationale` con marca Artemis.
- `local.properties` apuntando al SDK de AppData.
- Core vendorizado.

### 5.7 Archivos añadidos, eliminados y renombrados

- Añadidos:
  - `values-lv/strings.xml`
  - `values-ta/strings.xml`
  - Headers nuevos de OpenSSL 4.0.2
  - `app/src/main/jni/moonlight-core/Build.txt`
  - APKs en `app/build/outputs/apk/...`
- Renombrados:
  - `libopus/` → `opus/`
- Eliminados:
  - `libssl.a` para las 4 ABI (el core ya solo usa `libcrypto`).
  - `libopus` antiguo.
  - Headers obsoletos de OpenSSL (`asn1_mac.h`, `opensslconf.h.in`, `rand_drbg.h`).

### 5.8 Cambios funcionales de Moonlight 12.2 incorporados

- Captura nativa de teclado en Android 16.1 (`CAPTURE_KEYBOARD`).
- `setProducerThrottlingEnabled(false)` en Android 16+.
- Keycodes F13–F24, PRINT, SCREENSHOT.
- Shift para la tecla `+`.
- Mandos 8BitDo y Xbox Series S/X.
- Sincronización de la base de mandos SDL.
- Corrección de ANRs/crashes del LED RGB.
- `VibratorManager` en Android 12+.
- `GameManager` tolerante a Meta Quest.
- `nanors`, 16 KB pages.
- Traducciones lv/ta/eo.
- OkHttp 5.5.0, BouncyCastle 1.85.x, jMDNS 3.6.3.
- Java 17 + desugaring.

---

## 6. Compilación y artefactos

### 6.1 Entorno

| Elemento | Valor |
|---|---|
| Android Studio | 2026.2.2.9 |
| JBR | OpenJDK 25.0.4 |
| SDK | `C:\Users\Leonidas\AppData\Local\Android\Sdk` |
| Platform | `android-37.0` |
| Build-tools | `37.0.0` |
| NDK | `29.0.14206865` |
| Gradle | 9.7.1 (wrapper) |
| AGP | 9.4.0 |

`local.properties`:

```properties
sdk.dir=C\:\\Users\\Leonidas\\AppData\\Local\\Android\\Sdk
```

### 6.2 Comandos

```powershell
cd E:\ProyectoProgramacion\apolo_V2026\artemis_V2026\Artemis\moonlight-android-moonlight-noir
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat :app:assembleNonRoot_gameRelease --no-daemon
.\gradlew.bat :app:assembleNonRoot_gameDebug --no-daemon
```

Al terminar, los APK se copian automáticamente a
`E:\ProyectoProgramacion\apolo_V2026\artemis_V2026\Release` (ver 6.5).

### 6.3 Resultado

- `assembleNonRoot_gameRelease`: **BUILD SUCCESSFUL** (2m 43s).
- `assembleNonRoot_gameDebug`: **BUILD SUCCESSFUL** (1m 27s).
- `lintVital` pasó.
- R8 pasó.

### 6.4 APKs

Ruta base:

```
app/build/outputs/apk/nonRoot_game/
```

Release (sin firmar):

| APK | Tamaño | SHA-256 |
|---|---:|---|
| `release/app-nonRoot_game-arm64-v8a-release-unsigned.apk` | 40.100.163 B | `0805EEB2AEF6EA3250FC93C4AE4E4F1228CED1888A94990BDD5C2964205F7614` |
| `release/app-nonRoot_game-armeabi-v7a-release-unsigned.apk` | 37.302.341 B | `2523C1FB0737647407C3DB0D3543F8153C514A63AC7D43D99FDE3750185132D8` |
| `release/app-nonRoot_game-x86-release-unsigned.apk` | 47.899.985 B | `3ED201232F37E31A081A1406A4E5C058B8D3873D0FC3BE65A60025AE4D09A4A9` |
| `release/app-nonRoot_game-x86_64-release-unsigned.apk` | 54.352.832 B | `8AA282CD03E5863FB3913B9B05143E6C7CFD81F58290F1D2425FF9ADC641EC4B` |

Debug (firmado con clave debug de Android):

| APK | Tamaño | SHA-256 |
|---|---:|---|
| `debug/app-nonRoot_game-arm64-v8a-debug.apk` | 43.223.672 B | `6188082B2EB7989D22FBF8F1FC93065B312BFACE0BAB0CF2E7CE932B7757CAF0` |
| `debug/app-nonRoot_game-armeabi-v7a-debug.apk` | 40.046.031 B | `8C255468A71352BF2CCF8C29B62208670D5A8298F794EF3416FE00E9887D5678` |
| `debug/app-nonRoot_game-x86-debug.apk` | 52.371.450 B | `8B80DE2615CFD6E2FFF01E4AF8EA507DDF6A206F008D164099110A3D86DEA681` |
| `debug/app-nonRoot_game-x86_64-debug.apk` | 59.058.461 B | `449BF97C3CD25953EF7785CBE6BBE04BFDD6EEDE520081C24CB46ABDAD2B9653` |

- Debug: `com.limelight.noirdebug`, label **Diana**, firmado con el certificado
  `CN=Android Debug` (SHA-256 `c4db76d6bf9440f12ddee1a98c5e2e088a8ceb437e381280b5b9529195cce937`).
- Release: `com.limelight.noir`, label **Artemis**, **sin firmar**. Para publicar hay
  que firmarlo con la keystore habitual.

> Nota: las tablas de 6.4 son la salida completa de Gradle para todas las ABI.
> La carpeta curada `Release` solo recibe `artemis.apk` y `artemis-debug.apk`
> (arm64-v8a); el resto queda en `app/build/outputs/apk/` para quien lo necesite.

### 6.5 Salida automática a `artemis_V2026\Release`

Cada compilación de `nonRoot_game` deja los APK exportados en:

```
E:\ProyectoProgramacion\apolo_V2026\artemis_V2026\Release
```

Nombres:

| Archivo exportado | Origen | Firma | Uso |
|---|---|---|---|
| `artemis.apk` | `release/...-arm64-v8a-...apk` | Sin firmar | Firmar y publicar |
| `artemis-debug.apk` | `debug/...-arm64-v8a-...apk` | Firmado con clave debug | **Pruebas en dispositivo** |
| `LEEME_APK.txt` | — | — | Explica cuál usar |

Las demás ABI (`armeabi-v7a`, `x86`, `x86_64`) **no se exportan** a `Release`
para evitar duplicados y confusión. Si hicieran falta, se generan en
`app/build/outputs/apk/nonRoot_game/`.

Se implementó en `app/build.gradle` con dos tareas `Copy`:

- `exportArtemisReleaseApks`
- `exportArtemisDebugApks`

Y se engancharon con `finalizedBy` a:

- `assembleNonRoot_gameRelease`
- `assembleNonRoot_gameDebug`

Así se ejecutan **cada vez** que se compila la variante, también desde Android Studio
(Build → Make Project / Run) si la tarea de ensamblado se ejecuta.

> `artemis.apk` es el APK release de `arm64-v8a`, pero **no está firmado**. Para
> instalarlo/publicarlo hay que firmarlo con la keystore definitiva. Para pruebas
> rápidas, usar `artemis-debug.apk`, que ya está firmado con la clave debug.


---

## 7. Validación realizada

- **171 archivos Java** parseados sin errores de sintaxis.
- **138 archivos XML** válidos.
- **0 marcadores de conflicto**.
- Recursos `R.string`, `R.array`, `R.layout`, `R.drawable`, `R.id` comprobados.
- **34 métodos nativos** de `MoonBridge` ↔ **34 exports JNI**.
- Todos los símbolos `Li*` y constantes `LI_*` usados por el JNI existen en el core.
- Las 36 fuentes de `Android.mk` y sus include paths existen.
- Build release y debug correctos.
- No se pudo probar en dispositivo: no había ninguno conectado por ADB durante la
  validación.

---

## 8. Incidencia abierta: “drift fantasma” en algunos juegos/frontends

### 8.1 Síntomas reportados

- Mando **Xbox**.
- El mando **no tiene drift real**.
- El drift aparece **solo con algunos juegos y frontends de emulación**.
- En otros juegos el mismo mando se comporta bien.

### 8.2 Lectura del síntoma

Que ocurra solo en algunos juegos/frontends y que el mando esté bien apunta a:

1. **El juego/frontend aplica poca o ninguna deadzone**, o usa Raw Input/DirectInput
   en lugar de XInput.
2. **Steam Input** u otra capa añade su propia curva/deadzone.
3. El frontend de emulación (RetroArch, Dolphin, PCSX2, etc.) tiene su propio ajuste de
   deadzone o mapeo de ejes.
4. El pad virtual Xbox 360 de ViGEmBus **no aplica deadzone**; si Artemis envía un
   valor pequeño distinto de cero, el juego que no filtra lo verá.
5. Un perfil de Artemis puede estar sobrescribiendo la deadzone.
6. La deadzone de Artemis está en **negativo** (compensación) o en un valor bajo (5 %).

### 8.3 Configuración actual relevante

- Cliente: deadzone **5 %** por defecto, radial, rango -20…+20.
- Host: `gamepad_driver = vigembus`; `Gamepad 0 will be Xbox 360 controller`.
- ViGEmBus Xbox 360: `sThumbLX = lsX` directo, sin deadzone.
- SDL3 no interviene; la SDL DB solo clasifica VID/PID.

### 8.4 Comprobaciones recomendadas

1. **Artemis → Ajustes → Gamepad → “Ajustar zona muerta del stick analógico”**:
   poner **+8 % o +10 %**. No usar negativo salvo que el mando lo compense.
2. Revisar el **perfil activo**: puede sobrescribir `seekbar_deadzone`.
3. Durante un stream, en el PC:
   `Win+R` → `joy.cpl` → **Xbox 360 Controller for Windows** → Propiedades → Test.
   - Si la cruz se mueve en reposo: Artemis está enviando valores.
   - Si está centrada: el problema es del juego/frontend.
4. Probar el mando **directamente en el PC** con `joy.cpl` para descartar hardware.
5. Probar el mismo juego/frontend con:
   - Deadzone del juego/frontend subida.
   - Steam Input desactivado.
   - XInput en lugar de Raw Input/DirectInput (o viceversa).
6. En Apollo, probar otro perfil:
   - `gamepad = x360` (ViGEmBus, paso directo).
   - `gamepad = ds4` (ViGEmBus DS4; ojo al centro 127/129).
   - `gamepad_driver = virtualhid` con `gamepad = xseries` o `ds5` (broker ya instalado).
7. En Artemis, probar el **driver USB** si el mando es USB:
   - `checkbox_usb_driver` + `checkbox_usb_bind_all`.
8. Comprobar si el mando tiene modos (XInput / DirectInput / Switch) y probar otro modo.

### 8.5 Diagnóstico por `adb`

Cuando haya un dispositivo conectado:

```powershell
adb logcat | Select-String "LimeLog|Controller|deadzone|Analog stick"
```

Artemis registra:

```
Analog stick deadzone: <izquierdo> <derecho>
Trigger deadzone: <valor>
```

No registra los ejes elegidos ni los valores en reposo. Si hace falta, se pueden añadir
logs temporales en `createControllerContext()` y `handleAxisSet()`.

### 8.6 Backlog de mejoras para mandos

1. **Actualizar la traducción española** del resumen de deadzone para explicar los
   valores negativos.
2. **Usar `MotionRange.getFlat()` como deadzone mínima de sticks**, convirtiendo el
   valor raw a normalized. Evitaría el drift de mandos que reposan al 6–10 %.
3. **Auto-centrado opcional al conectar**: muestrear ~500 ms en reposo y restar el
   offset. Requiere aviso al usuario.
4. **Corregir la fusión**: cambiar `|= maxByMagnitude(...)` por
   `= maxByMagnitude(...)` para ejes y gatillos. Mantener `|=` solo para botones.
5. **Añadir logs de diagnóstico**: ejes elegidos, `getFlat()`, deadzone y valores en
   reposo.
6. **Probar Virtual HID** con perfiles xseries/ds5 para comparar el centrado.
7. **Documentar** el perfil de juego/frontend que falla y su deadzone.

### 8.7 Datos a recoger para cerrar la incidencia

- Modelo exacto del mando Xbox y modo (Xbox/Bluetooth/USB).
- ¿Stick izquierdo, derecho o ambos?
- Juegos/frontends afectados y versión.
- ¿RetroArch/Dolphin/PCSX2/Steam? ¿XInput o Raw Input?
- Valor actual de “Ajustar zona muerta del stick analógico”.
- ¿Perfil activo? ¿Controlador en pantalla activo?
- Salida de `joy.cpl` durante el stream.
- `sunshine.log` con la línea `Gamepad 0 will be ...`.
- `adb logcat` con las líneas de deadzone.

---

## 9. Mantenimiento y actualización futura

### 9.1 Actualizar Artemis con una nueva versión de Moonlight

1. Identificar el commit/tag exacto de Artemis y de Moonlight.
2. Clonar ambos repositorios con historial.
3. Calcular el merge base:
   ```bash
   git merge-base artemis/master moonlight/master
   ```
4. Hacer el merge:
   ```bash
   git checkout artemis/master
   git merge moonlight/vX.Y
   ```
5. Normalizar CRLF antes de resolver si hay archivos de Artemis con CRLF:
   - Convertir a LF los archivos implicados y volver a hacer el merge.
6. Resolver conflictos conservando:
   - `Game.java`: fullscreen, orientación, immersive.
   - `MediaCodecDecoderRenderer.java`: frame pacing, ULL, estadísticas.
   - `ControllerHandler.java`: LED, vibración, mapeo Artemis.
   - `KeyboardTranslator.java`: `scancode`.
   - `UiHelper.java`: null/log de GameManager.
   - `XboxOneController.java`: GameSir.
   - Traducciones: claves propias.
7. Actualizar el core:
   - Clonar `ClassicOldSong/moonlight-common-c`.
   - Añadir upstream `moonlight-stream/moonlight-common-c`.
   - `git merge <commit-upstream>`.
   - Resolver conflictos conservando `LiSendExecServerCmd`, `LiSendEmptyPayload`,
     clipboard, file transfer y DS adaptive triggers.
   - `git submodule update --init --recursive` para `enet` y `nanors`.
8. Copiar el core fusionado a `app/src/main/jni/moonlight-core/moonlight-common-c`.
9. Compilar release y debug.
10. Probar la regresión.

### 9.2 Checklist de regresión

- [ ] Conexión y emparejamiento con Apollo.
- [ ] Vídeo H.264 / HEVC / AV1.
- [ ] Audio estéreo, 5.1 y 7.1.
- [ ] Mando Xbox por Bluetooth y USB.
- [ ] Driver USB de Artemis.
- [ ] Vibración, gatillos, LED RGB.
- [ ] Touchpad y botón touchpad.
- [ ] Sensores de movimiento y fallback.
- [ ] Teclado físico, teclado virtual y accesibilidad.
- [ ] Ratón, trackpad y modos de ratón.
- [ ] Perfiles y overlays.
- [ ] Portapapeles.
- [ ] Virtual Display.
- [ ] Pantalla externa.
- [ ] Server Commands.
- [ ] SBS 3D.
- [ ] HUD/rendimiento.
- [ ] Pan/zoom y sensibilidad táctil.
- [ ] Frontends de emulación.

### 9.3 Trampas conocidas

- **CRLF:** el repo de Artemis mezcla LF/CRLF; un merge normal puede dar conflictos
  falsos de archivo completo. Normalizar a LF antes del merge.
- **Submódulo:** el core no se actualiza solo. Si se deja el submódulo apuntando a la
  versión antigua, el JNI y el protocolo no coinciden.
- **AGP 9.4:** `getDefaultProguardFile('proguard-android.txt')` ya no está permitido.
- **SDK no escribible:** el SDK de `Program Files (x86)` no permite instalar el NDK sin
  administrador; usar el SDK de `AppData\Local`.
- **Firma:** el release se genera sin firmar. No usar el APK release sin firmar para
  publicar.
- **`local.properties`:** no se debe versionar; contiene rutas absolutas.

---

## 10. Mapa de archivos clave

| Archivo | Para qué |
|---|---|
| `app/build.gradle` | Versión, SDK, flavors, dependencias, ProGuard, splits |
| `build.gradle` | AGP |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle |
| `app/src/main/AndroidManifest.xml` | Permisos, activities, servicios |
| `Game.java` | Activity de streaming, entrada, pantalla completa, PiP |
| `PcView.java` | Pantalla de PCs, búsqueda, acciones y energía remota |
| `NvHTTP.java` | HTTP GameStream, portapapeles y `sendServerCommand` |
| `ControllerHandler.java` | Mandos, deadzone, ejes, LED, vibración |
| `PreferenceConfiguration.java` | Preferencias y defaults |
| `preferences.xml` | UI de ajustes |
| `MediaCodecDecoderRenderer.java` | Decodificación, frame pacing, estadísticas |
| `KeyboardTranslator.java` | Keycodes Android → GFS |
| `KeyboardAccessibilityService.java` | Captura de teclado |
| `VirtualController*.java` | Controlador en pantalla |
| `ProfilesManager.java` | Perfiles |
| `Stereo3DRenderer.java` | SBS 3D |
| `ExternalDisplayControlActivity.java` | Pantalla externa |
| `MoonBridge.java` | JNI |
| `simplejni.c` | Implementación JNI |
| `moonlight-core/Android.mk` | Build NDK |
| `minisdl.c` / `controller_list.h` | SDL DB para VID/PID |
| `moonlight-common-c/src/ControlStream.c` | Paquetes de control |
| `moonlight-common-c/src/Limelight.h` | API del core |
| `app/src/main/res/values*/strings.xml` | Traducciones |
| `app/src/main/res/xml/preferences.xml` | Ajustes |
| `app/src/main/res/xml/locales_config.xml` | Locales |
| `_update/Apollo-git/src/input.cpp` | Parseo de paquetes en el host |
| `_update/Apollo-git/src/nvhttp.cpp` | Endpoint HTTPS `/servercmd` y comandos de energía |
| `_update/Apollo-git/src/stream.cpp` / `stream.h` | Ejecución de comandos de servidor |
| `_update/Apollo-git/src/platform/windows/input.cpp` | ViGEmBus |
| `_update/Apollo-git/src/platform/virtualhid_input.cpp` | Virtual HID |
| `_update/Apollo-git/libs/apollo-input/` | Abstracción de gamepads |
| `_update/Release/config/sunshine.conf` | Config del host |
| `_update/Release/config/sunshine.log` | Log del host |

---

## 11. Historial de versiones

| Versión | versionCode | Base | Notas |
|---|---:|---|---|
| 20.2.6 | 57 | Artemis `c5cf27f` | Estado previo a esta actualización |
| **20.2.7** | **58** | Artemis `c5cf27f` + Moonlight **v12.2** | Actualización actual; build OK |
| — | — | — | Próxima: decidir si se sube a 20.3.x o se mantiene la línea 20.2.x |

---

## 12. Glosario

- **Artemis:** cliente Android de Apollo/Sunshine, fork de Moonlight con mejoras propias.
- **Apollo:** host de streaming, fork de Sunshine.
- **Moonlight:** cliente original de código abierto.
- **Moonlight-common-c:** núcleo C del protocolo de streaming.
- **ViGEmBus:** driver de Windows que crea mandos virtuales Xbox 360/DS4.
- **Virtual HID / libvirtualhid:** driver/broker moderno que expone perfiles HID
  (Xbox One/Series, DualSense, Switch Pro, etc.).
- **SDL DB:** tabla de VID/PID de mandos basada en SDL; en Artemis solo se usa para
  clasificar el tipo de mando.
- **OSC:** on-screen controller, controlador en pantalla.
- **ULL:** ultra low latency; modo de presentación de Artemis.
- **Frame pacing:** reparto de frames para reducir stutter/latencia.
- **Deadzone:** zona muerta del stick.
- **Compensación de deadzone:** valor negativo que expande el movimiento y puede
  amplificar ruido.

---

## 13. Backlog de interfaz (UI/UX)

> Objetivo: alinear Artemis con el sistema de diseño de **Apollo 2026**
> (`Apollo-git/src_assets/common/assets/web`) sin perder las funciones propias de
> Artemis ni reescribir el cliente en Compose.
>
> Estado: Fase 1 implementada. Quedan pendientes menores de UI-05/06/07 y las fases 2–3.

### 13.1 Diagnóstico actual

| Aspecto | Apollo 2026 | Artemis actual |
|---|---|---|
| Framework | Vue 3 + Bootstrap 5.3 + Lucide | XML + Material Components |
| Tema | “Apollo” por defecto + 20+ temas | `Theme.MaterialComponents.NoActionBar`, `#1A1A1A` fijo |
| Paleta | Ámbar `#F59E0B`, cian `#06B6D4`, fondos `#0B0D13/#131620/#1A1E2B` | `#1A1A1A`, `#FF4081` solo en perfiles |
| Navegación | Sidebar + topbar + buscador global | 3 `ImageButton` + `GridView` + FAB |
| Inicio | Hero, stat cards, quick actions | Grid de PCs sin estado ni búsqueda |
| Apps | Tarjetas, badges, acciones | `CardView` 170×220, portada + texto |
| Ajustes | Secciones, búsqueda, valores actuales | Lista larga de preferencias |
| Menú en juego | — | `AlertDialog` con listas de texto |
| Perfiles | — | Lista simple con `RadioButton` |
| Temas | Claro/oscuro/sistema + temas | Solo oscuro fijo |

### 13.2 Tokens de diseño propuestos

| Token | Valor | Uso |
|---|---|---|
| `artemis_bg` | `#0B0D13` | Fondo principal |
| `artemis_surface` | `#131620` | Tarjetas, toolbar, sidebar |
| `artemis_surface_raised` | `#1A1E2B` | Hojas y elementos elevados |
| `artemis_border` | `#262B3B` | Bordes de 1 dp |
| `artemis_text` | `#E2E8F0` | Texto principal |
| `artemis_text_muted` | `#94A3B8` | Texto secundario |
| `artemis_primary` | `#F59E0B` | Acción principal, foco |
| `artemis_primary_hover` | `#FBBF24` | Estados pressed/hover |
| `artemis_accent` | `#06B6D4` | Chips Apollo, información |
| `artemis_success` | `#10B981` | Online, conectado |
| `artemis_danger` | `#F43F5E` | Errores, eliminar |
| `artemis_warning` | `#F59E0B` | Avisos |
| `artemis_on_primary` | `#0B0D13` | Texto sobre ámbar |
| Radios | 12/16 dp | Tarjetas y hojas |
| Espaciado | 4/8/12/16/24/32 dp | Escala |
| Tipografía | Sans + mono para versión | Títulos 28/20/16, cuerpo 14, caption 12 |

### 13.3 Backlog priorizado

| ID | Mejora | Fase | Estado |
|---|---|---|---|
| UI-01 | Tokens de color, dimens y estilos Apollo en `values/` | 1 | Hecho |
| UI-02 | `MaterialToolbar` en `PcView` y `AppView` con título, búsqueda, ajustes y añadir | 1 | Hecho |
| UI-03 | Tarjetas modernas de PC con estado, versión y acciones | 1 | Hecho |
| UI-04 | Tarjetas modernas de apps con scrim, título y badges | 1 | Hecho |
| UI-05 | Menús contextuales a `BottomSheetDialog` | 1 | Parcial — cerrar en Fase 2 (apps) |
| UI-06 | Edge-to-edge e insets en pantallas no críticas | 1 | Parcial — revisar en Fase 2 |
| UI-07 | Estados vacíos, carga y skeletons | 1 | Parcial — skeletons en Fase 2 |
| UI-08 | Corregir resumen español de deadzone (valores negativos) | 1 | Hecho |
| UI-09 | Ajustes por secciones con búsqueda y valores actuales | 2 | Pendiente (Fase 2) |
| UI-10 | Menú en juego como bottom sheet con iconos y secciones | 2 | Pendiente (Fase 2) |
| UI-11 | Perfiles como tarjetas con acciones y estado activo | 2 | Pendiente (Fase 2) |
| UI-12 | Tarjeta de anfitrión Apollo y capacidades del host | 2 | Pendiente (Fase 2) |
| UI-13 | Control tester con ejes, botones, gatillos y visualizador de deadzone | 3 | Pendiente |
| UI-14 | Editor OSC/teclado con rejilla, snapping, presets y undo/redo | 3 | Pendiente |
| UI-15 | Onboarding y emparejamiento con PIN/QR | 3 | Pendiente |
| UI-16 | Selector de temas (Apollo Dark/Light/System/OLED) | 3 | Pendiente |
| UI-17 | Branding: logo, icono adaptativo, splash y store assets | 3 | Pendiente |
| UI-18 | Accesibilidad: 48 dp, TalkBack, contraste, escalado, reducir movimiento | 1–3 | Pendiente |
| UI-19 | Diagnóstico de red/códec/logs y “copiar diagnóstico” | 3 | Pendiente |
| UI-20 | Animaciones y microinteracciones | 3 | Pendiente |
| UI-21 | Gestión de energía remota: apagar/reiniciar el PC anfitrión | 2A/3B | Hecho (3B); 2A opcional |

### 13.4 Detalle por pantalla

#### Inicio / lista de PCs (UI-02, UI-03, UI-05, UI-07)

- `MaterialToolbar` con wordmark Artemis, badge de versión, búsqueda, ajustes, ayuda y añadir.
- Tarjeta de anfitrión: Apollo 2026, versión, plataforma, driver de gamepad y estado.
- Chips de perfil activo y filtro (Todos / Online / Favoritos).
- PCs como `MaterialCardView`: nombre, icono/portada, punto de estado, “Online · Apollo 1.0.0”, última conexión.
- Acciones: Despertar, Emparejar, Abrir Web UI, Detalles, Eliminar; en bottom sheet con iconos.
- Búsqueda, orden (reciente/nombre/estado), pull-to-refresh y skeletons.
- Estado vacío: “No hay PCs” + “Añadir manualmente” + “Buscar en red”.

#### Lista de apps (UI-04, UI-07)

- Toolbar con nombre de PC, estado, búsqueda, orden y selector grid/lista.
- Tarjetas 16:9 con degradado, título y badges: HDR, AV1, 4K, 120 FPS, Virtual Display, Clipboard, Server Cmd.
- Acciones rápidas: jugar, favorito, detalles, acceso directo, perfil de app.
- Sección “Recientes” y “Favoritos”.
- Bottom sheet de detalles con portada, descripción, capacidades y botón “Iniciar”.

#### Ajustes (UI-09)

- Búsqueda global (SearchPreference ya disponible).
- Secciones: Streaming, Pantalla, Controles, Audio, Apollo, Apariencia, Avanzado.
- Cada fila con icono, título, resumen y valor actual como chip.
- Cabecera con perfil activo y “Gestionar perfiles”.
- “Restaurar valores” por sección.
- Enlaces a Web UI, documentación y troubleshooting de Apollo.

#### Perfiles (UI-11)

- Tarjetas con nombre, acento, estado activo, última modificación y número de overrides.
- Acciones: activar, editar, duplicar, exportar, eliminar; swipe actions y FAB.
- Estado vacío con ilustración.
- Detalle: diff contra valores base, “Aplicar a esta app”, “Usar como predeterminado”.

#### Menú en juego (UI-10)

- Bottom sheet (o side sheet en landscape) con cabecera de sesión: PC/app, latencia, resolución/FPS y calidad.
- Secciones: Pantalla, Entrada, Apollo, Sesión.
- Filas con icono, etiqueta, estado actual y `Switch`/chip.
- Acciones: HUD, teclado, OSC, modo ratón, zoom, rotar, portapapeles, comandos de servidor, desconectar, salir.
- HUD rediseñado con sparklines y colores por umbral.
- Snackbars con icono y acción “Reintentar” en lugar de `Toast` sueltos.

#### Editor OSC/teclado (UI-14)

- Rejilla, snapping, guías de alineación, multicapa, undo/redo.
- Presets Xbox/PlayStation/Switch/custom.
- Opacidad, escala y vibración con sliders y previsualización en vivo.
- Import/export y perfiles por juego.

#### Diagnóstico y mandos (UI-13, UI-19)

- Control tester con ejes en vivo, círculo de deadzone, botones, gatillos, giroscopio y batería.
- Visualizador de deadzone: mover el slider y ver el círculo/valores en tiempo real.
- Estado del host: ViGEmBus/Virtual HID, SudoVDA, permisos y versión de Apollo.
- Test de red: latencia, jitter, pérdida, MTU.
- Visor de logs y “Copiar diagnóstico”.
- Ayuda directa al problema de drift fantasma.

#### Marca y onboarding (UI-15, UI-16, UI-17)

- Logo/wordmark Artemis coherente con el estilo orbital de Apollo (ámbar/cian).
- Icono adaptativo, splash y store assets.
- Onboarding de 3 pasos: “Conecta tu PC”, “Empareja”, “Elige tu perfil”.
- Emparejamiento con PIN grande, QR y estado en vivo.
- Tema inicial Apollo Dark; opciones System/Light/OLED.

#### Accesibilidad (UI-18)

- 48 dp mínimos, content descriptions, contraste, escalado de fuente, TalkBack.
- Reducir movimiento, feedback háptico, navegación con mando/TV.
- Adaptación a tablet, DeX, landscape y foldables.

### 13.5 Criterios de aceptación de la Fase 1

- [x] Tokens Apollo en `colors.xml`, `dimens.xml` y `styles.xml`.
- [x] `MaterialToolbar` funcionando en `PcView` y `AppView`.
- [x] Tarjetas de PC y apps con el nuevo estilo.
- [x] Al menos un menú contextual convertido a bottom sheet (PC).
- [x] Insets/edge-to-edge revisados en pantallas no críticas.
- [x] Estados de carga/vacío revisados (skeletons quedan para Fase 2).
- [x] Resumen español de deadzone corregido.
- [x] Build release y debug correctos.
- [x] `Release\artemis.apk` actualizado.
- [x] Este documento actualizado con el resultado.

### 13.6 Resultado de la Fase 1 (2026-11-20)

- **Commit del repositorio:** `ce94e26`.
- **Repositorio:** <https://github.com/leonidas10009/Artemis-2026>
- Cambios aplicados:
  - Paleta y tokens Apollo en `colors.xml`, `dimens.xml` y `styles.xml`.
  - `MaterialToolbar` con título, subtítulo, búsqueda, ajustes, ayuda y añadir.
  - Búsqueda con filtrado real en PCs y apps.
  - Tarjetas modernas de PC con estado online/offline/refrescando.
  - Tarjetas modernas de apps con scrim y título.
  - Bottom sheet de acciones para PCs (despertar, emparejar, apps, detalles, red, eliminar).
  - Estados vacíos “No se encontraron PCs/apps” y spinner de búsqueda.
  - Resumen español de deadzone corregido.
  - Exportación automática de APK a `Release\artemis*.apk`.
- Pendiente inmediato:
  - Bottom sheet de acciones para apps (UI-05).
  - Skeletons reales (UI-07).
  - Revisión visual en dispositivo/tablet/TV.
  - Fases 2–3; el alcance y orden de la Fase 2 está detallado en 13.7.

### 13.7 Fase 2 — Alcance, orden y criterios de aceptación

**Objetivo:** cerrar los pendientes de la Fase 1 y modernizar las pantallas de uso
diario sin tocar el pipeline de vídeo, entrada o streaming.

**Alcance:**

| ID | Entrega | Archivos principales | Criterio de aceptación |
|---|---|---|---|
| UI-05 | Bottom sheet de acciones de apps | `AppView.java`, `AppGridAdapter.java`, layout/helper de acciones, `menu/app_view_menu.xml` | Long-press/click abre la hoja; conserva iniciar/reanudar, salir, acceso directo, ocultar, detalles, exportar; sin duplicar el menú contextual |
| UI-06 | Revisión de insets/edge-to-edge | `UiHelper.java`, layouts de `PcView`/`AppView`, `styles.xml` | Toolbar bajo la barra de estado; nada tapado por navegación; correcto en portrait, landscape, tablet, DeX y TV |
| UI-07 | Skeletons de carga | `pc_grid_item.xml`, `app_grid_item.xml`, drawables de skeleton, adaptadores | Placeholder mientras carga; sin saltos de layout; estados vacíos estables |
| UI-12 | Tarjeta de anfitrión Apollo | `PcView.java`, `ComputerDetails`, layout de host card, `UiHelper` | Muestra Apollo/versión/plataforma/driver/estado; modo offline claro; campos desconocidos ocultos |
| UI-09 | Ajustes por secciones + búsqueda | `preferences.xml`, `StreamSettings.java`, layouts de preferencias, strings | Todas las preferencias actuales siguen accesibles; búsqueda funciona; valor actual visible; reset/import/export sin regresiones |
| UI-11 | Perfiles como tarjetas | `ProfilesActivity.java`, `ProfilesAdapter.java`, `row_profile.xml`, `activity_profiles.xml`, `EditProfileActivity` | Estado activo visible; activar/editar/duplicar/exportar/eliminar; estado vacío; sin pérdida de datos |
| UI-10 | Menú en juego como bottom sheet | `GameMenu.java`, `activity_game.xml`, layout de menú, strings | Conserva todas las acciones actuales; secciones e iconos; landscape; foco con mando/TV; sin fugas de entrada |

**Orden de implementación recomendado:**

1. **UI-05** — bottom sheet de apps (cierra Fase 1).
2. **UI-06/UI-07** — insets y skeletons (cierra Fase 1).
3. **UI-12** — tarjeta de anfitrión Apollo (bajo riesgo, alto valor).
4. **UI-09** — ajustes por secciones.
5. **UI-11** — perfiles como tarjetas.
6. **UI-10** — menú en juego (mayor riesgo; probar en dispositivo).

**Definición de terminado (Fase 2):**

- [ ] Build release y debug correctos.
- [ ] `Release` solo contiene `artemis.apk` y `artemis-debug.apk` (+ `LEEME_APK.txt`).
- [ ] Sin regresiones en conexión, vídeo, audio, mandos, teclado, perfiles ni pantalla externa.
- [ ] Probado en teléfono/tablet/landscape; TV si aplica.
- [ ] Documentación actualizada y commit subido a GitHub.
- [ ] APK debug firmado para pruebas.

**Riesgos y mitigaciones:**

- **Ajustes:** perder alguna clave al reorganizar `preferences.xml`.
  Mitigación: mantener todas las `android:key` existentes y probar cada sección.
- **Menú en juego:** fugas de foco/entrada al abrir la hoja.
  Mitigación: probar con mando, teclado y táctil; conservar el comportamiento de `GameMenu`.
- **Perfiles:** corrupción o pérdida de perfiles al cambiar el adaptador.
  Mitigación: no tocar el formato JSON; probar activar/editar/borrar/exportar.
- **TV/DeX:** navegación con D-pad y layout.
  Mitigación: probar foco y `nextFocus*` en tablets/TV.

**No incluido en Fase 2:** control tester, editor OSC, onboarding/QR, selector de
temas, branding y diagnóstico de red (Fase 3).

### 13.8 Riesgos y no objetivos

- **No** reescribir en Compose.
- **No** tocar `StreamContainer`, `MediaCodecDecoderRenderer`, `VirtualController` ni el pipeline de entrada por motivos estéticos.
- **No** copiar el layout web de Apollo tal cual: Android necesita Material, bottom sheets y áreas táctiles.
- **No** añadir Bootstrap, JS ni fuentes web al APK.
- **No** romper la navegación con mando/TV ni el modo inmersivo del stream.
- **No** cambiar package IDs ni la firma.

### 13.9 Gestión de energía remota (apagar/reiniciar el PC anfitrión)

**Idea:** añadir botones dedicados en Artemis para apagar o reiniciar el PC donde
corre Apollo, con confirmación y permisos.

#### 13.9.1 Qué existe ya en Apollo

Apollo ya tiene un sistema de **comandos de servidor**:

- `server_cmd` en `sunshine.conf` es una lista de comandos con nombre, comando y
  `elevated`.
- El host los envía al cliente emparejado en `serverinfo`
  (`src/nvhttp.cpp`), solo si el cliente tiene el permiso `server_cmd`.
- El cliente Artemis los recibe en `ComputerDetails.serverCommands` y los muestra
  en el menú de juego (`GameMenu.showServerCmd`).
- Al pulsar uno, Artemis envía el índice por el control stream
  (`Game.sendExecServerCmd` → `MoonBridge` → `LiSendExecServerCmd`).
- El host valida `PERM::server_cmd` y ejecuta el comando en `src/stream.cpp`
  (`IDX_EXEC_SERVER_CMD`), con `elevated` si está configurado.

Por tanto, **para apagar/reiniciar durante una sesión de streaming no hace falta
tocar el código C++ de Apollo**: basta con configurar los comandos en el host y
añadir botones dedicados en el cliente.

#### 13.9.2 Fase 2A — botones dentro del juego (sin cambios en Apollo)

1. En `_update\Release\config\sunshine.conf`, añadir a `server_cmd`:
   ```ini
   server_cmd = [{"name":"Bubbles","cmd":"bubbles.scr","elevated":false},{"name":"Apagar PC","cmd":"shutdown /s /t 5","elevated":true},{"name":"Reiniciar PC","cmd":"shutdown /r /t 5","elevated":true},{"name":"Cancelar apagado","cmd":"shutdown /a","elevated":true}]
   ```
   - Mantener los comandos existentes.
   - `elevated:true` es necesario en Windows para `shutdown`.
   - `/t 5` deja 5 segundos para cancelar con `shutdown /a`.
   - En Linux: `systemctl poweroff` / `systemctl reboot`.
2. En Apollo Web UI → Clients, conceder `server_cmd` al cliente Artemis.
3. En Artemis, añadir una sección **Energía** en `GameMenu`:
   - Buscar por nombre en `game.getServerCmds()` (sin distinguir mayúsculas):
     `apagar`, `shutdown`, `poweroff`; `reiniciar`, `restart`, `reboot`;
     `cancelar`, `shutdown /a`.
   - Si existen, mostrar botones dedicados “Apagar PC” y “Reiniciar PC” con
     confirmación.
   - Si no existen, mantener el menú actual de “Comandos de servidor”.
4. Confirmación obligatoria:
   - Diálogo: “¿Apagar el PC anfitrión? Se cerrará la sesión.”
   - Para reiniciar, el mismo aviso.
   - Opcional: cuenta atrás de 3–5 segundos y opción “Cancelar”.

**Ventajas:** funciona ya, sin protocolo nuevo, y usa el permiso existente.
**Limitación:** solo está disponible **durante una sesión** (el control stream está
activo).

#### 13.9.3 Fase 3B — implementada (botones fuera de la sesión)

**Estado: implementado el 2026-11-20.**

Host:
- Ruta HTTPS `POST /servercmd?index=N` en `src/nvhttp.cpp`.
- Requiere certificado de cliente emparejado y permiso `server_cmd`.
- Ejecuta el comando con `stream::execute_server_command(index)`, la misma lógica
  que usa el control stream.
- Responde `200 OK`, `400`, `401` o `404` según el caso.
- No se añadió un permiso nuevo: se reutiliza `server_cmd`.
- `docs/configuration.md` actualizado con el endpoint y un ejemplo de energía.

Cliente:
- `NvHTTP.sendServerCommand(int index)` hace un POST autenticado al host.
- `PcView` muestra una sección **Energía** en el bottom sheet del PC.
- Detecta por nombre: `apagar/shutdown/poweroff`, `reiniciar/restart/reboot`,
  `cancelar/cancel shutdown`.
- Confirmación obligatoria antes de enviar.
- Si el host no tiene comandos de energía o el cliente no tiene permiso, la sección
  no aparece.

Configuración del host:
- `sunshine.conf` actualizado con `Apagar PC`, `Reiniciar PC` y `Cancelar apagado`.
- Respaldo: `sunshine.conf.bak-2026-11-20-power`.
- `apollo.exe` recompilado y desplegado; respaldo
  `apollo.exe.old-2026-11-20-power`.
- `ApolloService` reiniciado; el log confirma las 4 entradas de `server_cmd`.
- El endpoint sin certificado de cliente aborta la conexión (verificado con `curl`),
  por lo que queda protegido por TLS y por el permiso `server_cmd`.

#### 13.9.4 Recomendación actual

- **Fase 3B:** hecha. Botones “Apagar PC” y “Reiniciar PC” en la página del PC,
  sin iniciar stream.
- **Fase 2A:** opcional. Añadir los mismos botones al menú en juego si se quiere
  redundancia; no es necesaria para el objetivo actual.
- **No** añadir un botón de apagado sin confirmación ni permiso.
- Mantener `server_cmd` solo para clientes de confianza y no exponer Apollo a
  Internet sin HTTPS/VPN.

#### 13.9.5 Archivos afectados

| Lado | Archivo | Cambio |
|---|---|---|
| Host | `_update/Release/config/sunshine.conf` | Comandos de energía añadidos |
| Host | `src/stream.h` | Declaración `execute_server_command` |
| Host | `src/stream.cpp` | Implementación reutilizable del comando |
| Host | `src/nvhttp.cpp` | Endpoint `POST /servercmd` + validación de permiso |
| Host | `docs/configuration.md` | Documentación del endpoint y seguridad |
| Host | `src/crypto.h` | Permiso `server_cmd` ya existente |
| Cliente | `NvHTTP.java` | `sendServerCommand(int)` |
| Cliente | `PcView.java` | Sección Energía + confirmación + envío |
| Cliente | `values/strings.xml`, `values-es/strings.xml` | Textos de energía |
| Cliente | `app/build.gradle` | Exportación solo de APK arm64 |

#### 13.9.6 Estado de pruebas

- **Commit Artemis:** `b39ed02` — *Energía remota: endpoint /servercmd y botones
  apagar/reiniciar*.
- **Commit Apollo:** `e5b72c9f` — *Add authenticated /servercmd endpoint for remote
  server commands*.
- Host recompilado y desplegado.
- Servicio reiniciado con UAC; log confirma `server_cmd`:
  `Bubbles`, `Apagar PC`, `Reiniciar PC`, `Cancelar apagado`.
- Endpoint sin certificado: conexión abortada (protección TLS correcta).
- Cliente debug/release compilado.
- **Pendiente:** prueba end-to-end desde el dispositivo (no había ADB conectado
  durante la implementación).

---

## 14. Pendientes

- [x] Actualizar la traducción española del resumen de deadzone.
- [x] Fase 1 de interfaz: tokens, toolbar, tarjetas, búsqueda, bottom sheet y ajustes de insets.
- [x] Copia automática de APK a `artemis_V2026\Release` (solo `artemis.apk` y `artemis-debug.apk`).
- [x] Limpieza de APK antiguos y variantes ABI no usadas.
- [x] Incluir esta memoria en el repositorio.
- [ ] Fase 2: UI-05, UI-06, UI-07, UI-12, UI-09, UI-11 y UI-10 (ver 13.7).
- [x] UI-21 Fase 3B: endpoint autenticado de energía + botones en la lista de PCs.
- [ ] UI-21 Fase 2A (opcional): botones de energía también en el menú en juego.
- [ ] Prueba end-to-end de energía desde un dispositivo Artemis.
- [ ] Probar la Fase 1 en dispositivo/tablet/TV.
- [ ] Implementar `MotionRange.getFlat()` como deadzone mínima de sticks.
- [ ] Evaluar auto-centrado al conectar.
- [ ] Corregir el `|=` de fusión de ejes/gatillos.
- [ ] Añadir logs de ejes elegidos y valores en reposo.
- [ ] Probar Virtual HID con `xseries`/`ds5`.
- [ ] Probar el mando Xbox en los juegos/frontends afectados y documentar resultados.
- [ ] Firmar el release con la keystore definitiva.
- [ ] Verificar que `Release\artemis.apk` se actualiza en cada compilación.
- [ ] Crear tag/release de GitHub cuando el APK release esté firmado.
- [ ] Añadir changelog de la versión 58 en `fastlane` si se publica.
- [ ] Actualizar este documento tras cada nueva actualización.

---

## 15. Publicación en GitHub

- **Repositorio:** <https://github.com/leonidas10009/Artemis-2026>
- **Rama principal:** `main`
- **Estado:** publicado; rama `main` activa. Primer commit `ce94e26`; los commits posteriores incluyen documentación y limpieza de APK. Consultar `git log -1` para el HEAD actual.
- **Remoto:** `origin` → `https://github.com/leonidas10009/Artemis-2026.git`
- **Contenido:** código fuente completo, core `moonlight-common-c` vendorizado,
  `MEMORIA_ARTEMIS.md`, `README.md` actualizado, `.gitattributes` y `.gitignore`.
- **No se versiona:** `local.properties`, `build/`, `.gradle/`, APKs.
- **Primer commit:** `Artemis 20.2.7 — actualización a Moonlight v12.2, core Apollo
  fusionado y Fase 1 de interfaz Apollo 2026`.

Comandos de referencia:

```powershell
cd E:\ProyectoProgramacion\apolo_V2026\artemis_V2026\Artemis\moonlight-android-moonlight-noir
git init -b main
git config user.name "leonidas10009"
git config user.email "leonidas10009@users.noreply.github.com"
git add .
git commit -m "Artemis 20.2.7 - Moonlight v12.2 + Apollo core + UI Phase 1"
git remote add origin https://github.com/leonidas10009/Artemis-2026.git
git push -u origin main
```

Para futuras actualizaciones:

```powershell
git add .
git commit -m "Descripción del cambio"
git push
```

Notas:
- Al estar el core vendorizado, no se necesita `git submodule update`.
- Para futuras actualizaciones, conservar la lógica de Artemis y actualizar el core
  junto con el JNI.
- Los APK de release se generan sin firmar; usar la keystore definitiva antes de
  publicarlos en GitHub Releases.

---

> **Regla de oro:** Artemis no debe perder sus funciones propias en una actualización.
> Ante un conflicto, conservar la lógica de Artemis y adaptar solo lo necesario de
> Moonlight. El core debe actualizarse siempre junto con el JNI y el protocolo.
