package com.example.sdk_qa.core

import android.app.Activity

sealed class ScenarioListItem {

    data class Header(val title: String) : ScenarioListItem()

    data class Scenario(
        val title: String,
        val description: String,
        val status: Status,
        val activityClass: Class<out Activity>
    ) : ScenarioListItem()

    enum class Status(val emoji: String) {
        /** Escenario verificado y funcional */
        STABLE("✅"),
        /** Funciona con limitaciones conocidas */
        WARNING("⚠️"),
        /** Actualmente roto o bloqueado */
        BROKEN("🔴"),
        /** Sin verificar aún */
        PENDING("⬜")
    }
}
