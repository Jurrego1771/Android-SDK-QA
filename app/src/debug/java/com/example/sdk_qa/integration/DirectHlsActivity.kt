package com.example.sdk_qa.integration

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

class DirectHlsActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Direct HLS (Integration)"

    override fun buildConfig() = buildDirectConfig()

    companion object {
        fun buildDirectConfig() = MediastreamPlayerConfig().apply {
            src = TestContent.Video.SRC_DIRECT_HLS
            type = MediastreamPlayerConfig.VideoTypes.VOD
            autoplay = true
            isDebug = false
        }
    }
}
