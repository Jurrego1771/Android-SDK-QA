# DRM (Widevine) — reglas y criterios

> **Feature:** `drm` · **id_prefix:** `DRM` · **deeplink:** `sdkqa://scenario/drm`
> **Alcance:** contenido protegido con Widevine L1/L3 (único esquema DRM implementado). El SDK
> delega la negociación de licencia a ExoPlayer/Media3. Proveedor usado en este QA app: **Axinom**.
> Versión del SDK bajo test: la fija `app/build.gradle.kts` (línea 10.0.x).

---

## 1. Qué es correcto (reglas de comportamiento)

- **DASH obligatorio.** El stream DRM debe servirse en DASH (`.mpd`). Widevine **no funciona sobre
  HLS** en Android. El SDK pide el formato con `videoFormat = AudioVideoFormat.DASH`; si se omite,
  selecciona HLS por defecto y la licencia Widevine **no se aplica** aunque `drmData` esté seteado
  (sin error ni warning — ver [[risks]] DRM-RISK-001).
- **Resolución automática de licencia.** Con `drmData != null`, el SDK configura
  `DrmConfiguration.Builder("widevine")` (esquema hardcoded) con `licenseUri` + `licenseRequestHeaders`
  y ExoPlayer negocia la licencia. El integrador no hace nada más.
- **Propagación de error.** Una licencia que falla (token vencido, key mismatch, URL/headers malos)
  debe propagarse vía `onError` / `onPlaybackErrors` con código DRM — entre `onBuffering` y `onReady`.
- **Secuencia de callbacks esperada (DRM exitoso):**
  `playerViewReady → onBuffering` (descarga MPD + negociación) `→ onReady` (licencia OK, descifrado)
  `→ onPlay`. Un error entre buffering y ready ⇒ fallo de licencia.

### La clase `DrmData` (`MediastreamPlayerConfig.kt`)
```kotlin
class DrmData(
    var drmUrl: String,                    // URL del servidor de licencias Widevine
    var drmHeaders: Map<String, String>    // headers de autorización (típicamente un JWT)
)
// en MediastreamPlayerConfig:  var drmData: DrmData? = null   // null = sin DRM
```

### Consumo en el SDK (`MediastreamPlayer.kt`)
```kotlin
if (config.drmData != null) {
    mediaItem.setDrmConfiguration(
        Util.getDrmUuid("widevine")?.let {        // solo Widevine
            DrmConfiguration.Builder(it)
                .setLicenseUri(config.drmData?.drmUrl)
                .setLicenseRequestHeaders(config.drmData?.drmHeaders ?: emptyMap())
                .build()
        }
    )
}
```
`DrmSessionManagerProvider` se crea en `createMediaSourceFactory()` y se pasa al
`DefaultMediaSourceFactory`. Media3/ExoPlayer maneja la negociación completa.

### Config mínima correcta
```kotlin
MediastreamPlayerConfig().apply {
    id          = "CONTENT_ID"
    accountID   = "ACCOUNT_ID"
    type        = MediastreamPlayerConfig.VideoTypes.VOD     // o LIVE
    videoFormat = MediastreamPlayerConfig.AudioVideoFormat.DASH   // NO OMITIR
    environment = MediastreamPlayerConfig.Environment.PRODUCTION
    drmData = MediastreamPlayerConfig.DrmData(
        drmUrl     = "https://<tenant>.drm-widevine-licensing.axprod.net/AcquireLicense",
        drmHeaders = mapOf("X-AxDRM-Message" to "<JWT_VIGENTE>")   // header case-sensitive
    )
}
```

---

## 2. Criterios de aceptación

- **DRM-AC-001** — Con `drmData` seteado, `videoFormat = DASH` y device compatible, el contenido
  protegido obtiene licencia y reproduce: llegan `onReady` y `onPlay` sin error de licencia.
  *(En emulador solo aplica L3; L1 requiere hardware certificado — ver [[defects]] DRM-DEF-002.)*
- **DRM-AC-002** — El integrador configura `DrmData(drmUrl, drmHeaders)` y el SDK envía esos headers
  al servidor de licencias; con header correcto (`X-AxDRM-Message`) y token válido, la licencia se
  adquiere.
- **DRM-AC-003** — Un token JWT **vencido** o con **Key ID que no coincide** produce un error DRM
  propagado (`onError`/`onPlaybackErrors`, código tipo `4032` en Axinom), no una pantalla colgada
  silenciosa.

---

## 3. Proveedor Axinom (config de este QA app)

- Header único: `X-AxDRM-Message: <JWT firmado>` (case-sensitive).
- URL de licencia: `https://d231f6fd.drm-widevine-licensing.axprod.net/AcquireLicense`
  (`d231f6fd` = tenant ID).
- JWT relevante para QA: `expiration_date` (vencido ⇒ error `4032`) y `keys[].id` (debe coincidir
  con el Key ID del stream; mismatch ⇒ error de contenido/`4032`). `com_key_id` = Communication Key
  del tenant.

## 4. Dependencias clave (contexto de riesgo)
- `androidx.media3:media3-exoplayer` — negocia la licencia Widevine (acoplamiento alto).
- **Axinom DRM License Server** — dependencia externa; su disponibilidad/validez de token afecta
  directamente la reproducción.

---

## 5. Errores Widevine (debugging)

| Código | Causa | Acción |
|---|---|---|
| `ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED` | token vencido, URL incorrecta, header mal escrito | verificar JWT, URL, nombre exacto del header |
| `ERROR_CODE_DRM_CONTENT_ERROR` | Key ID del JWT no coincide con el stream | regenerar JWT con Key ID correcto |
| `ERROR_CODE_DRM_PROVISIONING_FAILED` | device no provisionado para Widevine | problema de dispositivo |
| `ERROR_CODE_DRM_DISALLOWED_OPERATION` | restricción de salida (HDCP) | verificar nivel Widevine del device |
| `ERROR_CODE_DRM_DEVICE_REVOKED` | device sin licencia Widevine válida | usar device físico certificado |
| `4032` (Axinom) | token vencido + key mismatch | verificar `expiration_date` y `keys[].id` |
| `ClassCastException: ContextImpl → Activity` | bug del SDK en Youbora al cambiar estado | no workaroundeable desde QA (ver [[risks]] DRM-RISK-005) |
| pantalla negra / `onError` inmediato | device solo L3 con stream L1, o contenido sin DASH | device físico para L1; verificar `.mpd` en plataforma |

### Niveles Widevine
- **L1** — descifrado en hardware seguro (TEE); requerido para HD protegido; solo devices físicos certificados.
- **L3** — descifrado en software; funciona en todos los Android 4.4+ incluyendo emuladores; algunos streams L1 fallan en L3.

### Logcat (con `isDebug = true`)
```bash
adb logcat -s ExoPlayerLib:W DrmSessionManager:D MediastreamPlayer:D *:S
```
La petición de licencia aparece como HTTP POST a la URL de Axinom.

---

## 6. Checklist de validación
- [ ] Content ID con DASH configurado en la plataforma (no solo HLS)
- [ ] `videoFormat = AudioVideoFormat.DASH`
- [ ] JWT con `expiration_date` futura
- [ ] `keys[].id` del JWT coincide con el Key ID del stream
- [ ] Header exacto `X-AxDRM-Message` (case-sensitive)
- [ ] Device físico para validar L1 (emulador = solo L3)
- [ ] `isDebug = true` para ver requests de licencia en logcat
