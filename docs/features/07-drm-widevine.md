# DRM Widevine — Mediastream SDK Android

## 1. Resumen

El SDK soporta **Widevine L1/L3** (único esquema DRM implementado). La integración requiere:

1. Stream en formato **DASH** (`.mpd`) — HLS no soporta Widevine en Android.
2. URL del servidor de licencias Widevine (proveedor: Axinom, BuyDRM, Ezdrm, etc.).
3. Headers de autorización requeridos por el proveedor (token, API key, etc.).

---

## 2. Prerequisitos del contenido

El stream DRM **debe** estar disponible en formato DASH en la plataforma Mediastream. El SDK selecciona el formato del contenido consultando la API de Mediastream y eligiendo la clave `"mpd"` del mapa `srcList` de la respuesta. Si el contenido solo tiene clave `"hls"`, el SDK usará HLS y Widevine **no funcionará**.

Verificación: en la plataforma Mediastream el contenido debe tener una URL `.mpd` en su configuración de formatos.

---

## 3. La clase `DrmData`

Ubicación en el SDK: `MediastreamPlayerConfig.kt`

```kotlin
class DrmData(
    var drmUrl: String,           // URL del servidor de licencias Widevine
    var drmHeaders: Map<String, String>  // Headers de autorización
)
```

### Cómo el SDK la consume

`MediastreamPlayer.kt` — líneas 3441–3450:

```kotlin
if (config.drmData != null) {
    mediaItem.setDrmConfiguration(
        Util.getDrmUuid("widevine")?.let {  // Esquema hardcoded: solo Widevine
            DrmConfiguration.Builder(it)
                .setLicenseUri(config.drmData?.drmUrl)
                .setLicenseRequestHeaders(config.drmData?.drmHeaders ?: emptyMap())
                .build()
        }
    )
}
```

`DrmSessionManagerProvider` se crea antes en `createMediaSourceFactory()` (líneas 2219–2323) y se pasa al `DefaultMediaSourceFactory`. Media3/ExoPlayer maneja la negociación completa de licencia.

---

## 4. Configuración — VOD con DRM

```kotlin
val drmHeaders = mapOf(
    "X-AxDRM-Message" to "eyJhbGci..."  // JWT de Axinom
)

val config = MediastreamPlayerConfig().apply {
    id          = "TU_CONTENT_ID"
    accountID   = "TU_ACCOUNT_ID"
    type        = MediastreamPlayerConfig.VideoTypes.VOD
    environment = MediastreamPlayerConfig.Environment.PRODUCTION
    videoFormat = MediastreamPlayerConfig.AudioVideoFormat.DASH  // OBLIGATORIO
    autoplay    = true
    isDebug     = true

    drmData = MediastreamPlayerConfig.DrmData(
        drmUrl     = "https://d231f6fd.drm-widevine-licensing.axprod.net/AcquireLicense",
        drmHeaders = drmHeaders
    )
}
```

---

## 5. Configuración — Live con DRM

```kotlin
val drmHeaders = mapOf(
    "X-AxDRM-Message" to "eyJhbGci..."
)

val config = MediastreamPlayerConfig().apply {
    id          = "TU_LIVE_ID"
    accountID   = "TU_ACCOUNT_ID"
    type        = MediastreamPlayerConfig.VideoTypes.LIVE
    environment = MediastreamPlayerConfig.Environment.DEV
    videoFormat = MediastreamPlayerConfig.AudioVideoFormat.DASH  // OBLIGATORIO
    dvr         = false
    autoplay    = true
    isDebug     = true

    drmData = MediastreamPlayerConfig.DrmData(
        drmUrl     = "https://d231f6fd.drm-widevine-licensing.axprod.net/AcquireLicense",
        drmHeaders = drmHeaders
    )
}
```

> **`videoFormat = DASH` es obligatorio.** Sin él, el SDK selecciona HLS por defecto (`AudioVideoFormat.DEFAULT = "hls"`) y la licencia Widevine no se aplica aunque `drmData` esté seteado.

---

## 6. Proveedor Axinom (configuración usada en este QA app)

### Header requerido

Axinom usa un único header:

```
X-AxDRM-Message: <JWT firmado>
```

### Estructura del JWT (decodificado)

```json
{
  "version": 1,
  "com_key_id": "24cff0fc-4fec-44f2-abe9-b36901 65b6ad",
  "message": {
    "type": "entitlement_message",
    "version": 1,
    "expiration_date": "2026-04-23T18:39:15.317Z",
    "keys": [
      { "id": "24AF6D7F-DD5F-479D-B01D-92714D19937E" }
    ]
  }
}
```

Campos relevantes para QA:
- `expiration_date` — el token expira en esta fecha/hora UTC. Tokens vencidos producen error `4032` de Widevine.
- `keys[].id` — Key ID que debe coincidir con el stream. Mismatch = error `4032`.
- `com_key_id` — Communication Key del tenant Axinom.

### URL del servidor de licencias

```
https://d231f6fd.drm-widevine-licensing.axprod.net/AcquireLicense
```

El prefijo `d231f6fd` es el **tenant ID** en la infraestructura de Axinom.

---

## 7. Análisis del escenario actual (`VideoLiveScenarioActivity`)

### ✅ Correcto

| Aspecto | Valor |
|---|---|
| `videoFormat` | `DASH` — correcto |
| Header DRM | `X-AxDRM-Message` — correcto para Axinom |
| `DrmData` constructor | URL + headers — correcto |
| `dvr = false` | Correcto para baseline DRM test |
| `isDebug = true` | Activa `LoggingDataSourceFactory` — útil para ver requests |

### ⚠️ Problemas

**1. Token expirado (crítico)**
```kotlin
// VideoLiveScenarioActivity.kt:14
val DRM_ACCESS_TOKEN = "eyJhbGci..."  // expira 2026-04-23T18:39:15Z — HOY
```
El token JWT embebido expiró. El servidor Axinom retornará error de autorización. Regenerar token con fecha de expiración futura.

**2. Referencia de ID inconsistente**
```kotlin
id = TestContent.Video.LIVE  // ❌ debería ser TestContent.Drm.LIVE_ID
```
`TestContent.Drm.LIVE_ID` existe con el mismo valor (`699afcb05a41925324fa4605`) pero semánticamente el escenario DRM debería usar la constante del objeto `Drm`.

**3. Token y headers fuera de `buildConfig()`**
```kotlin
// Actual — vals de clase, se crean al instanciar la Activity
val DRM_ACCESS_TOKEN = "..."
val drmHeaders = HashMap<String, String>().apply { ... }
```
```kotlin
// Preferible — locales a buildConfig()
override fun buildConfig() = MediastreamPlayerConfig().apply {
    val token = "..."
    val headers = mapOf("X-AxDRM-Message" to token)
    drmData = MediastreamPlayerConfig.DrmData(LICENSE_URL, headers)
    ...
}
```
No es bug funcional, pero vals de clase visibles desde fuera sin necesidad.

**4. TODO stale en `TestContent.Drm`**
```kotlin
// TestContent.kt:108
// TODO: cuando SDK exponga DrmData API, agregar LICENSE_URL y HEADERS aquí
```
`DrmData` ya está expuesta desde hace versiones. El TODO debe limpiarse y las constantes `LICENSE_URL` y `HEADERS` pueden agregarse al objeto `Drm` ahora.

---

## 8. Errores comunes y debugging

### Tabla de errores Widevine

| Código | Causa | Solución |
|---|---|---|
| `ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED` | Token expirado, URL incorrecta, headers mal formados | Verificar token, URL, header name |
| `ERROR_CODE_DRM_CONTENT_ERROR` | Key ID del token no coincide con el stream | Regenerar token con key ID correcto |
| `ERROR_CODE_DRM_DEVICE_REVOKED` | Device no tiene licencia Widevine válida | Solo L3 en emuladores; L1 requiere hardware certificado |
| `4032` (código Axinom) | Combinación de token vencido + key mismatch | Verificar `expiration_date` y `keys[].id` |

### Emuladores

Los emuladores solo soportan **Widevine L3** (sin hardware seguro). Algunos streams con política L1 fallarán incluso con token válido. Usar dispositivo físico para tests DRM completos.

### Logcat con `isDebug = true`

El SDK activa `LoggingDataSourceFactory` cuando `config.isDebug = true`. Filtra:

```bash
adb logcat -s SDK_QA/ERROR ExoPlayerLib:W DrmSessionManager:D *:S
```

La petición de licencia aparece como request HTTP a la URL de Axinom — visible en logcat si `isDebug = true`.

### Secuencia de callbacks esperada (DRM exitoso)

```
playerViewReady
onBuffering          ← descarga del MPD y negociación de licencia
onReady              ← licencia obtenida, stream descifrado, player listo
onPlay               ← reproducción iniciada
```

Si `onError` o `onPlaybackErrors` llega entre `onBuffering` y `onReady`, el error es de licencia DRM.

---

## 9. Referencia rápida

```kotlin
// Construcción mínima y correcta
MediastreamPlayerConfig().apply {
    id          = "CONTENT_ID"
    accountID   = "ACCOUNT_ID"
    type        = MediastreamPlayerConfig.VideoTypes.VOD  // o LIVE
    videoFormat = MediastreamPlayerConfig.AudioVideoFormat.DASH  // NO OMITIR
    environment = MediastreamPlayerConfig.Environment.PRODUCTION

    drmData = MediastreamPlayerConfig.DrmData(
        drmUrl     = "https://<tenant>.drm-widevine-licensing.axprod.net/AcquireLicense",
        drmHeaders = mapOf("X-AxDRM-Message" to "<JWT_VIGENTE>")
    )
}
```

### Checklist de validación

- [ ] `videoFormat = DASH` seteado
- [ ] Token JWT con `expiration_date` futura
- [ ] `keys[].id` en el token coincide con el key ID del stream
- [ ] URL de licencia correcta (tenant ID en el subdominio)
- [ ] Header name exacto: `X-AxDRM-Message` (case-sensitive en algunos servidores)
- [ ] Dispositivo físico para validar L1 (emulador solo L3)
- [ ] `isDebug = true` para ver requests de licencia en logcat
