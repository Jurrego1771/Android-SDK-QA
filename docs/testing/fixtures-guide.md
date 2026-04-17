# Guia de Fixtures

Los fixtures son respuestas JSON reales capturadas de la API de Mediastream. Son la base de los integration tests y garantizan que los tests reflejan comportamiento real.

## Estructura de carpetas

```
fixtures/
├── README.md
├── vod/
│   ├── vod-basic.json            <- VOD simple sin ads ni DRM
│   ├── vod-with-preroll.json     <- VOD con ads preroll (adURL presente)
│   ├── vod-with-drm.json         <- VOD con DRM Widevine
│   └── vod-with-subtitles.json   <- VOD con multiples tracks de subtitulo
├── live/
│   ├── live-basic.json           <- Live sin DVR
│   └── live-with-dvr.json        <- Live con DVR habilitado
├── episode/
│   ├── episode-with-next.json    <- Episodio con siguiente episodio
│   └── episode-last.json         <- Ultimo episodio (sin siguiente)
├── audio/
│   ├── audio-live.json           <- Radio live (Icecast/HLS)
│   └── audio-podcast.json        <- Podcast VOD
└── errors/
    ├── 400-bad-request.json
    ├── 401-unauthorized.json
    ├── 404-not-found.json
    └── 500-server-error.json
```

## Reglas de fixtures

1. **Son respuestas reales**: capturadas del endpoint real de la API
2. **Sin credenciales**: tokens de acceso reemplazados por `"REDACTED"` o removidos
3. **Inmutables**: no editar un fixture existente; crear uno nuevo si se necesita variacion
4. **Nombrado descriptivo**: el nombre indica exactamente que escenario representa

## Como capturar un fixture

```bash
# Desde terminal (requiere credenciales validas del equipo)
curl -s "https://api.mdstrm.com/player/v4/account/{ACCOUNT_ID}/{TYPE}/{CONTENT_ID}/config" \
  -H "Accept: application/json" \
  | jq 'del(.mdstrm.accessToken)' \
  > fixtures/vod/vod-basic.json
```

## Como usar un fixture en un test

```kotlin
// Helper function en BaseTest
fun readFixture(path: String): String {
    return javaClass.classLoader
        ?.getResourceAsStream("fixtures/$path")
        ?.bufferedReader()
        ?.readText()
        ?: error("Fixture not found: fixtures/$path")
}

// Uso en test
val json = readFixture("vod/vod-basic.json")
mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
```

## Fixtures de error

Los errores HTTP deben representar respuestas reales del servidor, no strings inventados:

```json
// fixtures/errors/404-not-found.json
{
  "error": "Content not found",
  "code": 404,
  "message": "The requested content ID does not exist"
}
```

---

*Los fixtures son el contrato entre los tests y la API real.*
