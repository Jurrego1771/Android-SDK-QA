package com.example.sdk_qa.utils

import android.os.StrictMode
import androidx.test.platform.app.InstrumentationRegistry
import leakcanary.DetectLeaksAfterTestSuccess
import leakcanary.LeakCanary
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import shark.AndroidReferenceMatchers
import shark.LibraryLeakReferenceMatcher
import shark.ReferencePattern.InstanceFieldPattern

/**
 * Regla JUnit reutilizable para todos los tests del SDK.
 *
 * Combina en una sola declaración:
 *   1. DetectLeaksAfterTestSuccess — falla si el SDK retiene objetos después del test
 *      (omitido cuando detectLeaks = false — usar para tests con IMA que consumen
 *      demasiada RAM en el análisis de heap en dispositivos TV con memoria limitada)
 *   2. StrictMode                  — detecta network/disk en main thread (log, no death)
 *   3. SdkEvidenceRule             — placeholder para evidencia adicional
 *
 * Uso:
 *   @get:Rule val sdkRule = SdkTestRule()
 *   @get:Rule val sdkRule = SdkTestRule(detectLeaks = false)  // para AdsIntegrationTest
 *
 * Para tests que también necesitan ActivityScenarioRule, encadenar con RuleChain:
 *   @get:Rule val rules = RuleChain
 *       .outerRule(SdkTestRule())
 *       .around(ActivityScenarioRule(MyActivity::class.java))
 */
class SdkTestRule(private val detectLeaks: Boolean = true) : TestRule {

    private val inner: RuleChain = if (detectLeaks) {
        RuleChain
            .outerRule(DetectLeaksAfterTestSuccess())
            .around(StrictModeRule())
            .around(SdkEvidenceRule())
    } else {
        RuleChain
            .outerRule(StrictModeRule())
            .around(SdkEvidenceRule())
    }

    override fun apply(base: Statement, description: Description): Statement {
        // Tell LeakCanary to treat known IMA SDK leaks as library leaks (not app failures).
        // AndroidDetectLeaksAssert reads LeakCanary.config.referenceMatchers at analysis time.
        LeakCanary.config = LeakCanary.config.copy(
            referenceMatchers = AndroidReferenceMatchers.appDefaults + IMA_LIBRARY_LEAKS
        )
        // Remove HPROF files from previous tests before each test runs.
        // Ads tests each trigger a ~45MB heap dump; 5+ dumps fill storage and crash the process.
        purgeStaleHprofs()
        return inner.apply(base, description)
    }

    private fun purgeStaleHprofs() {
        try {
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir
                .listFiles { f -> f.name.endsWith(".hprof") }
                ?.forEach { it.delete() }
        } catch (_: Exception) { /* best-effort */ }
    }
}

// ---------------------------------------------------------------------------
// IMA SDK known library leaks — excluded from application leak detection.
//
// IMA registers a global Application.ActivityLifecycleCallbacks (zzea) that
// retains the last Activity referencing MediastreamPlayer. This causes
// cross-test contamination: Activity from test N is still referenced by IMA
// when test N+1 triggers LeakCanary. Reported to Mediastream — we cannot
// unregister IMA's global callback from our side.
// ---------------------------------------------------------------------------
private val IMA_LIBRARY_LEAKS = listOf(
    LibraryLeakReferenceMatcher(
        pattern = InstanceFieldPattern(
            className = "com.google.ads.interactivemedia.v3.internal.zzea",
            fieldName = "zzb"
        ),
        description = "IMA SDK (Google) retains Activities via a global " +
            "Application.ActivityLifecycleCallbacks registered by zzea. " +
            "Cannot be fixed from app code — SDK-level bug."
    ),
    LibraryLeakReferenceMatcher(
        pattern = InstanceFieldPattern(
            className = "androidx.media3.exoplayer.ima.AdTagLoader\$\$ExternalSyntheticLambda1",
            fieldName = "f\$0"
        ),
        description = "Media3 IMA schedules a delayed Runnable on the main thread that captures " +
            "AdTagLoader as f\$0. AdTagLoader retains Activity-bound objects (configuration, " +
            "adDisplayContainer, …) until the message fires ~2-5s after Activity#onDestroy. " +
            "SDK bug #1 — AdTagLoader is a Media3/IMA internal; cannot fix from app side."
    )
)

// ---------------------------------------------------------------------------
// StrictMode como TestRule
// ---------------------------------------------------------------------------

/**
 * Activa StrictMode durante el test y lo restaura al terminar.
 *
 * Network: penaltyLog (el SDK hace llamadas de red — es esperado).
 * Disk I/O: penaltyLog (loggea sin matar el test).
 * VM policy: penaltyLog (loggea leaked closeables sin matar).
 *
 * Todos los logcat de StrictMode aparecen bajo TAG "StrictMode".
 */
private class StrictModeRule : TestRule {

    override fun apply(base: Statement, description: Description) = object : Statement() {

        override fun evaluate() {
            val previousThreadPolicy = StrictMode.getThreadPolicy()
            val previousVmPolicy = StrictMode.getVmPolicy()

            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build()
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build()
            )

            try {
                base.evaluate()
            } finally {
                StrictMode.setThreadPolicy(previousThreadPolicy)
                StrictMode.setVmPolicy(previousVmPolicy)
            }
        }
    }
}
