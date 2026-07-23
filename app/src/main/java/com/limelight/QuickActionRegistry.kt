package com.limelight

import android.content.ContentValues
import android.content.Context
import com.limelight.binding.input.advance_setting.config.PageConfigController
import org.json.JSONArray

object QuickActionRegistry {
    private const val PREF_FILE = "quick_button_config"
    private const val PREF_KEY = "button_ids"

    private val BASE_DEFAULT_IDS = listOf(
        "send_esc",
        "send_win",
        "send_alt_tab",
        "toggle_keyboard",
        "toggle_perf"
    )

    fun getAllActions(customKeys: List<Array<String>>?): LinkedHashMap<String, StreamAction> =
        StreamActionRegistry.getQuickActions(customKeys)

    fun getBuiltin(id: String): StreamAction? = StreamActionRegistry.getBuiltin(id)

    fun defaultIds(context: Context): MutableList<String> {
        val config = (context as? Game)?.prefConfig
        return defaultIdsFor(
            enableHdr = config?.enableHdr == true,
            enableMic = config?.enableMic == true,
            enableVirtualController = config?.onscreenController == true
        ).toMutableList()
    }

    internal fun defaultIdsFor(
        enableHdr: Boolean,
        enableMic: Boolean,
        enableVirtualController: Boolean
    ): List<String> = buildList {
        addAll(BASE_DEFAULT_IDS)
        if (enableHdr) add("toggle_hdr")
        if (enableMic) add("toggle_mic")
        if (enableVirtualController) add("toggle_controller")
        add("quit")
    }

    fun loadConfig(context: Context): MutableList<String> {
        loadProfileConfig(context)?.let { return it }
        return loadLegacyConfig(context)
    }

    fun saveConfig(context: Context, ids: List<String>) {
        if (saveProfileConfig(context, ids)) return
        saveLegacyConfig(context, ids)
    }

    private fun loadProfileConfig(context: Context): MutableList<String>? {
        val game = context as? Game ?: return null
        val controllerManager = game.controllerManager ?: return null
        val configId = controllerManager.pageConfigController?.currentConfigId ?: return null
        val databaseHelper = controllerManager.superConfigDatabaseHelper ?: return null
        val json = databaseHelper.queryConfigAttribute(
            configId,
            PageConfigController.COLUMN_STRING_QUICK_ACTION_IDS,
            ""
        ) as? String

        if (json.isNullOrEmpty()) return null
        return parseConfig(json)
    }

    private fun saveProfileConfig(context: Context, ids: List<String>): Boolean {
        val game = context as? Game ?: return false
        val controllerManager = game.controllerManager ?: return false
        val configId = controllerManager.pageConfigController?.currentConfigId ?: return false
        val databaseHelper = controllerManager.superConfigDatabaseHelper ?: return false
        val values = ContentValues().apply {
            put(PageConfigController.COLUMN_STRING_QUICK_ACTION_IDS, encodeConfig(ids))
        }
        databaseHelper.updateConfig(configId, values)
        return true
    }

    private fun loadLegacyConfig(context: Context): MutableList<String> {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val json = prefs.getString(PREF_KEY, null)
        if (json != null) {
            parseConfig(json)?.let { return it }
        }
        return defaultIds(context)
    }

    private fun saveLegacyConfig(context: Context, ids: List<String>) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY, encodeConfig(ids))
            .apply()
    }

    private fun encodeConfig(ids: List<String>): String =
        JSONArray().apply { ids.forEach { put(it) } }.toString()

    private fun parseConfig(json: String): MutableList<String>? {
        return try {
            val arr = JSONArray(json)
            MutableList(arr.length()) { arr.getString(it) }
        } catch (_: Exception) {
            null
        }
    }
}
