# Estrategia de Testing

## Contexto fundamental

El SDK se consume como **dependencia publicada en Maven Central**, no desde codigo fuente local:

```kotlin
implementation("io.github.mediastream:mediastreamplatformsdkandroid:11.0.0-alpha.01")
```

Esto define el alcance completo de las pruebas:

> **El SDK es una caja negra.** Solo podemos interactuar con su API publica.
> Los tests de logica interna del SDK (unit tests de `MediastreamPlayerConfig`, `ConfigMain`, `Util`, etc.) corresponden al **repo del SDK** — no a este proyecto.

---

## Que testeamos aqui

| Tipo | Que verificamos | Donde |
|------|----------------|-------|
| **E2E / Instrumented** | Comportamiento observable del player: UI, callbacks, flujos completos | `src/androidTest/` |
| **Manual** | Escenarios complejos, dispositivos reales, edge cases visuales | App module directamente |

**Lo que NO va aqui:**
- Unit tests de clases internas del SDK (`MediastreamPlayerConfig`, `ConfigMain`, `ApiService`, analytics managers, etc.) → esos van en el repo `MediastreamPlatformSDKAndroid`

---

## Stack tecnologico

| Herramienta | Version | Rol |
|-------------|---------|-----|
| AndroidJUnitRunner | — | Runner de tests instrumentados |
| Espresso | 3.5.1 | Interacciones de UI (click, scroll, assert vistas) |
| Espresso Intents | 3.5.1 | Verificar Intents lanzados (fullscreen Activity) |
| UI Automator | 2.3.0 | Interacciones de sistema: notificaciones, PiP, home button |
| MockK Android | 1.13.10 | Mockear callbacks del SDK para verificar que se disparan |
| Truth | 1.4.2 | Assertions legibles |
| kotlinx-coroutines-test | 1.7.3 | Tests asincronos con coroutines |

---

## Tipos de tests en este proyecto

### 1. Tests de callbacks (instrumented)

Verificar que el SDK dispara los callbacks correctos en el orden correcto.

```kotlin
@LargeTest
@RunWith(AndroidJUnit4::class)
class VodCallbacksTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(VideoOnDemandActivity::class.java)

    @Test
    fun `onReady fires before onPlay when autoplay is true`() {
        val callbackOrder = mutableListOf<String>()
        val latch = CountDownLatch(2)

        // El callback se registra desde el test via la Activity
        activityRule.scenario.onActivity { activity ->
            activity.player.addPlayerCallback(object : MediastreamPlayerCallback {
                override fun onReady() {
                    callbackOrder.add("onReady")
                    latch.countDown()
                }
                override fun onPlay() {
                    callbackOrder.add("onPlay")
                    latch.countDown()
                }
            })
        }

        latch.await(10, TimeUnit.SECONDS)

        assertThat(callbackOrder).containsExactly("onReady", "onPlay").inOrder()
    }
}
```

### 2. Tests de UI con Espresso

Verificar que la UI del player responde correctamente.

```kotlin
@Test
fun `fullscreen button toggles fullscreen mode`() {
    // Verificar que el boton de fullscreen existe y es clickeable
    onView(withId(R.id.exo_fullscreen))
        .check(matches(isDisplayed()))
        .perform(click())

    // Verificar que onFullscreen callback fue llamado
    verify { mockCallback.onFullscreen(any()) }
}
```

### 3. Tests de sistema con UI Automator

Para interacciones que Espresso no puede manejar: notificaciones, PiP, home button.

```kotlin
@Test
fun `player continues in PiP after pressing home`() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    // Iniciar reproduccion en la Activity
    // ...

    // Presionar home
    device.pressHome()

    // Verificar que la ventana PiP aparece
    val pipWindow = device.findObject(UiSelector().packageName("com.example.sdk_qa"))
    assertThat(pipWindow.exists()).isTrue()
}
```

### 4. Tests de configuracion (black-box)

Verificar que las opciones de config tienen el efecto esperado en el comportamiento observable.

```kotlin
@Test
fun `pip DISABLE prevents entering PiP`() {
    // Config con PiP deshabilitado
    // Llamar startPiP()
    // Verificar que la ventana PiP NO aparece (UI Automator)
}
```

---

## Como disenar los tests sin acceso al codigo interno

Como no podemos inyectar mocks en el Retrofit del SDK, las opciones son:

### Opcion A — Contenido real de prueba (preferida)

Usar IDs de contenido estables del entorno DEV de Mediastream:
- Un VOD de prueba corto (30 segundos) para tests de ciclo completo
- Un Live de prueba siempre activo
- Un episodio con siguiente episodio configurado

```kotlin
object TestContent {
    const val VOD_SHORT = "TEST_VOD_ID"         // VOD de 30 segundos en DEV
    const val LIVE_STABLE = "TEST_LIVE_ID"       // Live siempre activo en DEV
    const val EPISODE_WITH_NEXT = "TEST_EP_ID"   // Episodio con siguiente
    const val ACCOUNT_ID = "TEST_ACCOUNT_ID"
    val ENV = MediastreamPlayerConfig.Environment.DEV
}
```

### Opcion B — src directo (para tests de reproduccion pura)

Usar una URL publica de HLS/MP4 directa que no requiere la API:

```kotlin
config.src = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" // stream publico de prueba
// No llama a la API de Mediastream — testea solo el motor de reproduccion
```

### Opcion C — Proxy de red

Herramientas como Charles Proxy o mitmproxy para interceptar y modificar respuestas de la API en tests exploratorios. No automatizable facilmente.

---

## Estructura de carpetas de tests

```
app/src/
├── main/
│   └── java/com/example/sdk_qa/
│       ├── scenarios/          <- Activities para cada escenario de test
│       │   ├── VodScenarioActivity.kt
│       │   ├── LiveScenarioActivity.kt
│       │   ├── EpisodeScenarioActivity.kt
│       │   └── ...
│       └── utils/
│           └── TestContent.kt  <- IDs y constantes de contenido de prueba
└── androidTest/
    └── java/com/example/sdk_qa/
        ├── callbacks/
        │   ├── VodCallbacksTest.kt
        │   ├── LiveCallbacksTest.kt
        │   └── EpisodeCallbacksTest.kt
        ├── ui/
        │   ├── FullscreenTest.kt
        │   ├── ControlsTest.kt
        │   └── NextEpisodeOverlayTest.kt
        ├── system/
        │   ├── PipTest.kt
        │   ├── NotificationsTest.kt
        │   └── BackgroundPlaybackTest.kt
        ├── config/
        │   └── ConfigBehaviorTest.kt   <- black-box de opciones de config
        └── base/
            └── BasePlayerTest.kt       <- setup/teardown comun
```

---

## App module — manual testing

El `app` module es la **app de pruebas manual**. Cada `Activity` representa un escenario:

```
app/src/main/java/com/example/sdk_qa/scenarios/
├── VodScenarioActivity        <- VOD basico
├── LiveScenarioActivity       <- Live sin DVR
├── LiveDvrScenarioActivity    <- Live con DVR
├── EpisodeScenarioActivity    <- Episodio con next episode
├── AudioLiveScenarioActivity  <- Radio en vivo
├── AudioPodcastActivity       <- Podcast
├── AdsScenarioActivity        <- Con preroll IMA
├── DrmScenarioActivity        <- Con DRM
├── PipScenarioActivity        <- PiP
└── DebugActivity              <- Panel de debug (callbacks, estado)
```

Cada Activity muestra:
- El player
- Un log en tiempo real de todos los callbacks recibidos
- El estado actual del player (posicion, duracion, buffer)
- Botones para simular acciones (pausa, seek, siguiente)

---

## Reglas para evitar false positives

1. **Usar `CountDownLatch` o `IdlingResource` para esperar callbacks asincronos** — nunca `Thread.sleep()`
2. **El test debe fallar sin el SDK correcto** — si pasa siempre, es un false positive
3. **Tests de callbacks: verificar el orden, no solo que se dispararon**
4. **Tests de UI: verificar el estado resultante, no solo que el click ocurrio**
5. **Usar contenido DEV estable** — si el contenido de prueba cambia, el test falla por eso, no por un bug

---

## Que va en el repo del SDK (no aqui)

- Unit tests de `MediastreamPlayerConfig` (copy, merge, enums)
- Unit tests de `ConfigMain` deserialization
- Unit tests de `ComscoreAnalyticsManager`
- Unit tests de `Util.kt`, `Logger.kt`
- Integration tests de `ApiService` con MockWebServer
- Tests del `CustomAssSubtitleParser`
- Tests de logica de DVR offset

---

*SDK version bajo test: 11.0.0-alpha.01 | Estrategia actualizada: 2026-04-16*
