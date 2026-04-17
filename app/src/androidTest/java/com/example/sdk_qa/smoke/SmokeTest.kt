package com.example.sdk_qa.smoke

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.sdk_qa.R
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.CallbackCaptor
import com.example.sdk_qa.core.TestContent
import com.example.sdk_qa.scenarios.video.VideoLiveScenarioActivity
import com.example.sdk_qa.scenarios.video.VideoVodScenarioActivity
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.example.sdk_qa.utils.getCallbackCaptor
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests — CDN real Mediastream (PRODUCTION).
 *
 * Gate de release: estos 5 tests deben pasar antes de publicar cualquier versión.
 * Timeout: 20s por test (red real, latencia variable).
 *
 * NO correr en CI en cada commit — solo en release branches o manualmente.
 * Anotados con @LargeTest para filtrarlos: `adb shell am instrument -e size large`
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SmokeTest {

    private val TIMEOUT = 20_000L

    // -------------------------------------------------------------------------
    // [SMOKE-01] El container del player es visible al arrancar un VOD
    // -------------------------------------------------------------------------
    @Test
    fun vod_playerContainer_isVisible() {
        ActivityScenario.launch(VideoVodScenarioActivity::class.java).use { scenario ->
            onView(withId(R.id.player_container)).check(matches(isDisplayed()))
        }
    }

    // -------------------------------------------------------------------------
    // [SMOKE-02] VOD con ID real dispara onReady — SDK se conecta a la API
    // -------------------------------------------------------------------------
    @Test
    fun vod_onReady_fires() {
        ActivityScenario.launch(VideoVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [SMOKE-03] Live stream conecta y dispara onReady
    // -------------------------------------------------------------------------
    @Test
    fun live_onReady_fires() {
        ActivityScenario.launch(VideoLiveScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)
            scenario.assertNoErrorFired()
        }
    }

    // -------------------------------------------------------------------------
    // [SMOKE-04] URL inválida dispara onError — el SDK no falla silenciosamente
    // -------------------------------------------------------------------------
    @Test
    fun invalidUrl_onError_fires() {
        val scenario = ActivityScenario.launch(InvalidUrlScenarioActivity::class.java)
        val received = scenario.getCallbackCaptor().awaitEvent("onError", TIMEOUT)
        assertWithMessage("Se esperaba onError con URL inválida").that(received).isTrue()
        scenario.close()
    }

    // -------------------------------------------------------------------------
    // [SMOKE-05] onEnd dispara cuando el VOD termina (seek al final)
    // -------------------------------------------------------------------------
    @Test
    fun vod_seekToEnd_onEnd_fires() {
        ActivityScenario.launch(VideoVodScenarioActivity::class.java).use { scenario ->
            // Esperar que el player esté listo y tenga duración conocida
            scenario.awaitCallback("onReady", TIMEOUT)

            // Seek a 2s antes del final
            scenario.onActivity { activity ->
                val duration = activity.player?.msPlayer?.duration ?: 0L
                if (duration > 5_000L) {
                    activity.player?.msPlayer?.seekTo(duration - 2_000L)
                }
            }

            scenario.awaitCallback("onEnd", TIMEOUT)
        }
    }
}

