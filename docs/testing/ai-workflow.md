# Workflow con IA para Generar Tests

## Principio fundamental

La IA genera el boilerplate y los escenarios. El humano valida, corrige y ejecuta.
**Ningun test generado por IA entra al repo sin revision y ejecucion local.**

---

## Flujo paso a paso

### Paso 1 — Documentar la feature primero
Antes de pedir tests, el archivo `docs/features/XX-nombre.md` debe estar completo con:
- Comportamiento esperado documentado
- Lista de escenarios de test (`## Testing — Escenarios a cubrir`)

### Paso 2 — Preparar el contexto para la IA
Para cada sesion de generacion de tests, dar a la IA:
```
1. El archivo docs/features/XX-nombre.md completo
2. El codigo fuente de la clase a testear
3. El fixture JSON relevante (si es integration test)
4. El archivo docs/testing/test-strategy.md como referencia de stack
```

### Paso 3 — Prompt recomendado
```
Dado este documento de feature: [pegar docs/features/XX.md]
Y este codigo fuente: [pegar codigo de la clase]
Y esta estrategia de testing: [pegar test-strategy.md]

Implementa los unit tests para los siguientes escenarios:
- [escenario 1]
- [escenario 2]

Usar: JUnit 4, MockK, Truth, runTest para coroutines.
NO usar datos inventados — usar los enums y valores reales del codigo fuente.
Cada test debe tener exactamente un assert sobre el comportamiento.
```

### Paso 4 — Review del codigo generado

Checklist de review:
- [ ] El test compila sin errores
- [ ] El test usa datos reales del SDK (no strings inventados)
- [ ] El assert es especifico al comportamiento (no solo `isNotNull`)
- [ ] No hay `Thread.sleep()` ni `delay()` sin `runTest`
- [ ] El test tiene un nombre descriptivo en backticks
- [ ] El tag `@SmallTest` / `@MediumTest` / `@LargeTest` es correcto
- [ ] El test falla si la implementacion es incorrecta (esto es clave)

### Paso 5 — Verificar que el test falla primero (TDD)
Si el test pasa sin haber implementado nada, es un false positive.

```kotlin
// Ejemplo de verificacion: comentar la logica real
// y verificar que el test falla correctamente
```

### Paso 6 — Ejecutar y confirmar

```bash
# Unit tests
./gradlew :app:test

# Integration tests con Robolectric
./gradlew :app:testDebugUnitTest

# E2E (requiere emulador)
./gradlew :app:connectedAndroidTest
```

### Paso 7 — Actualizar el tracker
Despues de confirmar que el test pasa:
1. Actualizar `risk-map/COVERAGE_TRACKER.md`
2. Si se completa una feature: actualizar `risk-map/RISK_MAP.md`

---

## Prompts utiles por tipo de test

### Para unit test de una clase de configuracion
```
Dame un unit test JUnit4 + MockK + Truth para [NombreClase].
La clase hace [descripcion del comportamiento].
Testea especificamente: [escenario concreto].
Usa datos reales del enum [EnumName] con valores [valor1, valor2].
```

### Para integration test con MockWebServer
```
Dame un integration test con MockWebServer para [ApiService.metodo].
El fixture JSON esta en fixtures/[path].json y contiene [descripcion].
Verifica que: [comportamiento esperado del modelo parseado].
Usa runTest para coroutines.
```

### Para generar escenarios de test de una feature
```
Dado este documento de feature [pegar feature doc],
identifica todos los casos borde y escenarios negativos que
NO estan en la lista de "Testing — Escenarios a cubrir".
Prioriza por riesgo de fallo en produccion.
```

---

## Anti-patrones a evitar

| Anti-patron | Por que evitar | Alternativa |
|-------------|---------------|-------------|
| Test que siempre pasa | False positive, no detecta regresiones | Verificar que falla antes de implementar |
| Mock de la clase que se esta testeando | No testea nada real | Usar instancia real, mockear solo dependencias |
| `Thread.sleep()` en tests asincronos | Race condition, flaky test | `runTest` + `advanceUntilIdle()` |
| Datos string inventados en fixtures | El test no refleja la API real | Capturar fixture real |
| Un test con multiples assertions de comportamiento diferente | Dificil diagnosticar cual falla | Un test por comportamiento |
| Tests sin nombre descriptivo | Dificil saber que falla | `fun \`given X when Y then Z\`()` |

---

*El objetivo es velocidad con confianza: la IA acelera el boilerplate, el humano garantiza la calidad.*
