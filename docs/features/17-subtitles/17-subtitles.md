# 17 — Subtitulos (ASS/SSA, WebVTT, CEA-608)

## Descripcion

El SDK soporta multiples formatos de subtitulos. Implementa parsers personalizados para el formato ASS/SSA (usado en anime y contenido latinoamericano).

## Formatos soportados

| Formato | Soporte | Notas |
|---------|---------|-------|
| WebVTT | Nativo ExoPlayer | Formato mas comun en HLS |
| SRT | Nativo ExoPlayer | Subtitulos externos |
| ASS/SSA | **Custom parser del SDK** | `CustomAssSubtitleParser` |
| CEA-608/708 | Nativo ExoPlayer | Subtitulos embebidos en el stream |

## Implementacion custom ASS/SSA

El SDK incluye:
- `CustomSubtitleParserFactory` — factory que registra el parser custom
- `CustomAssSubtitleParser` — parser de archivos ASS/SSA

Esto se inyecta automaticamente en `CustomMediaSourceFactory` durante la inicializacion del player.

## Seleccion de track de subtitulo

El usuario puede seleccionar subtitulos desde el dialogo de tracks:
- Mobile: `TrackSelectionDialog`
- TV: `TVSubtitleAudioDialog`

## Configuracion desde API

Los subtitulos disponibles vienen en `ConfigMain.subtitle` como lista de tracks con URL y nombre.

## Testing — Escenarios a cubrir

- [ ] Subtitulos WebVTT se muestran correctamente
- [ ] Subtitulos ASS/SSA se parsean y muestran correctamente
- [ ] Seleccion de track de subtitulo desde el dialogo
- [ ] Cambio de track en mitad de la reproduccion
- [ ] Deshabilitar subtitulos (seleccionar "ninguno")
- [ ] Subtitulos en multiples idiomas disponibles
- [ ] `CustomAssSubtitleParser` parsea correctamente timing y estilos basicos de ASS
- [ ] Content sin subtitulos: el dialogo no muestra opcion de subtitulos (o muestra "ninguno")

---

*Feature: 17-subtitles | SDK v9.9.0 | 2026-04-16*
