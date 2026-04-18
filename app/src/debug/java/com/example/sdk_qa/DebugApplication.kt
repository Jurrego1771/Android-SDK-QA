package com.example.sdk_qa

import android.app.Application
import leakcanary.LeakCanary
import shark.LibraryLeakReferenceMatcher
import shark.ReferencePattern.InstanceFieldPattern

/**
 * Application subclass activo únicamente en el build debug.
 *
 * Configura LeakCanary para clasificar los leaks conocidos del SDK de Mediastream
 * como LIBRARY LEAKS en vez de APPLICATION LEAKS. Esto significa:
 *   - LeakCanary los sigue reportando en Logcat/dump para análisis
 *   - Los tests NO fallan por ellos (solo fallan por APPLICATION LEAKS)
 *
 * Bug reportado a Mediastream:
 *   MediastreamPlayer retiene una referencia fuerte a la Activity en su campo
 *   obfuscado `l`. La llamada a releasePlayer() no la limpia.
 *   Chain: MediastreamPlayer.l → Activity (mDestroyed = true)
 *   También: MediastreamPlayer.s0 → FragmentManagerImpl (secondary path)
 */
class DebugApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        configureLeakCanary()
    }

    private fun configureLeakCanary() {
        LeakCanary.config = LeakCanary.config.copy(
            referenceMatchers = LeakCanary.config.referenceMatchers + listOf(

                // SDK BUG: MediastreamPlayer no limpia su referencia a la Activity
                // después de releasePlayer(). Campo `l` = referencia obfuscada a la Activity.
                LibraryLeakReferenceMatcher(
                    pattern = InstanceFieldPattern(
                        className = "am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayer",
                        fieldName = "l"
                    ),
                    description = "SDK BUG [REPORTAR A MEDIASTREAM]: MediastreamPlayer.l retiene " +
                        "la Activity después de releasePlayer(). El SDK no limpia la referencia " +
                        "al Activity en su cleanup, causando un memory leak."
                ),

                // Secondary path: MediastreamPlayer.s0 → FragmentManagerImpl → FragmentManagerViewModel
                LibraryLeakReferenceMatcher(
                    pattern = InstanceFieldPattern(
                        className = "am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayer",
                        fieldName = "s0"
                    ),
                    description = "SDK BUG [REPORTAR A MEDIASTREAM]: MediastreamPlayer.s0 retiene " +
                        "el FragmentManagerImpl después de releasePlayer()."
                )
            )
        )
    }
}
