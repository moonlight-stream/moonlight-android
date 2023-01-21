package com.limelight;

import android.app.AlertDialog;
import android.os.Handler;
import android.widget.ArrayAdapter;

import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

/**
 * Provide options for ongoing Game Stream.
 * <p>
 * Shown on back action in game activity.
 */
public class GameMenu {

    private static final long KEY_UP_DELAY = 25;

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

    private void sendKeys(short[] keys) {
        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, (byte) 0);
        }

        new Handler().postDelayed((() -> {

            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];

                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, (byte) 0);
            }
        }), KEY_UP_DELAY);
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
                new MenuOption(getString(R.string.game_menu_send_keys_esc),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE})),
                new MenuOption(getString(R.string.game_menu_send_keys_f11),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_F11})),
                new MenuOption(getString(R.string.game_menu_send_keys_win),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN})),
                new MenuOption(getString(R.string.game_menu_send_keys_win_d),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D})),
                new MenuOption(getString(R.string.game_menu_send_keys_win_g),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G})),
                new MenuOption(getString(R.string.game_menu_send_keys_shift_tab),
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB})),
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
