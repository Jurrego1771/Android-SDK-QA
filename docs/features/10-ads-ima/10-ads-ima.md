# 10 — Publicidad: Google IMA / SSAI / DAI

## Descripcion

El SDK integra Google IMA (Interactive Media Ads) para reproduccion de anuncios. Soporta tres modalidades:

| Modalidad | Descripcion |
|-----------|-------------|
| **Client-side IMA** | El player descarga y reproduce anuncios via VAST/VMAP |
| **SSAI** (Server-Side Ad Insertion) | Los anuncios estan cosidos en el stream por el servidor |
| **DAI** (Dynamic Ad Insertion) | Version de Google de SSAI con tracking propio |

## Configuracion — Client-side IMA

```kotlin
val config = MediastreamPlayerConfig()
config.id = "CONTENT_ID"
config.accountID = "ACCOUNT_ID"
config.adURL = "https://pubads.g.doubleclick.net/gampad/ads?..."  // tag VAST/VMAP
config.googleImaPpid = "PPID_OPCIONAL"
config.muteAds = MediastreamPlayerConfig.FlagStatus.DISABLE  // no silenciar ads
```

## Configuracion — Atributos personalizados de anuncios

```kotlin
config.addAdCustomAttribute("custom_param", "valor")
config.addAdCustomAttribute("section", "noticias")
// Se inyectan en el ad tag URL
```

## Configuracion — SSAI / DAI

La configuracion de DAI viene desde la API (campo `adInsertionGoogle` en `ConfigMain`). No requiere configuracion manual del cliente, sino que la plataforma Mediastream gestiona la integracion.

## Callbacks de ads

```kotlin
override fun onAdEvents(type: AdEvent.AdEventType) {
    when (type) {
        AdEvent.AdEventType.STARTED -> showAdIndicator()
        AdEvent.AdEventType.COMPLETED -> hideAdIndicator()
        AdEvent.AdEventType.SKIPPED -> hideAdIndicator()
        AdEvent.AdEventType.CLICKED -> trackAdClick()
        else -> { /* log otros eventos */ }
    }
}

override fun onAdErrorEvent(error: AdError) {
    // El contenido principal debe continuar reproduciendo tras error de ad
    Log.e("Ads", "Error de anuncio: ${error.errorCode} - ${error.message}")
}
```

## Comportamiento importante

- Si el ad tag falla (`onAdErrorEvent`), el contenido principal **debe** continuar reproduciendo
- Durante la reproduccion de un anuncio, el seek esta deshabilitado
- Los anuncios pueden ser: preroll (antes), midroll (durante), postroll (despues)
- `prerollPlayerForSsai` es el player secundario usado para prerolls en SSAI

## Testing — Escenarios a cubrir

- [ ] Preroll se reproduce antes del contenido principal
- [ ] `onAdEvents(STARTED)` se dispara al iniciar el ad
- [ ] `onAdEvents(COMPLETED)` se dispara al terminar el ad
- [ ] Contenido principal inicia tras `COMPLETED`
- [ ] Ad tag invalido → `onAdErrorEvent` → contenido principal continua
- [ ] Ad tag con VMAP (multiples ads) reproduce todos en orden
- [ ] Anuncio skippeable: boton de skip aparece en el tiempo correcto
- [ ] `onAdEvents(SKIPPED)` se dispara al skipear
- [ ] `muteAds=ENABLE` silencia el audio del anuncio
- [ ] Atributos custom aparecen en la URL del ad tag

---

*Feature: 10-ads-ima | SDK v9.9.0 | 2026-04-16*
