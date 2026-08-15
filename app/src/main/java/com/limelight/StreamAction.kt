package com.limelight

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.edit
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.KeyboardPacket
import org.json.JSONObject

private fun Int.toShortKey(): Short = toShort()

data class StreamAction(
    val id: String,
    val label: String,
    val iconRes: Int,
    val iconDisabledRes: Int = 0,
    val labelRes: Int = 0,
    val tintableIcon: Boolean = false,
    val iconText: String? = null,
    val requiresGameFocus: Boolean = false
)

data class CustomKeyData(val name: String, val keys: ShortArray)

object CustomKeyRepository {
    const val PREF_NAME = "custom_special_keys"
    const val KEY_NAME = "data"

    fun load(context: Context, showErrorToast: Boolean = false): List<CustomKeyData> {
        val result = mutableListOf<CustomKeyData>()
        val preferences = context.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE)
        var value = preferences.getString(KEY_NAME, "")

        if (value.isNullOrEmpty()) {
            value = readDefaultKeys(context)
            if (value.isNotEmpty()) {
                preferences.edit { putString(KEY_NAME, value) }
            }
        }

        if (value.isEmpty()) return result

        try {
            val root = JSONObject(value)
            val dataArray = root.optJSONArray("data") ?: return result
            for (i in 0 until dataArray.length()) {
                val keyObject = dataArray.getJSONObject(i)
                val name = keyObject.optString("name")
                val codesArray = keyObject.getJSONArray("data")
                val keys = ShortArray(codesArray.length()) { j ->
                    codesArray.getString(j).substring(2).toInt(16).toShort()
                }
                result.add(CustomKeyData(name, keys))
            }
        } catch (e: Exception) {
            LimeLog.warning("Exception while loading custom keys: ${e.message}")
            if (showErrorToast) {
                Toast.makeText(context, R.string.toast_load_custom_keys_corrupted, Toast.LENGTH_SHORT).show()
            }
        }

        return result
    }

    private fun readDefaultKeys(context: Context): String {
        return try {
            context.resources.openRawResource(R.raw.default_special_keys).use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            LimeLog.warning("Failed to read default custom keys: ${e.message}")
            ""
        }
    }
}

object StreamActionRegistry {
    private val QUICK_ACTION_IDS = listOf(
        "send_win",
        "send_esc",
        "toggle_hdr",
        "toggle_mic",
        "send_sleep",
        "quit",
        "send_tab",
        "send_alt_tab",
        "send_alt_f4",
        "toggle_keyboard",
        "toggle_controller",
        "toggle_perf"
    )

    val BUILTIN = linkedMapOf(
        "open_keyboard" to StreamAction("open_keyboard", "KB", R.drawable.ic_keyboard_cute, 0, R.string.quick_btn_keyboard),
        "open_menu" to StreamAction("open_menu", "Menu", R.drawable.ic_menu_item_default),
        "toggle_visibility" to StreamAction("toggle_visibility", "Hide", R.drawable.ic_btn_quit, tintableIcon = true),
        "send_win" to StreamAction("send_win", "Win", R.drawable.ic_btn_win, labelRes = R.string.quick_btn_win, tintableIcon = true),
        "send_esc" to StreamAction("send_esc", "Esc", R.drawable.ic_btn_esc, labelRes = R.string.quick_btn_esc, tintableIcon = true),
        "toggle_hdr" to StreamAction("toggle_hdr", "HDR", R.drawable.ic_btn_hdr, labelRes = R.string.quick_btn_hdr, tintableIcon = true),
        "toggle_mic" to StreamAction("toggle_mic", "Mic", R.drawable.ic_mic_gm, R.drawable.ic_mic_gm_disabled, R.string.quick_btn_mic),
        "send_sleep" to StreamAction(
            "send_sleep", "Sleep", R.drawable.ic_btn_sleep,
            labelRes = R.string.quick_btn_sleep,
            tintableIcon = true
        ),
        "quit" to StreamAction(
            "quit", "Quit", R.drawable.ic_btn_quit,
            labelRes = R.string.quick_btn_quit,
            tintableIcon = true
        ),
        "send_tab" to StreamAction("send_tab", "Tab", 0, labelRes = R.string.quick_btn_tab, iconText = "HK"),
        "send_alt_tab" to StreamAction("send_alt_tab", "Alt+Tab", 0, labelRes = R.string.quick_btn_alt_tab, iconText = "HK"),
        "send_alt_f4" to StreamAction("send_alt_f4", "Alt+F4", 0, labelRes = R.string.quick_btn_alt_f4, iconText = "HK"),
        "toggle_keyboard" to StreamAction(
            "toggle_keyboard",
            "KB",
            R.drawable.ic_keyboard_cute,
            labelRes = R.string.quick_btn_keyboard,
            requiresGameFocus = true
        ),
        "toggle_controller" to StreamAction("toggle_controller", "Pad", R.drawable.ic_controller_cute, 0, R.string.quick_btn_controller),
        "toggle_perf" to StreamAction("toggle_perf", "Perf", R.drawable.ic_performance_cute, 0, R.string.quick_btn_perf),
    )

    fun getBuiltin(id: String): StreamAction? = BUILTIN[id]

    fun getAllActions(customKeys: List<Array<String>>?): LinkedHashMap<String, StreamAction> {
        val all = LinkedHashMap(BUILTIN)
        customKeys?.forEach { customKey ->
            val id = customActionId(customKey[0])
            all[id] = StreamAction(id, customKey[0], 0)
        }
        return all
    }

    fun getQuickActions(customKeys: List<Array<String>>?): LinkedHashMap<String, StreamAction> {
        val all = linkedMapOf<String, StreamAction>()
        QUICK_ACTION_IDS.forEach { id ->
            BUILTIN[id]?.let { all[id] = it }
        }
        customKeys?.forEach { customKey ->
            val id = customActionId(customKey[0])
            all[id] = StreamAction(id, customKey[0], 0)
        }
        return all
    }

    fun customActionId(name: String): String = "custom_$name"
}

class StreamActionExecutor(
    private val game: Game,
    private val connProvider: () -> NvConnection?,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    fun execute(actionId: String?): Boolean {
        if (actionId.isNullOrEmpty() || actionId == "none") return false

        return when (actionId) {
            "open_keyboard" -> {
                game.toggleVirtualKeyboard()
                true
            }
            "open_menu" -> {
                game.showGameMenu(null)
                true
            }
            "toggle_visibility" -> {
                game.floatBallHandler.toggleVisibility()
                true
            }
            "send_win" -> sendKeys(shortArrayOf(KeyboardTranslator.VK_LWIN.toShortKey()))
            "send_esc" -> sendKeys(shortArrayOf(KeyboardTranslator.VK_ESCAPE.toShortKey()))
            "toggle_hdr" -> sendKeys(shortArrayOf(
                KeyboardTranslator.VK_LWIN.toShortKey(),
                KeyboardTranslator.VK_MENU.toShortKey(),
                KeyboardTranslator.VK_B.toShortKey()
            ))
            "toggle_mic" -> {
                game.handleMicrophoneMenuAction()
                true
            }
            "send_sleep" -> {
                sendKeys(shortArrayOf(KeyboardTranslator.VK_LWIN.toShortKey(), 88.toShortKey()))
                handler.postDelayed({ sendKeys(shortArrayOf(85.toShortKey(), 83.toShortKey())) }, SLEEP_DELAY)
                true
            }
            "quit" -> {
                if (game.prefConfig.swapQuitAndDisconnect) game.disconnect() else disconnectAndQuit()
                true
            }
            "send_tab" -> sendKeys(shortArrayOf(KeyboardTranslator.VK_TAB.toShortKey()))
            "send_alt_tab" -> sendKeys(shortArrayOf(
                KeyboardTranslator.VK_MENU.toShortKey(),
                KeyboardTranslator.VK_TAB.toShortKey()
            ))
            "send_alt_f4" -> sendKeys(shortArrayOf(
                KeyboardTranslator.VK_MENU.toShortKey(),
                (KeyboardTranslator.VK_F1 + 3).toShortKey()
            ))
            "toggle_keyboard" -> {
                game.toggleKeyboard()
                true
            }
            "toggle_controller" -> {
                game.toggleVirtualController()
                true
            }
            "toggle_perf" -> {
                game.togglePerformanceOverlay()
                true
            }
            else -> executeCustomAction(actionId)
        }
    }

    fun sendKeys(keys: ShortArray, afterRelease: (() -> Unit)? = null): Boolean {
        val conn = connProvider() ?: return false
        if (keys.isEmpty()) return false

        var modifier: Byte = 0
        for (key in keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier, 0)
            modifier = (modifier.toInt() or KeyModifier.getModifier(key).toInt()).toByte()
        }

        val finalModifier = modifier
        handler.postDelayed({
            var mod = finalModifier
            for (pos in keys.indices.reversed()) {
                val key = keys[pos]
                mod = (mod.toInt() and KeyModifier.getModifier(key).toInt().inv()).toByte()
                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, mod, 0)
            }
            afterRelease?.invoke()
        }, KEY_UP_DELAY)

        return true
    }

    fun disconnectAndQuit() {
        try {
            if (game.prefConfig.lockScreenAfterDisconnect) {
                val lockKeysSent = sendKeys(
                    shortArrayOf(
                        KeyboardTranslator.VK_LWIN.toShortKey(),
                        KeyboardTranslator.VK_L.toShortKey()
                    ),
                    ::finishDisconnectAndQuit
                )
                if (lockKeysSent) return
            }
            finishDisconnectAndQuit()
        } catch (e: Exception) {
            Toast.makeText(game, game.getString(R.string.toast_disconnect_error, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishDisconnectAndQuit() {
        try {
            game.disconnect()
            connProvider()?.doStopAndQuit()
        } catch (e: Exception) {
            Toast.makeText(game, game.getString(R.string.toast_disconnect_error, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeCustomAction(actionId: String): Boolean {
        if (!actionId.startsWith(CUSTOM_ACTION_PREFIX)) return false
        val name = actionId.substring(CUSTOM_ACTION_PREFIX.length)
        val customKey = CustomKeyRepository.load(game).find { it.name == name } ?: return false
        return sendKeys(customKey.keys)
    }

    private enum class KeyModifier(val keyCode: Short, val modifier: Byte) {
        SHIFT(KeyboardTranslator.VK_LSHIFT.toShortKey(), KeyboardPacket.MODIFIER_SHIFT),
        CTRL(KeyboardTranslator.VK_LCONTROL.toShortKey(), KeyboardPacket.MODIFIER_CTRL),
        META(KeyboardTranslator.VK_LWIN.toShortKey(), KeyboardPacket.MODIFIER_META),
        ALT(KeyboardTranslator.VK_MENU.toShortKey(), KeyboardPacket.MODIFIER_ALT);

        companion object {
            fun getModifier(key: Short): Byte =
                entries.find { it.keyCode == key }?.modifier ?: 0
        }
    }

    companion object {
        private const val CUSTOM_ACTION_PREFIX = "custom_"
        private const val KEY_UP_DELAY = 25L
        private const val SLEEP_DELAY = 200L
    }
}
