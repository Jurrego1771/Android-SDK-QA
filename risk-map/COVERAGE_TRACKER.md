# Coverage Tracker — Por Feature

**Ultima actualizacion:** 2026-04-16
**SDK Version bajo test:** 11.0.0-alpha.01
**Tipo de tests:** Instrumented (E2E / caja negra) — el SDK es una dependencia externa

> Unit tests de logica interna del SDK → repo `MediastreamPlatformSDKAndroid`

---

## Como actualizar este documento

Cuando se implementa un test:
1. Cambiar el `Estado` de la feature
2. Incrementar el contador de tests
3. Agregar el nombre del archivo de test en `Archivo de test`
4. Actualizar el porcentaje estimado de cobertura
5. Guardar snapshot en `history/YYYY-MM-DD.md`

---

## Leyenda de estado

| Estado | Significado |
|--------|-------------|
| ⬜ PENDIENTE | Sin tests |
| 🟡 PARCIAL | Algunos escenarios cubiertos |
| ✅ COMPLETO | Todos los escenarios del checklist cubiertos |
| ⚠️ BLOQUEADO | No se puede testear (requiere hardware/servicio externo) |

---

## Tracking por feature

### 00 — SDK Overview / Arquitectura
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | N/A | — | — |

---

### 01 — Inicializacion
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 8
**Escenarios cubiertos:** 0

---

### 02 — PlayerConfig
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⚠️ SDK REPO | — | Pertenece a MediastreamPlatformSDKAndroid |
| Comportamiento black-box | ⬜ PENDIENTE | 0 | Verificar efecto de la config en el player real |
| E2E | N/A | — | — |

**Escenarios pendientes:** Los de config.pip, config.dvr, config.autoplay verificados por comportamiento observable
**Escenarios cubiertos:** 0

---

### 03 — Callbacks
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 17
**Escenarios cubiertos:** 0

---

### 04 — Video VOD
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | N/A | — | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 10
**Escenarios cubiertos:** 0

---

### 05 — Video Live
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | N/A | — | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 8
**Escenarios cubiertos:** 0

---

### 06 — Episodes / Next Episode
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 8
**Escenarios cubiertos:** 0

---

### 07 — Audio
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | N/A | — | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 8
**Escenarios cubiertos:** 0

---

### 08 — DVR
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 10
**Escenarios cubiertos:** 0

---

### 09 — DRM
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⚠️ BLOQUEADO | 0 | Requiere dispositivo con Widevine |

**Escenarios pendientes:** 7
**Escenarios cubiertos:** 0

---

### 10 — Ads IMA
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 10
**Escenarios cubiertos:** 0

---

### 11 — Analytics Comscore
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | N/A | — | — |

**Escenarios pendientes:** 5
**Escenarios cubiertos:** 0

---

### 12 — Analytics Youbora
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | N/A | — | — |

**Escenarios pendientes:** 4
**Escenarios cubiertos:** 0

---

### 13 — Chromecast
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⚠️ BLOQUEADO | 0 | Requiere Chromecast fisico |

**Escenarios pendientes:** 6
**Escenarios cubiertos:** 0

---

### 14 — Picture-in-Picture
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | N/A | — | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 8
**Escenarios cubiertos:** 0

---

### 15 — Android TV / Fire TV
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⚠️ BLOQUEADO | 0 | Requiere emulador/dispositivo TV |

**Escenarios pendientes:** 6
**Escenarios cubiertos:** 0

---

### 16 — Android Auto
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⚠️ BLOQUEADO | 0 | Requiere Desktop Head Unit |

**Escenarios pendientes:** 5
**Escenarios cubiertos:** 0

---

### 17 — Subtitulos
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 8
**Escenarios cubiertos:** 0

---

### 18 — Descarga Offline
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | N/A | — | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 5
**Escenarios cubiertos:** 0

---

### 19 — Notificaciones
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | N/A | — | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 7
**Escenarios cubiertos:** 0

---

### 20 — UI Customization
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | N/A | — | — |
| Integration | N/A | — | — |
| E2E | ⬜ PENDIENTE | 0 | — |

**Escenarios pendientes:** 8
**Escenarios cubiertos:** 0

---

### 21 — Services (PlayerService + PlayerServiceWithSync)
| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⚠️ BLOQUEADO | 0 | Android Auto requiere Desktop Head Unit |

**Escenarios pendientes:** 13
**Escenarios cubiertos:** 0

---

### 06b — Next Episode UI (overlay, timing, modos)
> Sub-feature de 06 con complejidad propia

| Tipo | Estado | Tests | Archivo |
|------|--------|-------|---------|
| Unit | ⬜ PENDIENTE | 0 | — (timing logic, state machine) |
| Integration | ⬜ PENDIENTE | 0 | — |
| E2E | ⬜ PENDIENTE | 0 | — (overlay visible, botones) |

**Escenarios pendientes:** 15
**Escenarios cubiertos:** 0

---

## Resumen global

| Metrica | Valor |
|---------|-------|
| Features totales | 23 (21 originales + Services + Next Episode UI) |
| Features con cobertura completa | 0 |
| Features con cobertura parcial | 0 |
| Features sin cobertura | 23 |
| Features bloqueadas por hardware | 5 |
| Escenarios totales identificados | ~190 |
| Escenarios cubiertos | 0 |
| **Cobertura global estimada** | **0%** |

---

## Orden de implementacion recomendado (por riesgo)

1. `02-player-config` — Unit tests puros, sin dependencias, rapidos de implementar
2. `01-initialization` — Integration con MockWebServer
3. `03-callbacks` — Integration + orden correcto
4. `04-video-vod` — E2E basico
5. `10-ads-ima` — Manejo de errores critico
6. `08-dvr` — Logica mas compleja
7. `07-audio` — Background playback
8. `05-video-live` — Con reconexion
9. `06-episodes` — Next episode flow
10. El resto segun prioridad del negocio

---

*Actualizar este documento en cada PR que agregue tests*
