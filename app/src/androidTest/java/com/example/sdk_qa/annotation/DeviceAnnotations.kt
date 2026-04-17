package com.example.sdk_qa.annotation

/**
 * Anotaciones para controlar en qué tipo de dispositivo corre cada test.
 *
 * Tests SIN anotación → corren en TODOS los dispositivos.
 *
 * Uso en conjunto con run-tests.sh:
 *   --target tv      → excluye @MobileOnly
 *   --target mobile  → excluye @TvOnly y @FireTvOnly
 *   --target firetv  → excluye @MobileOnly
 *   --target all     → sin filtro
 *
 * Ejemplo:
 *   @Test
 *   @TvOnly
 *   fun dpad_navigation_works() { ... }
 *
 *   @Test
 *   @MobileOnly
 *   fun touch_seek_works() { ... }
 */

/** Solo corre en Android TV / Google TV. No aplica en móviles ni Fire TV. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TvOnly

/** Solo corre en smartphones y tablets. No aplica en TV ni Fire TV. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MobileOnly

/** Solo corre en Fire TV. No aplica en móviles ni Android TV. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class FireTvOnly
