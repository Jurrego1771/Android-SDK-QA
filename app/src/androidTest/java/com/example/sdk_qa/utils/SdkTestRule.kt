package com.example.sdk_qa.utils

import android.os.StrictMode
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

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

    override fun apply(base: Statement, description: Description): Statement =
        inner.apply(base, description)
}

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

