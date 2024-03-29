package com.secretingradient.launchertest.data

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import com.secretingradient.launchertest.LauncherException
import com.secretingradient.launchertest.decodeBitmap
import com.secretingradient.launchertest.encodeBitmap
import java.io.Serializable

class DataKeeper(val context: Context) {

    // this is pool for Bitmaps and data needed to create AppInfo
    private val allAppsCacheData: FileDataset<String, AppCache>
    val appDataset: Dataset<AppData, AppInfo>
    val folderDataset: Dataset<FolderData, FolderInfo>
    val allAppsIds: MutableList<String>

    init {
        allAppsCacheData = FileDataset(context,"all_apps_cache_data")
        fetchUpdates()
        allAppsCacheData.rawDataset.forEach { it.value.loadIcon(context) }
        allAppsIds = allAppsCacheData.rawDataset.keys.toMutableList()
        appDataset = Dataset(this, "test")
        folderDataset = Dataset(this, "test2")
    }

    private fun getLaunchableApps(): List<ResolveInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null)
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, 0)
    }

    fun fetchUpdates(size: Int? = null) {
        println("fetching updates...")
        val realAppInfo = getLaunchableApps()
        val realAppMap = mutableMapOf<String, ResolveInfo>()
        realAppInfo.forEach {realAppMap[AppCache.getIdFromResolveInfo(it)] = it}
        val realApps = realAppMap.keys
        val cachedApps = allAppsCacheData.rawDataset.keys as Set<String>
        if (realApps != cachedApps) {
            val newApps = realApps - cachedApps
            val oldApps = cachedApps - realApps
            oldApps.forEach {
                allAppsCacheData.rawDataset.remove(it)
            }
            newApps.forEach {
                val resolveInfo = realAppMap[it]
                allAppsCacheData.rawDataset[it] = AppCache(context, resolveInfo!!)
            }
            println("newApps: ${newApps.size}, oldApps: ${oldApps.size}, now: ${allAppsCacheData.rawDataset.size} apps")
            allAppsCacheData.dumpFileData()
            if (oldApps.isNotEmpty()) {
                appDataset.dumpFileData()
            }
        }
    }

    private fun getAppCache(id: String): AppCache {
        if (allAppsCacheData.rawDataset[id] == null) throw LauncherException("id $id for App not found in allAppsCacheData")
        return allAppsCacheData.rawDataset[id]!!
    }

    fun createAppInfo(id: String): AppInfo {
        return getAppCache(id).createAppInfo()
    }

    class AppCache(context: Context, resolveInfo: ResolveInfo) : Serializable {
        private val packageName: String
        private val name: String
        private val label: String
        private var iconByteArray: ByteArray?
        private var icon: Drawable? = null

        init {
            // +prepare for dump
            val pm = context.packageManager
            val activity = resolveInfo.activityInfo
            packageName = activity.packageName
            name = activity.name
            label = activity.loadLabel(pm).toString()
            iconByteArray = encodeIcon(activity.loadIcon(pm))
        }

        fun loadIcon(context: Context) {
            icon = decodeIcon(context)
            iconByteArray = null
        }

        fun createAppInfo(): AppInfo {
            return AppInfo(packageName, name, label, icon!!)
        }

        private fun encodeIcon(bitmap: Drawable, size: Int? = null): ByteArray {
            return encodeBitmap(if (size == null) bitmap.toBitmap() else bitmap.toBitmap(size, size))
        }

        private fun decodeIcon(context: Context): BitmapDrawable {
            return BitmapDrawable(context.resources, decodeBitmap(iconByteArray!!))
        }

        companion object {
            private const val serialVersionUID = 44010L

            fun getIdFromResolveInfo(resolveInfo: ResolveInfo): String {
                return "${resolveInfo.activityInfo.packageName}_${resolveInfo.activityInfo.name}"
            }
        }
    }



}