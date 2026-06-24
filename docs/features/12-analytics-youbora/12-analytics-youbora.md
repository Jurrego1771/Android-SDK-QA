# 12 — Analytics: Youbora / NPAW

## Descripcion

Integracion con Youbora (NPAW) para analytics de calidad de experiencia (QoE). Mide buffering, bitrate, errores, y calidad percibida del stream.

## Como funciona

Similar a Comscore, la configuracion viene de la API via `ConfigMain.youbora`. El SDK usa el adapter `media3-npaw` (v6.8.7) para conectar ExoPlayer con el plugin de Youbora.

## Datos trackeados

- Tiempo de inicio (join time)
- Rebuffering (frecuencia y duracion)
- Bitrate actual y promedio
- Errores de reproduccion
- Metadatos del contenido (titulo, duracion, tipo)
- Datos de la app (nombre, version)

## Configuracion desde API (YouboraCustoms en ConfigMain)

El objeto `youbora` puede contener custom parameters especificos del cliente configurados en la plataforma Mediastream.

## Testing — Escenarios a cubrir

- [ ] Youbora adapter se inicializa cuando la API devuelve datos de Youbora
- [ ] NO se inicializa cuando la API no devuelve datos
- [ ] Metadatos del contenido se pasan correctamente al plugin
- [ ] `appName` y `appVersion` de `MediastreamPlayerConfig` se reportan a Youbora

**Nota:** Mockear el plugin de Youbora en tests unitarios, verificar llamadas con parametros correctos.

---

*Feature: 12-analytics-youbora | SDK v9.9.0 | 2026-04-16*
