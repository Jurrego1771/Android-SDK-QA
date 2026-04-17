package com.example.sdk_qa.integration

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

class DirectHlsActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "Direct HLS (Integration)"

    override fun buildConfig() = buildDirectConfig()

    companion object {
        fun buildDirectConfig() = MediastreamPlayerConfig().apply {
            id = TestContent.Video.VOD_SHORT
            accountID = TestContent.ACCOUNT_ID
            type = MediastreamPlayerConfig.VideoTypes.VOD
            environment = TestContent.ENV
            autoplay = true
            isDebug = false
        }
    }
}
