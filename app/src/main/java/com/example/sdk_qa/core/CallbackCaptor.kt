package com.example.sdk_qa.core

import android.os.Looper
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Registra eventos de callbacks del SDK para verificación en tests instrumentados.
 *
 * Uso en test:
 *   val captor = activity.callbackCaptor
 *   assertThat(captor.awaitEvent("onReady", 15_000)).isTrue()
 *   assertThat(captor.hasEvent("onError")).isFalse()
 */
class CallbackCaptor {

    private val latches = ConcurrentHashMap<String, CountDownLatch>()
    private val fired = ConcurrentHashMap.newKeySet<String>()
    private val eventThreads = ConcurrentHashMap<String, String>() // event → thread name
    private val eventOrder = Collections.synchronizedList(mutableListOf<String>())

    /**
     * Registra que el evento [name] ocurrió.
     * Llamado desde el callback del SDK (puede venir de cualquier thread).
     */
    fun recordEvent(name: String) {
        fired.add(name)
        eventThreads[name] = Thread.currentThread().name
        eventOrder.add(name)
        // Si ya hay un latch esperando, lo libera. Si no, crea uno resuelto
        // para que awaitEvent() futuro retorne true inmediatamente.
        latches.getOrPut(name) { CountDownLatch(1) }.countDown()
    }

    /**
     * Bloquea hasta que [name] sea registrado o expire [timeoutMs].
     * @return true si el evento llegó, false si hubo timeout.
     */
    fun awaitEvent(name: String, timeoutMs: Long = 15_000): Boolean {
        val latch = latches.getOrPut(name) { CountDownLatch(1) }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /** true si el evento ya fue registrado (sin bloquear). */
    fun hasEvent(name: String): Boolean = fired.contains(name)

    /**
     * Verifica que el evento [name] fue disparado desde el main thread.
     * Útil para garantizar el contrato de threading del SDK.
     */
    fun firedOnMainThread(name: String): Boolean {
        val threadName = eventThreads[name] ?: return false
        return threadName == Looper.getMainLooper().thread.name
    }

    /** Limpia todos los eventos y latches — útil entre tests. */
    fun reset() {
        fired.clear()
        latches.clear()
        eventThreads.clear()
        eventOrder.clear()
    }

    /** Lista de todos los eventos registrados hasta ahora (orden de inserción no garantizado). */
    fun allEvents(): Set<String> = fired.toSet()

    /**
     * Lista ordenada de eventos en el orden exacto en que llegaron.
     * Permite verificar invariantes de secuencia: onReady antes que onPlay, etc.
     * Puede contener duplicados si el mismo evento fue registrado varias veces.
     */
    fun eventOrderSnapshot(): List<String> = synchronized(eventOrder) { eventOrder.toList() }
}
