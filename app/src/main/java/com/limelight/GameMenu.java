package com.limelight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.KeyMapper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provide options for ongoing Game Stream.
 * <p>
 * Shown on back action in game activity.
 */
public class GameMenu implements Game.GameMenuCallbacks {

    private static final long TEST_GAME_FOCUS_DELAY = 10;
    private static final long KEY_UP_DELAY = 25;

    public static final String PREF_NAME = "specialPrefs"; // SharedPreferences的名称

    public static final String KEY_NAME = "special_key"; // 要保存的键名称

    public static class MenuOption {
        private final String label;
        private final boolean withGameFocus;
        private final Runnable runnable;

        public MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.runnable = runnable;
        }

        public MenuOption(String label, Runnable runnable) {
            this(label, false, runnable);
        }
    }

    private final Game game;
    private final NvConnection conn;

    private AlertDialog currentDialog;

    public GameMenu(Game game, NvConnection conn) {
        this.game = game;
        this.conn = conn;
    }

    private String getString(int id) {
        return game.getResources().getString(id);
    }

    public static byte getModifier(short key) {
        switch (key) {
            case KeyboardTranslator.VK_LSHIFT:
                return KeyboardPacket.MODIFIER_SHIFT;
            case KeyboardTranslator.VK_LCONTROL:
                return KeyboardPacket.MODIFIER_CTRL;
            case KeyboardTranslator.VK_LWIN:
                return KeyboardPacket.MODIFIER_META;
            case KeyboardTranslator.VK_LMENU:
                return KeyboardPacket.MODIFIER_ALT;
            default:
                return 0;
        }
    }

    private void sendKeys(short[] keys) {
        final byte[] modifier = {(byte) 0};

        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);

            // Apply the modifier of the pressed key, e.g. CTRL first issues a CTRL event (without
            // modifier) and then sends the following keys with the CTRL modifier applied
            modifier[0] |= getModifier(key);
        }

        new Handler().postDelayed((() -> {
            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];

                // Remove the keys modifier before releasing the key
                modifier[0] &= (byte) ~getModifier(key);

                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }), KEY_UP_DELAY);
    }

    private void runWithGameFocus(Runnable runnable) {
        // Ensure that the Game activity is still active (not finished)
        if (game.isFinishing()) {
            return;
        }
        // Check if the game window has focus again, if not try again after delay
        if (!game.hasWindowFocus()) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        // Game Activity has focus, run runnable
        runnable.run();
    }

    private void run(MenuOption option) {
        if (option.runnable == null) {
            return;
        }

        if (option.withGameFocus) {
            runWithGameFocus(option.runnable);
        } else {
            option.runnable.run();
        }
    }

    private void showMenuDialog(String title, MenuOption[] options) {
        AlertDialog.Builder builder = new AlertDialog.Builder(game);
        builder.setTitle(title);

        final ArrayAdapter<String> actions =
                new ArrayAdapter<String>(game, android.R.layout.simple_list_item_1);

        builder.setAdapter(actions, (dialog, which) -> {
            String label = actions.getItem(which);
            for (MenuOption option : options) {
                if (!label.equals(option.label)) {
                    continue;
                }

                run(option);
                break;
            }
        });

        if (currentDialog != null) {
            currentDialog.dismiss();
        }
        currentDialog = builder.show();

        Window window = currentDialog.getWindow();

        if (window != null) {
            View decorView = window.getDecorView();
            decorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {

                    decorView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    new Handler(Looper.getMainLooper()).post(() -> {
                        for (MenuOption option : options) {
                            actions.add(option.label);
                        }
                        actions.notifyDataSetChanged();
                    });
                }
            });
        }
    }

    private void showSpecialKeysMenu() {
        List<MenuOption> options = new ArrayList<>();

        if(!PreferenceConfiguration.readPreferences(game).enableClearDefaultSpecial){
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_esc),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_f11),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_F11})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_f4),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_enter),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_v),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_V})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_d),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_g),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_shift_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_shift_left),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_q),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_Q})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f1),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F1})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f12),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F12})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_b),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_B})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_x_u_s), () -> {
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_S})), 200);
            }));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_x_u_u), () -> {
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_U})), 200);
            }));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_x_u_r), () -> {
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_R})), 200);
            }));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_x_u_i), () -> {
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_I})), 200);
            }));

        }

        //自定义导入的指令
        SharedPreferences preferences = game.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE);
        String value = preferences.getString(KEY_NAME,"");

        if(!TextUtils.isEmpty(value)){
            try {
                JSONObject object = new JSONObject(value);
                JSONArray array = object.optJSONArray("data");
                if(array != null&&array.length()>0){
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject object1 = array.getJSONObject(i);
                        String name = object1.optString("name");
                        JSONArray array1 = object1.getJSONArray("keys");
                        short[] datas = new short[array1.length()];
                        for (int j = 0; j < array1.length(); j++) {
                            String code = array1.getString(j);
                            int keycode;
                            if (code.startsWith("0x")) {
                                keycode = Integer.parseInt(code.substring(2), 16);
                            } else if (code.startsWith("VK_")) {
                                Field vkCodeField = KeyMapper.class.getDeclaredField(code);
                                keycode = vkCodeField.getInt(null);
                            } else {
                                throw new Exception("Unknown key code: " + code);
                            }
                            datas[j] = (short) keycode;
                        }
                        MenuOption option = new MenuOption(name, () -> sendKeys(datas));
                        options.add(option);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(game,getString(R.string.wrong_import_format),Toast.LENGTH_SHORT).show();
            }
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_send_keys), options.toArray(new MenuOption[options.size()]));
    }

    private void showAdvancedMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();

        if (game.presentation == null) {
            options.add(new MenuOption(getString(R.string.game_menu_select_mouse_mode), true,
                    game::selectMouseMode));
        }

        options.add(new MenuOption(getString(R.string.game_menu_hud), true,
                game::toggleHUD));

        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard_model), true,
                game::showHideKeyboardController));

        options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_model), true,
                game::showHideVirtualController));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_keyboard_model), true,
                game::showHidekeyBoardLayoutController));

        options.add(new MenuOption(getString(R.string.game_menu_task_manager), true,
                () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE})));

        options.add(new MenuOption(getString(R.string.game_menu_send_keys), true, this::showSpecialKeysMenu));

        options.add(new MenuOption(getString(R.string.game_menu_switch_touch_sensitivity_model), true,
                game::switchTouchSensitivity));

        if (device != null) {
            options.addAll(device.getGameMenuOptions());
        }

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_advanced), options.toArray(new MenuOption[options.size()]));
    }

    private void showServerCmd(ArrayList<String> serverCmds) {
        List<MenuOption> options = new ArrayList<>();

        AtomicInteger index = new AtomicInteger(0);
        for (String str : serverCmds) {
            final int finalI = index.getAndIncrement();
            options.add(new MenuOption("> " + str, true, () -> game.sendExecServerCmd(finalI)));
        };

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_server_cmd), options.toArray(new MenuOption[options.size()]));
    }

    public void showMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();

        options.add(new MenuOption(getString(R.string.game_menu_disconnect), game::disconnect));

        options.add(new MenuOption(getString(R.string.game_menu_quit_session), game::quit));

        options.add(new MenuOption(getString(R.string.game_menu_upload_clipboard), true,
                () -> game.sendClipboard(true)));

        options.add(new MenuOption(getString(R.string.game_menu_fetch_clipboard), true,
                () -> game.getClipboard(0)));

        options.add(new MenuOption(getString(R.string.game_menu_server_cmd), true,
                () -> {
                    ArrayList<String> serverCmds = game.getServerCmds();

                    if (serverCmds.isEmpty()) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(game);
                        builder.setTitle(R.string.game_dialog_title_server_cmd_empty);
                        builder.setMessage(R.string.game_dialog_message_server_cmd_empty);

                        AlertDialog dialog = builder.create();
                        dialog.show();
                    } else {
                        this.showServerCmd(serverCmds);
                    }
                }));

        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), true,
                game::toggleKeyboard));

        options.add(new MenuOption(getString(game.isZoomModeEnabled() ? R.string.game_menu_disable_zoom_mode : R.string.game_menu_enable_zoom_mode), true,
                game::toggleZoomMode));

        options.add(new MenuOption(getString(R.string.game_menu_rotate_screen), true,
                game::rotateScreen));

        options.add(new MenuOption(getString(R.string.game_menu_advanced), true,
                () -> showAdvancedMenu(device)));

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.quick_menu_title), options.toArray(new MenuOption[options.size()]));
    }

    public void hideMenu() {
        if (currentDialog != null) {
            currentDialog.dismiss();
            currentDialog = null;
        }
    }

    public boolean isMenuOpen() {
        if (currentDialog == null) {
            return false;
        }
        return currentDialog.isShowing();
    }
}