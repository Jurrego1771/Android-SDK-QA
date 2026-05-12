package com.example.sdk_qa.integration

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.sdk_qa.R
import com.example.sdk_qa.scenarios.video.VideoAdsScenarioActivity
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertCallbackNotFired
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests — IMA Ads (preroll sobre VOD).
 *
 * Involucra dos hops de red: API Mediastream (para resolver el contenido) +
 * servidor IMA de Google (para el ad tag). Timeout de 30s.
 *
 * Contenido: TestContent.Video.VOD_WITH_ADS — VOD con preroll IMA configurado en plataforma.
 * Anotados @LargeTest: no se ejecutan en cada commit, solo en nightly/release.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AdsIntegrationTest {

    // detectLeaks=false: IMA AdTagLoader heap analysis (~45MB + ~70s CPU per test) OOM-kills
    // the process on BRAVIA TV after 4 sequential tests. IMA leak (Bug #1) is already known
    // and excluded from LeakCanary. StrictMode still runs via SdkTestRule.
    @get:Rule val sdkRule = SdkTestRule(detectLeaks = false)

    private val TIMEOUT = 30_000L

    // -------------------------------------------------------------------------
    // [INT-ADS-01] El container del player está visible al arrancar con ads
    // -------------------------------------------------------------------------
    @Test
    fun vodWithAds_playerContainer_isVisible() {
        ActivityScenario.launch(VideoAdsScenarioActivity::class.java).use {
            onView(withId(R.id.player_container)).check(matches(isDisplayed()))
        }
    }

    // -------------------------------------------------------------------------
    // [INT-ADS-02] onReady dispara con contenido que tiene ads configurados
    // -------------------------------------------------------------------------
    @Test
    fun vodWithAds_onReady_fires() {
        ActivityScenario.launch(VideoAdsScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [INT-ADS-03] El preroll dispara onAdEvents con tipo STARTED
    // -------------------------------------------------------------------------
    @Test
    fun vodWithAds_preroll_onAdStarted_fires() {
        ActivityScenario.launch(VideoAdsScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onAdEvents:STARTED", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [INT-ADS-04] El preroll completa — COMPLETED llega después de STARTED
    // -------------------------------------------------------------------------
    @Test
    fun vodWithAds_preroll_onAdCompleted_fires() {
        ActivityScenario.launch(VideoAdsScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onAdEvents:STARTED", TIMEOUT)
            scenario.awaitCallback("onAdEvents:COMPLETED", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [INT-ADS-05] Después del preroll el contenido continúa — onPlay dispara
    //
    // Garantiza que el SDK no queda bloqueado tras el ad: el contenido principal
    // debe iniciar reproducción sin intervención del usuario.
    // -------------------------------------------------------------------------
    @Test
    fun vodWithAds_afterPreroll_contentPlays() {
        ActivityScenario.launch(VideoAdsScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onAdEvents:COMPLETED", TIMEOUT)
            scenario.awaitCallback("onPlay", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [INT-ADS-06] Reload después de ads vuelve a disparar onReady
    //
    // Verifica que el SDK limpia correctamente el estado de IMA al recargar,
    // sin crashear ni quedar en estado inconsistente.
    // -------------------------------------------------------------------------
    @Test
    fun vodWithAds_reload_onReady_fires_again() {
        ActivityScenario.launch(VideoAdsScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.onActivity { activity ->
                activity.callbackCaptor.reset()
                activity.player?.reloadPlayer(activity.buildConfig())
            }
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [INT-ADS-07] onAdErrorEvent no bloquea la reproducción del contenido
    //
    // Si el ad falla (por cualquier razón), el SDK debe disparar onAdErrorEvent
    // y continuar con el contenido principal. Este test verifica que onPlay
    // llega incluso si onAdErrorEvent ocurre antes.
    //
    // Nota: solo aplica si el ad falla en el entorno de test. Si el ad se
    // reproduce exitosamente, el test igualmente pasa (onPlay llega igual).
    // -------------------------------------------------------------------------
    @Test
    fun vodWithAds_adError_doesNotBlockContent() {
        ActivityScenario.launch(VideoAdsScenarioActivity::class.java).use { scenario ->
            // onPlay debe llegar independientemente de si el ad falla o no
            scenario.awaitCallback("onPlay", TIMEOUT)
            // El contenido no debe disparar errores de reproducción
            scenario.assertCallbackNotFired("onError")
            scenario.assertCallbackNotFired("onPlaybackErrors")
        }
    }
}
