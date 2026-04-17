package com.example.sdk_qa.utils

import android.graphics.Bitmap
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.FileOutputStream

/**
 * JUnit TestWatcher que captura un screenshot automáticamente cuando un test falla.
 *
 * Dónde quedan los archivos en el device:
 *   /sdcard/Android/data/com.example.sdk_qa/files/sdk_qa_evidence/screenshots/
 *
 * El script run-tests.sh los descarga con adb pull y los incluye en el HTML report.
 *
 * Uso: ya está integrado en SdkTestRule — no necesita declararse manualmente.
 */
class SdkEvidenceRule : TestWatcher() {

    companion object {
        private const val TAG = "SDK_QA_EVIDENCE"
        // Tag especial que run-tests.sh busca en logcat para saber el path del screenshot
        const val SCREENSHOT_LOG_PREFIX = "SDK_QA_SCREENSHOT_PATH:"
    }

    override fun failed(e: Throwable, description: Description) {
        captureScreenshot(description)
    }

    private fun captureScreenshot(description: Description) {
        try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()

            // UiAutomation.takeScreenshot() — disponible desde API 18, no requiere permisos
            val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: run {
                Log.w(TAG, "takeScreenshot() devolvió null")
                return
            }

            // getExternalFilesDir no requiere WRITE_EXTERNAL_STORAGE en API 29+
            val screenshotDir = File(
                instrumentation.targetContext.getExternalFilesDir(null),
                "sdk_qa_evidence/screenshots"
            )
            screenshotDir.mkdirs()

            val className  = description.className.substringAfterLast('.')
            val methodName = description.methodName
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")  // sanitizar nombre
                .take(80)                                   // limitar longitud

            val file = File(screenshotDir, "${className}_${methodName}.png")

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            bitmap.recycle()

            // Loguear path para que run-tests.sh lo pueda extraer
            Log.i(TAG, "$SCREENSHOT_LOG_PREFIX${file.absolutePath}")
            Log.i(TAG, "Screenshot: ${file.name}")

        } catch (ex: Exception) {
            Log.e(TAG, "Error capturando screenshot: ${ex.message}")
        }
    }
}
