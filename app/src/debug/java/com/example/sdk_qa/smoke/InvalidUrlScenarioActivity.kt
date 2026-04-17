package com.example.sdk_qa.smoke

import am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerConfig
import com.example.sdk_qa.core.BaseScenarioActivity
import com.example.sdk_qa.core.TestContent

class InvalidUrlScenarioActivity : BaseScenarioActivity() {
    override fun getScenarioTitle() = "Invalid URL"
    override fun buildConfig() = MediastreamPlayerConfig().apply {
        src = "https://invalid.example.com/does_not_exist.m3u8"
        accountID = TestContent.ACCOUNT_ID
        type = MediastreamPlayerConfig.VideoTypes.VOD
        environment = TestContent.ENV
        autoplay = true
    }
}
