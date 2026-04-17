package com.example.sdk_qa.regression

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

class TvPlayerActivity : BaseScenarioActivity() {

    override fun getScenarioTitle() = "TV Focus Test"

    override fun buildConfig() = MediastreamPlayerConfig().apply {
        id = TestContent.Video.VOD_LONG
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
        isDebug = false
        showControls = true
        initialHideController = false
    }
}
