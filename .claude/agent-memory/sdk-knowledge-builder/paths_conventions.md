---
name: paths-conventions
description: Rutas clave de SDK/plantilla/salida y convención de IDs para construir qa-knowledge por módulo
metadata:
  type: reference
---

## Versión canónica
- **Versión canónica para QA = 10.0.8-alpha01** (binario Maven en `SDK-Android-Qa/app/build.gradle.kts:76`). El repo de código fuente local está en 10.0.7 (API equivalente). NO existe "v11" — memoria `sdk_v11_real_api` deprecada/errónea. Usar 10.0.8-alpha01 como label en KB de cara a QA; leer fuente 10.0.7 para firmas.

## Rutas
- SDK fuente: `D:\repos\mediastream\MediastreamPlatformSDKAndroid` — código fuente local en versión 10.0.7 (equivalente al binario QA 10.0.8-alpha01), namespace `am.mediastre.mediastreamplatformsdkandroid`, minSdk 24, compileSdk 35, Java/Kotlin 17.
- Core Player source dir: `mediastreamplatformsdkandroid/src/main/java/am/mediastre/mediastreamplatformsdkandroid/`
- Mapa de módulos del SDK: `D:\repos\mediastream\MediastreamPlatformSDKAndroid\docs\SDK_MODULES.md`
- Plantilla i18n (ESTRUCTURA): `D:\repos\jurrego1771\lightning-player-qa\qa-knowledge\modules\i18n\` (9 archivos). NOTA: está en `lightning-player-qa`, NO en SDK-Android-Qa.
- Salida del knowledge: `D:\repos\jurrego1771\SDK-Android-Qa\qa-knowledge\{module}\` (los archivos van directos en la carpeta del módulo, sin subcarpeta `modules/`).
- Tests QA instrumentados: `D:\repos\jurrego1771\SDK-Android-Qa\app\src\androidTest\java\com\example\sdk_qa\` (integration/, regression/, utils/).

## Convención de IDs (derivada de i18n, mayúsculas del slug en upper)
- Módulo i18n usa: `I18N-AC-00x`, `US-I18N-00x`, `GAP-I18N-00x`, `I18N-DEF-00x`, `I18N-RISK-00x`, `I18N-LEARN-00x`.
- OJO inconsistencia de la plantilla: user-stories usan prefijo `US-{MODULE}-` (US primero), pero AC/DEF/RISK/LEARN usan `{MODULE}-AC-` ({MODULE} primero). Replicar tal cual.
- Para core-player se usó: `CORE-AC-`, `US-CORE-`, `GAP-CORE-`, `CORE-DEF-`, `CORE-RISK-`, `CORE-LEARN-`, `CORE-TC-` (tests reales).
- Cross-link: user-stories.acceptance → AC ids; tests.ac_ref → AC ids; tests/gaps.defect_ref/risk_ref → DEF/RISK ids.

## Contenido en español, claves YAML en inglés
Narrativa y descripciones en español; keys YAML e IDs en inglés, igual que la plantilla i18n.
