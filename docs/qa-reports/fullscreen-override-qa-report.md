# QA Report — Fullscreen Override API (SDK 10.0.5-alpha01)

**Feature branch:** `feat/allow-override-rotation`
**SDK version:** `10.0.5-alpha01`
**Fecha:** 2026-05-12
**Ejecutado por:** QA Automation — `com.example.sdk_qa.integration.FullscreenOverrideTest`

---

## 1. Alcance

Verificación de los dos cambios de comportamiento introducidos en `feat/allow-override-rotation`
y presentes en la versión `10.0.5-alpha01` del Mediastream SDK Android:

| # | Cambio | Descripción |
|---|--------|-------------|
| A | **Nueva propiedad `onFullscreenOnClick`** | `Consumer<MediastreamPlayer>` en `MediastreamPlayerConfig`. Cuando está configurado, **reemplaza** la llamada interna a `enterFullscreen()`. Los botones del player siguen haciendo su swap de visibilidad. |
| B | **Cambio en `onFullscreenOffClick`** | El override de "exit fullscreen" ahora se invoca **aunque `isOnFullscreen == false`** (se eliminó el guard anterior). Necesario para React Native / bridges donde el estado nativo puede desfasarse del estado del SDK. |

---

## 2. Dispositivos de prueba

| Dispositivo | Modelo | Android | API | Tipo | Conexión |
|-------------|--------|---------|-----|------|----------|
| **Samsung A53** | SM-A536E | Android 16 | 36 | Mobile | ADB WiFi (192.168.0.36:43585) |
| **Sony BRAVIA VU31** | BRAVIA VU31 | Android 14 | 34 | Android TV | ADB WiFi (192.168.0.24:5555) |

---

## 3. Casos de prueba

### 3.1 Tests de smoke — TV + Mobile

#### [FS-OVERRIDE-01] Player arranca normalmente con ambos overrides configurados

**Objetivo:** Verificar que instalar ambos callbacks (`onFullscreenOnClick` + `onFullscreenOffClick`)
no causa crashes ni bloquea la inicialización del player.

**Configuración:**
```kotlin
MediastreamPlayerConfig().apply {
    onFullscreenOnClick  = Consumer { _ -> /* override */ }
    onFullscreenOffClick = Consumer { _ -> /* override */ }
}
```

**Assertion:** `onReady` se dispara dentro de 20s, sin `onError` / `onEmbedErrors` / `onPlaybackErrors`.

| Dispositivo | Resultado | Observaciones |
|-------------|-----------|---------------|
| Samsung A53 | ✅ PASS | onReady en < 5s |
| BRAVIA VU31 | ✅ PASS | onReady en < 5s |

---

#### [FS-OVERRIDE-02] Player arranca normalmente SIN overrides (path default)

**Objetivo:** Garantizar que el código de override no rompe la ruta de inicialización por defecto.

**Configuración:** `MediastreamPlayerConfig` sin `onFullscreenOnClick` ni `onFullscreenOffClick`.

**Assertion:** `onReady` se dispara dentro de 20s, sin errores.

| Dispositivo | Resultado | Observaciones |
|-------------|-----------|---------------|
| Samsung A53 | ✅ PASS | Regresión descartada — path default intacto |
| BRAVIA VU31 | ✅ PASS | Regresión descartada — path default intacto |

---

### 3.2 Tests de interacción — Solo Mobile (`@MobileOnly`)

> Los botones de fullscreen están ocultos en TV por diseño del SDK Customizer
> (`isDeviceTV()` check). Los siguientes tests solo corren en dispositivos mobile.

#### [FS-OVERRIDE-03] `onFullscreenOnClick` se invoca al presionar el botón de entrar

**Objetivo:** Verificar que al presionar el botón "enter fullscreen", el `Consumer` registrado
es llamado exactamente una vez.

**Secuencia:**
1. Lanzar activity con `enableOn = true`
2. Esperar `onReady`
3. Leer `onFullscreenOnCallCount` inicial (debe ser 0)
4. `simulateFullscreenOnClick()` → `PlayerView.btn_fullscreen_on.performClick()`
5. Leer `onFullscreenOnCallCount` final

**Assertion:** `countAfter == countBefore + 1`

| Dispositivo | Resultado | Count inicial → final |
|-------------|-----------|----------------------|
| Samsung A53 | ✅ PASS | 0 → 1 |

---

#### [FS-OVERRIDE-04] Con override activo, `enterFullscreen()` NO se llama

**Objetivo:** Confirmar que el override **reemplaza** completamente a `enterFullscreen()`;
si el player hubiese entrado en fullscreen nativo, `onFullscreen` callback se dispararía.

**Secuencia:**
1. Lanzar con `enableOn = true`
2. Esperar `onReady`
3. `simulateFullscreenOnClick()`
4. Verificar callbacks y estado

**Assertions:**
- `onFullscreen` callback **NO** disparado
- `player.isOnFullscreen == false`

| Dispositivo | Resultado | `onFullscreen` fired | `isOnFullscreen` |
|-------------|-----------|----------------------|-----------------|
| Samsung A53 | ✅ PASS | No | `false` |

---

#### [FS-OVERRIDE-05] `onFullscreenOffClick` dispara SIN guard `isOnFullscreen` ⭐ Cambio clave

**Objetivo:** Verificar el cambio de comportamiento principal del branch: el override de "exit"
se invoca **aunque el player no esté en fullscreen nativo** (`isOnFullscreen == false`).

> Con el código **anterior**, `onFullscreenOffClick` solo se invocaba si `isOnFullscreen` era `true`.
> En bridges de React Native el estado nativo puede desincronizarse, por lo que el guard impedía
> que el bridge recibiera el evento. El nuevo comportamiento elimina esa restricción.

**Secuencia:**
1. Lanzar con `enableOn = true` + `enableOff = true`
2. Esperar `onReady`
3. `simulateFullscreenOnClick()` → override on dispara, `isOnFullscreen` sigue `false`
4. Verificar prerequisito: `isOnFullscreen == false`
5. `simulateFullscreenOffClick()` → **con código nuevo: override off dispara**

**Assertion:** `onFullscreenOffCallCount == 1` (con `isOnFullscreen == false`)

| Dispositivo | Resultado | `isOnFullscreen` pre-click-off | `offCallCount` |
|-------------|-----------|-------------------------------|---------------|
| Samsung A53 | ✅ PASS | `false` | 1 |

**Comportamiento anterior (regresión):** `offCallCount == 0` — override no habría sido llamado.

---

#### [FS-OVERRIDE-06] Sin override, el botón "exit" respeta el guard `isOnFullscreen`

**Objetivo:** Verificar que el path **default** (sin override) sigue su comportamiento original:
`exitFullscreen()` solo se ejecuta si el player está en fullscreen. No es una regresión.

**Secuencia:**
1. Lanzar SIN overrides
2. Esperar `onReady`
3. `simulateFullscreenOffClick()` con `isOnFullscreen == false`

**Assertion:** `offFullscreen` callback **NO** disparado.

| Dispositivo | Resultado | `offFullscreen` fired |
|-------------|-----------|----------------------|
| Samsung A53 | ✅ PASS | No |

---

#### [FS-OVERRIDE-07] Ambos overrides — cada uno llamado exactamente una vez en secuencia on→off

**Objetivo:** Verificar el flujo completo: click en "enter" → click en "exit", con ambos
overrides activos. Cada override debe invocarse exactamente una vez y de forma independiente.

**Secuencia:**
1. Lanzar con `enableOn = true` + `enableOff = true`
2. Esperar `onReady`
3. `simulateFullscreenOnClick()`
4. Verificar contadores intermedios
5. `simulateFullscreenOffClick()`
6. Verificar contadores finales

**Assertions:**

| Momento | `onFullscreenOnCallCount` | `onFullscreenOffCallCount` |
|---------|--------------------------|---------------------------|
| Tras click "enter" | 1 | 0 |
| Tras click "exit" | 1 | 1 |

| Dispositivo | Resultado |
|-------------|-----------|
| Samsung A53 | ✅ PASS |

---

## 4. Resumen de resultados

| ID | Descripción | Mobile (A53) | TV (BRAVIA) |
|----|-------------|:------------:|:-----------:|
| FS-OVERRIDE-01 | Player con ambos overrides → onReady fires | ✅ PASS | ✅ PASS |
| FS-OVERRIDE-02 | Player sin overrides → path default | ✅ PASS | ✅ PASS |
| FS-OVERRIDE-03 | onFullscreenOnClick se invoca al click | ✅ PASS | N/A (TV) |
| FS-OVERRIDE-04 | Override activo previene enterFullscreen() | ✅ PASS | N/A (TV) |
| FS-OVERRIDE-05 | offClick dispara sin isOnFullscreen guard ⭐ | ✅ PASS | N/A (TV) |
| FS-OVERRIDE-06 | Sin override, exit respeta guard (default) | ✅ PASS | N/A (TV) |
| FS-OVERRIDE-07 | Ambos overrides, secuencia on→off exacta | ✅ PASS | N/A (TV) |

**Total: 7/7 PASS (Mobile) · 2/2 PASS (TV smoke)**

---

## 5. Detalles de ejecución

### Samsung A53 — run completo (2026-05-12)

```
Dispositivo : samsung SM-A536E  (Android 16, API 36)
Build       : bd90e0b / 9bf6c96
Runner      : androidx.test.runner.AndroidJUnitRunner
Filtro      : @MediumTest + --target mobile
FullscreenOverrideTest : 7/7 PASS
Suite completa         : 54/54 PASS — 7m50s
```

### Sony BRAVIA VU31 — run completo (2026-05-12)

```
Dispositivo : Sony BRAVIA VU31  (Android 14, API 34)
Build       : 9bf6c96
Runner      : androidx.test.runner.AndroidJUnitRunner
Filtro      : @MediumTest + --target tv (excluye @MobileOnly)
FullscreenOverrideTest : 2/2 PASS (solo smoke — @MobileOnly excluidos en TV)
Suite completa         : 45/45 PASS — 12m10s
```

---

## 6. Notas técnicas

### Problema de R.id en AGP 8+ (non-transitive R classes)

`resources.getIdentifier(name, "id", sdkPackage)` devuelve `0` en AGP 8+ con R classes
no transitivas. La solución usada en `VideoFullscreenOverrideScenarioActivity.clickSdkButton()`:

```kotlin
val resId = runCatching {
    Class.forName("am.mediastre.mediastreamplatformsdkandroid.R\$id")
        .getField(name).getInt(null)
}.getOrElse { 0 }
```

Esto garantiza que `btn_fullscreen_on` / `btn_fullscreen_off` se encuentran correctamente
en el `PlayerView` del SDK, independientemente de la versión de AGP.

### Cobertura de LeakCanary

Los tests usan `SdkTestRule` (LeakCanary). Ninguna fuga de memoria fue detectada
en los 7 tests de `FullscreenOverrideTest` en Samsung A53 ni en la suite completa.

---

## 7. Conclusión

Los dos cambios de `feat/allow-override-rotation` funcionan correctamente en SDK `10.0.5-alpha01`:

1. **`onFullscreenOnClick`** reemplaza `enterFullscreen()` sin efectos secundarios.
2. **`onFullscreenOffClick` sin guard** permite que bridges (React Native, Flutter, etc.)
   reciban el evento de salida de fullscreen aunque el estado nativo esté desincronizado.

El path por defecto (sin overrides) no presenta regresiones. **Aprobado para integración.**
