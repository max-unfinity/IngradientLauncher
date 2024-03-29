package com.secretingradient.launchertest.data

import java.io.Serializable

interface Data : Serializable {
    fun createInfo(dataKeeper: DataKeeper) : Info
}

// its all for UserStage:
//interface ElementData : Data
//class AppData(val appId: String, val pos: Int) : Data {
//    companion object {private const val serialVersionUID = 44001L}
//}
//
//class FolderData(var ids: List<String>) : Data {
//    companion object {private const val serialVersionUID = 44002L}
//}
//
//class WidgetData() : Data {
//    companion object {private const val serialVersionUID = 44003L}
//}