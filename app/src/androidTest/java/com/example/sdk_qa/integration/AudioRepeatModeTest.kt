package com.example.sdk_qa.integration

import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.scenarios.audio.AudioVodScenarioActivity
import com.example.sdk_qa.utils.SdkTestRule
import com.example.sdk_qa.utils.assertNoErrorFired
import com.example.sdk_qa.utils.awaitCallback
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audio repeat mode — cubre el fix del CHANGELOG 10.0.8:
 *   "Repeat mode logic now correctly accounts for audio media type to avoid unintended looping".
 *
 * Comportamiento esperado (del código rama 10.0.8): para AUDIO el SDK usa repeatMode=OFF y llega a
 * STATE_ENDED → onEnd; NO entra en loop indebido. NO duplica AudioIntegrationTest (que cubre
 * onReady/onPlay/seek), ni VodEndOfPlaybackTest (que es VIDEO). Foco: el final de un audio VOD.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
class AudioRepeatModeTest {

    @get:Rule
    val sdkRule = SdkTestRule()

    private val TIMEOUT = 25_000L

    // [AUDIO-END-01] Audio VOD al final dispara onEnd y NO entra en loop indebido (se detiene).
    @Test
    fun audioVod_end_firesOnEnd_noUnintendedLoop() {
        ActivityScenario.launch(AudioVodScenarioActivity::class.java).use { scenario ->
            scenario.awaitCallback("onReady", TIMEOUT)

            var duration = 0L
            scenario.onActivity { duration = (it as BaseScenarioActivity).player?.msPlayer?.duration ?: 0L }
            assertWithMessage("Audio VOD debe tener duración conocida").that(duration).isGreaterThan(5_000L)

            scenario.onActivity { (it as BaseScenarioActivity).player?.msPlayer?.seekTo(duration - 2_000L) }
            scenario.awaitCallback("onEnd", TIMEOUT)

            // Tras onEnd, el audio NO debe seguir reproduciendo (no hay loop nativo para audio).
            Thread.sleep(3_000L)
            var playing = false
            scenario.onActivity { playing = (it as BaseScenarioActivity).player?.msPlayer?.isPlaying ?: false }
            assertWithMessage("Audio VOD no debe entrar en loop indebido tras onEnd (isPlaying)")
                .that(playing).isFalse()
            scenario.assertNoErrorFired()
        }
    }
}
