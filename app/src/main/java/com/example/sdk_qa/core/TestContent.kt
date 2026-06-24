package com.example.sdk_qa.core

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig

/**
 * Constantes de contenido para pruebas manuales y automatizadas.
 *
 * IDs de producción — no usar en entornos que no sean de confianza.
 * Para cambiar a DEV: comentar PRODUCTION y descomentar DEV en ENV.
 */
object TestContent {

    /** ID de cuenta de Mediastream */
    const val ACCOUNT_ID = "5fbfd5b96660885379e1a129"

    /** Entorno por defecto para todos los escenarios */
    val ENV = MediastreamPlayerConfig.Environment.PRODUCTION
    // val ENV = MediastreamPlayerConfig.Environment.EU   // variante EU (requiere SDK -eu y accessToken)
    // val ENV = MediastreamPlayerConfig.Environment.DEV  // descomentar para pruebas en DEV

    // -------------------------------------------------------------------------
    // Video
    // -------------------------------------------------------------------------
    object Video {
        /** VOD corto — mismo contenido que el reel inicial */
        const val VOD_SHORT = "696e5966d7559b0a0a5225de"

        /** VOD normal (~10 min) — para pruebas de seek. TODO: reemplazar con VOD más largo */
        const val VOD_LONG = "696e5966d7559b0a0a5225de"

        /** Live activo y estable */
        const val LIVE = "5fd39e065d68477eaa1ccf5a"

        /** Live con DVR habilitado — mismo stream, ya tiene DVR activo */
        const val LIVE_DVR = "5fd39e065d68477eaa1ccf5a"

        /** Episodio con siguiente configurado en la plataforma (modo API) */
        const val EPISODE_WITH_NEXT_API = "69e14468941e7a8050a8584f"

        /** Primer episodio para modo manual (custom). Reutiliza el mismo por ahora */
        const val EPISODE_CUSTOM_1 = "69e14468941e7a8050a8584f"

        /** Segundo episodio para modo manual — reutiliza el mismo para validar el callback */
        const val EPISODE_CUSTOM_2 = "69e14468941e7a8050a8584f"

        /** Tercer episodio para modo manual — reutiliza el mismo para validar el callback */
        const val EPISODE_CUSTOM_3 = "69e14468941e7a8050a8584f"

        /** VOD con múltiples anuncios IMA: preroll + midroll (4 ads @ seg 14) + postroll */
        const val VOD_WITH_ADS = "6a3c14c8a9b8c3b23b2e0f86"

        /** VOD con subtítulos (WebVTT o ASS/SSA) configurados en la plataforma.
         *  TODO: reemplazar con ID real — actualmente alias de VOD_LONG */
        const val VOD_WITH_SUBTITLES = VOD_LONG

        /** VOD con DRM Widevine. TODO: reemplazar cuando se tenga ID */
        const val VOD_DRM = "69b0918a741d2bbba0cacf78"

        /** URL directa de HLS público — no requiere API, para tests de red */
        const val SRC_DIRECT_HLS = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

        /** VOD para prueba de cast (rama EU 10.0.8-alpha-eu-01) — requiere accessToken */
        const val CAST_VOD = "69cd939ba93693e9b03200b4"
        const val CAST_VOD_TOKEN =
            "Ydv8KEHlMp6AXLZyH8jryT5OCc9mCXJa7u3YxksZxnleiXEEnewLR6edGr6TK396lMfvr1FMDbG"

        /** Episode (con siguiente configurado) para prueba de cast en EU — requiere accessToken.
         *  Sirve para validar que la sesión de cast se mantiene al cambiar de episodio. */
        const val CAST_EPISODE = "6a39b9db2f77c7113ed97117"
        const val CAST_EPISODE_TOKEN =
            "ViHk6YhfhqEZvRk5p7ngyw1FeiRZ6QQU3gDfLoDXVhxK2N0PfU8V2p6qeKxQ3VBMqx2dhr5Tn6k"
    }

    // -------------------------------------------------------------------------
    // Audio
    // -------------------------------------------------------------------------
    object Audio {
        /** Radio en vivo */
        const val LIVE = "699f00cc48481b0e6ee49f8f"

        /** Podcast / audio bajo demanda */
        const val VOD = "659c1a5cb66e51001357f22c"

        /** Audio on-demand con anuncios IMA configurados */
        const val VOD_WITH_ADS = "66c49553ac52b1666c08c547"

        /** Episodio de audio con siguiente configurado (modo API) */
        const val EPISODE_WITH_NEXT = "696c6c6f6aaa3609e0741f3e"

        /** Episodio 1 para navegación manual — reutiliza el mismo por ahora */
        const val EPISODE_1 = "696c6c6f6aaa3609e0741f3e"

        /** Episodio 2 para navegación manual. TODO: reemplazar con episodio 2 real */
        const val EPISODE_2 = "TODO_AUDIO_EPISODE_2_ID"

        /** Episodio 3 para navegación manual. TODO: reemplazar con episodio 3 real */
        const val EPISODE_3 = "TODO_AUDIO_EPISODE_3_ID"
    }

    // -------------------------------------------------------------------------
    // Reels
    // -------------------------------------------------------------------------
    object Reels {
        /** Player ID del reproductor de reels */
        const val PLAYER_ID = "6980ccd0654c284dc952b544"

        /** Media ID inicial del primer reel */
        const val MEDIA_INITIAL = "6980e43ac0ac0673d0944d63"
    }

    object Drm {
        const val LIVE_ID = "699afcb05a41925324fa4605"
        val ENV = MediastreamPlayerConfig.Environment.DEV

        const val LICENSE_URL = "https://af718b38.drm-widevine-licensing.axprod.net/AcquireLicense"
        const val ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ2ZXJzaW9uIjoxLCJjb21fa2V5X2lkIjoiMjRjZmYwZmMtNGZlYy00NGYyLWFiZTktYjM2OTAxNjViNmFkIiwibWVzc2FnZSI6eyJ0eXBlIjoiZW50aXRsZW1lbnRfbWVzc2FnZSIsInZlcnNpb24iOjEsImV4cGlyYXRpb25fZGF0ZSI6IjIwMjYtMDQtMjhUMjA6Mjg6MzYuNzMzWiIsImtleXMiOlt7ImlkIjoiMjRBRjZEN0YtREQ1Ri00NzlELUIwMUQtOTI3MTREMTk5MzdFIn1dfX0.EKm2ec8zYeEMdrzWH9Slo6C2PQpsGtO5szza_vPDV9o"
    }
}
