# 09 — DRM (Digital Rights Management)

## Descripción

Soporte para contenido protegido con Widevine (estándar Android). El SDK delega la negociación de licencia a ExoPlayer/Media3. Proveedor usado en este QA app: **Axinom**.

---

## Requisitos del contenido

1. Stream en formato **DASH** (`.mpd`) — Widevine **no funciona sobre HLS** en Android
2. Contenido configurado con DRM en la plataforma Mediastream
3. Entorno **DEV** para contenido Axinom de este QA app

El SDK consulta la API de Mediastream con `videoFormat = "mpd"` y recibe la URL del manifiesto. Si el contenido no tiene DASH configurado en la plataforma, el SDK retorna error aunque `drmData` esté seteado.

---

## La clase `DrmData`

```kotlin
// MediastreamPlayerConfig.kt
class DrmData(
    var drmUrl: String,                    // URL del servidor de licencias
    var drmHeaders: Map<String, String>    // Headers de autorización (ej. JWT)
)
```

Campo en `MediastreamPlayerConfig`:
```kotlin
var drmData: DrmData? = null  // null = sin DRM
```

---

## Implementación — VOD con DRM

```kotlin
override fun buildConfig() = MediastreamPlayerConfig().apply {
    id          = "CONTENT_ID"
    accountID   = "ACCOUNT_ID"
    type        = MediastreamPlayerConfig.VideoTypes.VOD
    environment = MediastreamPlayerConfig.Environment.PRODUCTION
    videoFormat = MediastreamPlayerConfig.AudioVideoFormat.DASH  // OBLIGATORIO
    autoplay    = true
    isDebug     = true

    drmData = MediastreamPlayerConfig.DrmData(
        drmUrl     = "https://<tenant>.drm-widevine-licensing.axprod.net/AcquireLicense",
        drmHeaders = mapOf("X-AxDRM-Message" to "<JWT_VIGENTE>")
    )
}
```

---

## Implementación — Live con DRM

```kotlin
override fun buildConfig() = MediastreamPlayerConfig().apply {
    id          = "LIVE_CONTENT_ID"
    accountID   = "ACCOUNT_ID"
    type        = MediastreamPlayerConfig.VideoTypes.LIVE
    environment = MediastreamPlayerConfig.Environment.DEV
    videoFormat = MediastreamPlayerConfig.AudioVideoFormat.DASH  // OBLIGATORIO
    dvr         = false   // baseline: sin DVR
    autoplay    = true
    isDebug     = true

    drmData = MediastreamPlayerConfig.DrmData(
        drmUrl     = "https://d231f6fd.drm-widevine-licensing.axprod.net/AcquireLicense",
        drmHeaders = mapOf("X-AxDRM-Message" to "<JWT_VIGENTE>")
    )
}
```

> `videoFormat = DASH` es obligatorio. Sin él el SDK selecciona HLS por defecto y la licencia Widevine no aplica aunque `drmData` esté seteado.

---

## Flujo interno del SDK

1. `videoFormat = "mpd"` → API de Mediastream retorna URL `.mpd`
2. SDK detecta `drmData != null` → configura ExoPlayer con `DrmConfiguration.Builder("widevine")`
3. ExoPlayer descarga `.mpd`, encuentra `<ContentProtection>` con Widevine UUID
4. Negocia licencia contra `drmUrl` enviando los `drmHeaders`
5. Licencia OK → descifra segmentos y reproduce
6. Licencia falla → `onError` / `onPlaybackErrors` con código DRM

Código relevante en `MediastreamPlayer.kt`:
```kotlin
if (config.drmData != null) {
    mediaItem.setDrmConfiguration(
        Util.getDrmUuid("widevine")?.let {
            DrmConfiguration.Builder(it)
                .setLicenseUri(config.drmData?.drmUrl)
                .setLicenseRequestHeaders(config.drmData?.drmHeaders ?: emptyMap())
                .build()
        }
    )
}
```

---

## Proveedor Axinom (configuración QA)

Header requerido: `X-AxDRM-Message: <JWT>`

Estructura del JWT (decodificado):
```json
{
  "version": 1,
  "com_key_id": "24cff0fc-4fec-44f2-abe9-b36901 65b6ad",
  "message": {
    "type": "entitlement_message",
    "version": 1,
    "expiration_date": "2026-XX-XXT00:00:00Z",
    "keys": [{ "id": "24AF6D7F-DD5F-479D-B01D-92714D19937E" }]
  }
}
```

- `expiration_date` — token vence aquí. Expirado = error `4032` de Axinom
- `keys[].id` — debe coincidir con el Key ID del stream en la plataforma

URL del servidor: `https://d231f6fd.drm-widevine-licensing.axprod.net/AcquireLicense`
(`d231f6fd` = tenant ID de Axinom)

---

## Errores comunes

| Error | Causa | Solución |
|---|---|---|
| `ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED` | Token expirado, URL incorrecta, header mal escrito | Verificar JWT, URL, nombre exacto del header |
| `ERROR_CODE_DRM_CONTENT_ERROR` | Key ID del JWT no coincide con el stream | Regenerar JWT con Key ID correcto |
| `ERROR_CODE_DRM_PROVISIONING_FAILED` | Dispositivo no provisionado para Widevine | Problema de dispositivo |
| `ERROR_CODE_DRM_DISALLOWED_OPERATION` | Restricción de salida (HDCP) | Verificar nivel Widevine del dispositivo |
| `ERROR_CODE_DRM_DEVICE_REVOKED` | Device sin licencia Widevine válida | Usar dispositivo físico certificado |
| `ClassCastException: ContextImpl → Activity` | Bug SDK en Youbora al cambiar estado de playback | Fix en el SDK, no workaroundeable desde QA |
| Pantalla negra / sin imagen | Dispositivo solo soporta L3, stream requiere L1 | Usar dispositivo físico para L1 |
| `onError` inmediato (sin buffer) | Contenido sin DASH en plataforma | Verificar que el content ID tenga `.mpd` configurado |

---

## Secuencia de callbacks (DRM exitoso)

```
playerViewReady
onBuffering          ← descarga MPD + negociación de licencia Widevine
onReady              ← licencia obtenida, stream descifrado, listo
onPlay               ← reproducción iniciada
```

Si `onError` o `onPlaybackErrors` llega entre `onBuffering` y `onReady` → el error es de licencia.

---

## Debugging

Filtrar logcat con `isDebug = true`:
```bash
adb logcat -s ExoPlayerLib:W DrmSessionManager:D MediastreamPlayer:D *:S
```

La request de licencia aparece como HTTP POST a la URL de Axinom.

---

## Niveles Widevine

- **L1** — descifrado en hardware seguro (TEE). Requerido para contenido HD protegido. Solo dispositivos físicos certificados.
- **L3** — descifrado en software. Funciona en todos los Android 4.4+, incluyendo emuladores. Algunos streams con política L1 fallan en L3.

Verificar nivel del dispositivo:
```kotlin
val drm = MediaDrm(UUID(0xEDEF8BA979D64ACEL.toLong(), -0x650B1CB8B3B5E9EBL))
val securityLevel = drm.getPropertyString("securityLevel")  // "L1", "L3"
```

---

## Checklist de validación

- [ ] Content ID tiene DASH configurado en la plataforma (no solo HLS)
- [ ] `videoFormat = AudioVideoFormat.DASH` seteado
- [ ] JWT con `expiration_date` futura
- [ ] `keys[].id` en el JWT coincide con el Key ID del stream
- [ ] Header name exacto: `X-AxDRM-Message` (case-sensitive)
- [ ] Dispositivo físico para validar L1 (emulador = solo L3)
- [ ] `isDebug = true` para ver requests de licencia en logcat

---

*Feature: 09-drm | SDK v10.0.4-alpha05 | 2026-04-27*
