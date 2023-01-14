package com.limelight;

import android.app.AlertDialog;
import android.widget.ArrayAdapter;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

public class GameBackMenu {

    private final String ACTION_SEND_SPECIAL_KEYS;
    private final String ACTION_DISCONNECT;
    private final String ACTION_CANCEL;

    private final String ACTION_SEND_SPECIAL_KEYS_ESC;
    private final String ACTION_SEND_SPECIAL_KEYS_F11;
    private final String ACTION_SEND_SPECIAL_KEYS_WIN;
    private final String ACTION_SEND_SPECIAL_KEYS_WIN_D;
    private final String ACTION_SEND_SPECIAL_KEYS_WIN_G;

    private static class MenuOption {
        private final String label;
        private final Runnable runnable;

        MenuOption(String label, Runnable runnable) {
            this.label = label;
            this.runnable = runnable;
        }
    }


    private final Game game;
    private final NvConnection conn;

    public GameBackMenu(Game game, NvConnection conn) {
        this.game = game;
        this.conn = conn;

        this.ACTION_SEND_SPECIAL_KEYS = getString(R.string.back_menu_send_keys);
        this.ACTION_DISCONNECT = getString(R.string.back_menu_disconnect);
        this.ACTION_CANCEL = getString(R.string.back_menu_cancel);

        this.ACTION_SEND_SPECIAL_KEYS_ESC = getString(R.string.back_menu_send_keys_esc);
        this.ACTION_SEND_SPECIAL_KEYS_F11 = getString(R.string.back_menu_send_keys_f11);
        this.ACTION_SEND_SPECIAL_KEYS_WIN = getString(R.string.back_menu_send_keys_win);
        this.ACTION_SEND_SPECIAL_KEYS_WIN_D = getString(R.string.back_menu_send_keys_win_d);
        this.ACTION_SEND_SPECIAL_KEYS_WIN_G = getString(R.string.back_menu_send_keys_win_g);

        showBackMenu();
    }

    private String getString(int id) {
        return game.getResources().getString(id);
    }

    private void sendKeySequence(byte modifier, short[] keys) {
        for (short key : keys)
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN,
                    (byte) (modifier | KeyboardPacket.KEY_DOWN));

        for (int pos = keys.length - 1; pos >= 0; pos--)
            conn.sendKeyboardInput(keys[pos], KeyboardPacket.KEY_UP,
                    (byte) (modifier | KeyboardPacket.KEY_UP));
    }

    private void showMenuDialog(String title, MenuOption[] options) {
        AlertDialog.Builder builder = new AlertDialog.Builder(game);
        builder.setTitle(title);

        final ArrayAdapter<String> actions =
                new ArrayAdapter<String>(game, android.R.layout.simple_list_item_1);

        for (MenuOption option : options) {
            actions.add(option.label);
        }

        builder.setAdapter(actions, (dialog, which) -> {
            String label = actions.getItem(which);
            for (MenuOption option : options) {
                if (!label.equals(option.label)) {
                    continue;
                }

                if (option.runnable != null) {
                    option.runnable.run();
                }
                break;
            }
        });

        builder.show();
    }

    private void showSpecialKeysMenu() {
        showMenuDialog(ACTION_SEND_SPECIAL_KEYS, new MenuOption[]{
                new MenuOption(ACTION_SEND_SPECIAL_KEYS_ESC, () -> sendKeySequence(
                        (byte) 0, new short[]{0x18})),
                new MenuOption(ACTION_SEND_SPECIAL_KEYS_F11, () -> sendKeySequence(
                        (byte) 0, new short[]{0x7a})),
                new MenuOption(ACTION_SEND_SPECIAL_KEYS_WIN, () -> sendKeySequence(
                        (byte) 0, new short[]{0x5B})),
                new MenuOption(ACTION_SEND_SPECIAL_KEYS_WIN_D, () -> sendKeySequence(
                        (byte) 0, new short[]{0x5B, 0x44})),
                new MenuOption(ACTION_SEND_SPECIAL_KEYS_WIN_G, () -> sendKeySequence(
                        (byte) 0, new short[]{0x5B, 0x47})),
                /*
                TODO: Currently not working
                new MenuDialogOption(ACTION_SEND_SPECIAL_KEYS_SHIFT_TAB, () -> sendKeySequence(
                        (byte) 0, new short[]{0xA0, 0x09})),
                */
                new MenuOption(ACTION_CANCEL, null),
        });
    }

    private void showBackMenu() {
        showMenuDialog("Back Menu", new MenuOption[]{
                new MenuOption(ACTION_SEND_SPECIAL_KEYS, () -> showSpecialKeysMenu()),
                new MenuOption(ACTION_DISCONNECT, () -> game.onBackPressed()),
                new MenuOption(ACTION_CANCEL, null),
        });
    }
}
