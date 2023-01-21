package com.limelight;

import android.app.AlertDialog;
import android.widget.ArrayAdapter;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

/**
 * Provide options for ongoing Game Stream.
 *
 * Shown on back action in game activity.
 */
public class GameMenu {

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

    public GameMenu(Game game, NvConnection conn) {
        this.game = game;
        this.conn = conn;

        showMenu();
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
        showMenuDialog(getString(R.string.game_menu_send_keys), new MenuOption[]{
                new MenuOption(getString(R.string.game_menu_send_keys_esc), () -> sendKeySequence(
                        (byte) 0, new short[]{0x18})),
                new MenuOption(getString(R.string.game_menu_send_keys_f11), () -> sendKeySequence(
                        (byte) 0, new short[]{0x7a})),
                new MenuOption(getString(R.string.game_menu_send_keys_win), () -> sendKeySequence(
                        (byte) 0, new short[]{0x5B})),
                new MenuOption(getString(R.string.game_menu_send_keys_win_d), () -> sendKeySequence(
                        (byte) 0, new short[]{0x5B, 0x44})),
                new MenuOption(getString(R.string.game_menu_send_keys_win_g), () -> sendKeySequence(
                        (byte) 0, new short[]{0x5B, 0x47})),
                /*
                // TODO: Currently not working
                new MenuDialogOption(getString(R.string.game_menu_send_keys_shift_tab), () -> sendKeySequence(
                        (byte) 0, new short[]{0xA0, 0x09})),
                */
                new MenuOption(getString(R.string.game_menu_cancel), null),
        });
    }

    private void showMenu() {
        showMenuDialog("Game Menu", new MenuOption[]{
                new MenuOption(getString(R.string.game_menu_send_keys), () -> showSpecialKeysMenu()),
                new MenuOption(getString(R.string.game_menu_disconnect), () -> game.onBackPressed()),
                new MenuOption(getString(R.string.game_menu_cancel), null),
        });
    }
}
