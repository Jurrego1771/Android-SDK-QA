# 09 — DRM (Digital Rights Management)

## Descripcion

Soporte para contenido protegido con DRM. El SDK usa Widevine (estandar Android) via ExoPlayer/Media3.

## Configuracion

```kotlin
val config = MediastreamPlayerConfig()
config.id = "PROTECTED_CONTENT_ID"
config.accountID = "ACCOUNT_ID"
config.drmData = MediastreamPlayerConfig.DrmData(
    licenseUrl = "https://license-server.example.com/widevine",
    headers = mapOf(
        "X-Auth-Token" to "license-auth-token",
        "Content-Type" to "application/octet-stream"
    )
)
```

## Como funciona

1. El player detecta que el stream requiere DRM (via `ConfigMain` o `drmData` manual)
2. ExoPlayer solicita una licencia al `licenseUrl` con los `headers` provistos
3. Si la licencia es valida, el contenido se desencripta y reproduce
4. Si la licencia expira durante la reproduccion, el player puede interrumpirse

## Requisitos de dispositivo

- **Widevine L1** — descifrado en hardware (requerido para contenido HD protegido)
- **Widevine L3** — descifrado en software (funciona en todos los dispositivos Android 4.4+)
- Verificable via `MediaDrm.isCryptoSchemeSupported(UUID)`

## Errores comunes de DRM

| Error | Causa probable | Solucion |
|-------|---------------|---------|
| `ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED` | URL de licencia incorrecta o token invalido | Verificar `licenseUrl` y `headers` |
| `ERROR_CODE_DRM_CONTENT_ERROR` | Stream no compatible con el esquema DRM | Verificar formato del stream |
| `ERROR_CODE_DRM_PROVISIONING_FAILED` | Dispositivo no provisionado | Problema de dispositivo |
| `ERROR_CODE_DRM_DISALLOWED_OPERATION` | Restriccion de salida (HDCP) | Verificar nivel Widevine del dispositivo |

## Testing — Escenarios a cubrir

- [ ] Contenido DRM reproduce correctamente con licencia valida
- [ ] Token de licencia invalido → `onError` callback con mensaje DRM
- [ ] URL de licencia incorrecta → `onError` callback
- [ ] Licencia expirada durante reproduccion → comportamiento correcto
- [ ] DRM en VOD funciona
- [ ] DRM en Live funciona
- [ ] DRM con headers adicionales de autenticacion

**Nota:** Los tests de DRM requieren dispositivo/emulador real con Widevine. No se pueden mockear completamente a nivel unitario.

---

*Feature: 09-drm | SDK v9.9.0 | 2026-04-16*
