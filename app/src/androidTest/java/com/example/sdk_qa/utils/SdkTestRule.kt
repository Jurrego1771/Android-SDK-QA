package com.example.sdk_qa.utils

import android.os.StrictMode
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
 *   2. StrictMode                  — detecta network/disk en main thread (log, no death)
 *   3. SdkEvidenceRule             — placeholder para evidencia adicional
 *
 * Uso:
 *   @get:Rule val sdkRule = SdkTestRule()
 *
 * Para tests que también necesitan ActivityScenarioRule, encadenar con RuleChain:
 *   @get:Rule val rules = RuleChain
 *       .outerRule(SdkTestRule())
 *       .around(ActivityScenarioRule(MyActivity::class.java))
 */
class SdkTestRule : TestRule {

    private val inner: RuleChain = RuleChain
        .outerRule(DetectLeaksAfterTestSuccess())
        .around(StrictModeRule())
        .around(SdkEvidenceRule())

    override fun apply(base: Statement, description: Description): Statement {
        // Tell LeakCanary to treat known IMA SDK leaks as library leaks (not app failures).
        // AndroidDetectLeaksAssert reads LeakCanary.config.referenceMatchers at analysis time.
        LeakCanary.config = LeakCanary.config.copy(
            referenceMatchers = AndroidReferenceMatchers.appDefaults + IMA_LIBRARY_LEAKS
        )
        return inner.apply(base, description)
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
