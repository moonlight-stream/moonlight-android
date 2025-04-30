/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import static com.limelight.GameMenu.getModifier;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.widget.Toast;
import androidx.preference.PreferenceManager;

import com.limelight.GameMenu;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.KeyMapper;

import org.jcodec.common.ArrayUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KeyBoardControllerConfigurationLoader {
    public static final String OSC_PREFERENCE = "keyboard_axi_list";
    public static final String OSC_PREFERENCE_VALUE = "OSC_Keyboard";

    private static final Set<Integer> MODIFIER_KEY_CODES = new HashSet<>();
    static {
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_ALT_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_ALT_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_CTRL_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_CTRL_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_SHIFT_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_SHIFT_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_META_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_META_RIGHT);
    }

    public static boolean isModifierKey(int keyCode) {
        return MODIFIER_KEY_CODES.contains(keyCode);
    }

    // The default controls are specified using a grid of 128*72 cells at 16:9
    private static int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }

    private static int screenScaleSwicth(int result, int height) {
        return result * 72 / height;
    }

    private static KeyboardDigitalPadButton createDiaitalPadButton(String elementId, int keyCodeLeft, int keyCodeRight, int keyCodeUp, int keyCodeDown, final KeyBoardController controller, final Context context) {
        KeyboardDigitalPadButton button = new KeyboardDigitalPadButton(controller, context, elementId);
        button.addDigitalPadListener(new KeyboardDigitalPadButton.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeLeft);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeLeft);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeRight);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeRight);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_UP) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeUp);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeUp);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeDown);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeDown);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
            }
        });
        return button;
    }


    private static KeyBoardAnalogStickButton createKeyBoardAnalogStickButton(final KeyBoardController controller, String elementId, final Context context, int[] keylist) {

        KeyBoardAnalogStickButton analogStick = new KeyBoardAnalogStickButton(controller, elementId, context, keylist);
        analogStick.setListener(new KeyBoardAnalogStickButton.KeyBoardAnalogStickListener() {
            @Override
            public void onkeyEvent(int code, boolean isPress) {
                KeyEvent keyEvent = new KeyEvent(isPress ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code);
                keyEvent.setSource(2);
                controller.sendKeyEvent(keyEvent);
            }
        });

        return analogStick;

    }

    private static KeyBoardAnalogStickButtonFree createKeyBoardAnalogStickButton2(final KeyBoardController controller, String elementId, final Context context, int[] keylist) {

        KeyBoardAnalogStickButtonFree analogStick = new KeyBoardAnalogStickButtonFree(controller, elementId, context, keylist);
        analogStick.setListener(new KeyBoardAnalogStickButtonFree.KeyBoardAnalogStickListener() {
            @Override
            public void onkeyEvent(int code, boolean isPress) {
                KeyEvent keyEvent = new KeyEvent(isPress ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code);
                keyEvent.setSource(2);
                controller.sendKeyEvent(keyEvent);
            }
        });

        return analogStick;

    }

    private static KeyBoardDigitalButton createDigitalButton(
            final String elementId,
            final int keyShort,
            final int type,
            final int layer,
            final String text,
            final int icon,
            final boolean sticky,
            final KeyBoardController controller,
            final Context context) {
        KeyBoardDigitalButton button = new KeyBoardDigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);

        if(elementId.startsWith("m_s_")||elementId.startsWith("key_s_")){
            button.setEnableSwitchDown(true);
        }

        if (sticky) {
            button.addDigitalButtonListener(new KeyBoardDigitalButton.DigitalButtonListener() {
                @Override
                public void onClick() {
                    if (button.isSticky()) {
                        button.setSticky(false);
                        return;
                    }
                    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyShort);
                    keyEvent.setSource(type);
                    controller.sendKeyEvent(keyEvent);
                }

                @Override
                public void onLongClick() {
                    button.setSticky(true);
                    controller.vibrate(-1);
                }

                @Override
                public void onRelease() {
                    if (button.isSticky()) {
                        return;
                    }
                    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, keyShort);
                    keyEvent.setSource(type);
                    controller.sendKeyEvent(keyEvent);
                }
            });
        } else {
            button.addDigitalButtonListener(new KeyBoardDigitalButton.DigitalButtonListener() {
                @Override
                public void onClick() {
                    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyShort);
                    keyEvent.setSource(type);
                    controller.sendKeyEvent(keyEvent);
                }

                @Override
                public void onLongClick() {
                }

                @Override
                public void onRelease() {
                    KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, keyShort);
                    keyEvent.setSource(type);
                    controller.sendKeyEvent(keyEvent);
                }
            });
        }

        return button;
    }

    private static KeyBoardDigitalButton createCustomButton(
            String elementId,
            final short[] keys,
            final int layer,
            final String text,
            final int icon,
            final boolean sticky,
            final KeyBoardController controller,
            final NvConnection conn,
            final Context context
    ) {
        KeyBoardDigitalButton button = new KeyBoardDigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);

        final byte[] modifier = {(byte) 0};

        if (sticky) {
            button.addDigitalButtonListener(new KeyBoardDigitalButton.DigitalButtonListener() {
                @Override
                public void onClick() {
                    if (button.isSticky()) {
                        button.setSticky(false);
                        return;
                    }
                    controller.vibrate(KeyEvent.ACTION_DOWN);
                    for (short key : keys) {
                        conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);
                        modifier[0] |= getModifier(key);
                    }
                }

                @Override
                public void onLongClick() {
                    button.setSticky(true);
                    controller.vibrate(-1);
                }

                @Override
                public void onRelease() {
                    if (button.isSticky()) {
                        return;
                    }
                    controller.vibrate(KeyEvent.ACTION_UP);
                    for (int pos = keys.length - 1; pos >= 0; pos--) {
                        short key = keys[pos];
                        modifier[0] &= (byte) ~getModifier(key);
                        conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
                    }
                }
            });
        } else {
            button.addDigitalButtonListener(new KeyBoardDigitalButton.DigitalButtonListener() {
                @Override
                public void onClick() {
                    controller.vibrate(KeyEvent.ACTION_DOWN);
                    for (short key : keys) {
                        conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);
                        modifier[0] |= getModifier(key);
                    }
                }

                @Override
                public void onLongClick() {
                }

                @Override
                public void onRelease() {
                    controller.vibrate(KeyEvent.ACTION_UP);
                    for (int pos = keys.length - 1; pos >= 0; pos--) {
                        short key = keys[pos];
                        modifier[0] &= (byte) ~getModifier(key);
                        conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
                    }
                }
            });
        }

        return button;
    }


    private static KeyBoardTouchPadButton createDigitalTouchButton(
            final String elementId,
            final int keyShort,
            final int type,
            final int layer,
            final String text,
            final int icon,
            final KeyBoardController controller,
            final Context context) {
        KeyBoardTouchPadButton button = new KeyBoardTouchPadButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);
        button.addDigitalButtonListener(new KeyBoardTouchPadButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                int code=keyShort==9?3:1;
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, code);
                keyEvent.setSource(type);
                controller.sendKeyEvent(keyEvent);
            }

            @Override
            public void onLongClick() {
            }

            @Override
            public void onMove(int x, int y) {
                controller.sendMouseMove(x,y);
            }

            @Override
            public void onRelease() {
                int code=keyShort==9?3:1;
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, code);
                keyEvent.setSource(type);
                controller.sendKeyEvent(keyEvent);

            }
        });

        return button;
    }

    public static void createDefaultLayout(final KeyBoardController controller, final Context context, final NvConnection conn) {

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        int height = screen.heightPixels;

        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;

        int BUTTON_SIZE = 10;

        int w = screenScale(BUTTON_SIZE, height);

        int maxW = screen.widthPixels / 18;

        if (w > maxW) {
            BUTTON_SIZE = screenScaleSwicth(maxW, height);
            w = screenScale(BUTTON_SIZE, height);
        }

        String result = "";
        try {
            InputStream is = context.getAssets().open("config/keyboard.json");
            int lenght = is.available();
            byte[] buffer = new byte[lenght];
            is.read(buffer);
            result = new String(buffer, "utf8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (TextUtils.isEmpty(result)) {
            return;
        }
        try {
            JSONObject jsonObject = new JSONObject(result);
            JSONObject jsonObject1 = jsonObject.getJSONObject("data");

            JSONArray keystrokeList = jsonObject1.getJSONArray("keystroke");
            JSONArray dpadList = jsonObject1.getJSONArray("dpad");
            JSONArray rockerList = jsonObject1.getJSONArray("rocker");
            JSONArray mouseList = jsonObject1.getJSONArray("mouse");

            //十字键
            for (int i = 0; i < dpadList.length(); i++) {
                JSONObject obj = dpadList.getJSONObject(i);
                String code = obj.optString("elementId");
                int keyCodeLeft = obj.optInt("leftCode");
                int keyCodeRight = obj.optInt("rightCode");
                int keyCodeUp = obj.optInt("upCode");
                int keyCodeDown = obj.optInt("downCode");
                controller.addElement(createDiaitalPadButton(code, keyCodeLeft, keyCodeRight, keyCodeUp, keyCodeDown, controller, context),
                        screenScale(92, height) + rightDisplacement,
                        screenScale(41, height),
                        (int) (w * 2.5), (int) (w * 2.5)
                );
            }
            //摇杆
            for (int i = 0; i < rockerList.length(); i++) {
                JSONObject obj = rockerList.getJSONObject(i);
                String code = obj.optString("elementId");
                int keyCodeLeft = obj.optInt("leftCode");
                int keyCodeRight = obj.optInt("rightCode");
                int keyCodeUp = obj.optInt("upCode");
                int keyCodeDown = obj.optInt("downCode");
                int keyCodeMiddle = obj.optInt("middleCode");
                int[] keys = new int[]{keyCodeUp, keyCodeDown, keyCodeLeft, keyCodeRight, keyCodeMiddle};

                if(config.enableNewAnalogStick){
                    controller.addElement(createKeyBoardAnalogStickButton2(controller, code, context, keys),
                            screenScale(4, height),
                            screenScale(41, height),
                            (int) (w * 2.5), (int) (w * 2.5)
                    );
                }else{
                    controller.addElement(createKeyBoardAnalogStickButton(controller, code, context, keys),
                            screenScale(4, height),
                            screenScale(41, height),
                            (int) (w * 2.5), (int) (w * 2.5)
                    );
                }
            }

            //鼠标按键
            for (int i = 0; i < mouseList.length(); i++) {
                JSONObject obj = mouseList.getJSONObject(i);
                obj.put("type", 1);
                keystrokeList.put(obj);
            }

            double buttonSum = 14.0;

            int i;

            //普通按键
            for (i = 0; i < keystrokeList.length(); i++) {
                JSONObject obj = keystrokeList.getJSONObject(i);

                String name = obj.optString("name");

                int type = obj.optInt("type");

                int code = obj.optInt("code");

                int switchButton = obj.optInt("switchButton");

                String elementId = type == 0 ? "key_" + code : "m_" + code;

                if(switchButton == 1){
                    elementId = type == 0 ? "key_s_" + code : "m_s_" + code;
                }

                int lastIndex = (int) (i / buttonSum);

                int x = screenScale(1 + (int) (i % buttonSum) * BUTTON_SIZE, height);

                int y = screenScale(BUTTON_SIZE + lastIndex * BUTTON_SIZE, height);

                if(TextUtils.equals("m_9", elementId)||TextUtils.equals("m_10", elementId)||TextUtils.equals("m_11", elementId)){
                    controller.addElement(createDigitalTouchButton(elementId, code, type, 1, name, -1, controller, context),
                            x, y,
                            w, w
                    );
                }else{
                    controller.addElement(createDigitalButton(elementId, code, type, 1, name, -1, config.stickyModifierKey && isModifierKey(code), controller, context),
                            x, y,
                            w, w
                    );
                }
                LimeLog.info("x:" + x + ",y:" + y + ",W&H:" + w + "," + screenScale(BUTTON_SIZE, height));
            }

            // Custom keys
            SharedPreferences preferences = context.getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE);
            String value = preferences.getString(GameMenu.KEY_NAME,"");

            if(!TextUtils.isEmpty(value)){
                try {
                    JSONObject object = new JSONObject(value);
                    JSONArray keyMapArr = object.optJSONArray("data");
                    if(keyMapArr != null&&keyMapArr.length()>0){
                        for (int idx = 0; idx < keyMapArr.length(); idx++) {
                            JSONObject keyMap = keyMapArr.getJSONObject(idx);
                            String id = keyMap.optString("id", Integer.toString(idx));
                            String name = keyMap.optString("name");
                            JSONArray keySequence = keyMap.getJSONArray("keys");
                            short[] vkKeyCodes = new short[keySequence.length()];
                            for (int j = 0; j < keySequence.length(); j++) {
                                String code = keySequence.getString(j);
                                int keycode;
                                if (code.startsWith("0x")) {
                                    keycode = Integer.parseInt(code.substring(2), 16);
                                } else if (code.startsWith("VK_")) {
                                    Field vkCodeField = KeyMapper.class.getDeclaredField(code);
                                    keycode = vkCodeField.getInt(null);
                                } else {
                                    throw new Exception("Unknown key code: " + code);
                                }
                                vkKeyCodes[j] = (short) keycode;
                            }

                            boolean sticky = keyMap.optBoolean("sticky", false);

                            int lastIndex = (int) ((idx + i) / buttonSum);

                            int x = screenScale(1 + (int) ((idx + i) % buttonSum) * BUTTON_SIZE, height);
                            int y = screenScale(BUTTON_SIZE + lastIndex * BUTTON_SIZE, height);

                            controller.addElement(createCustomButton("custom_" + id, vkKeyCodes, 1, name, -1, sticky, controller, conn, context),
                                x, y,
                                w, w
                            );
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(context,context.getString(R.string.wrong_import_format),Toast.LENGTH_SHORT).show();
                }
            }

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        controller.setOpacity(config.oscOpacity);
    }

    public static void saveProfile(final KeyBoardController controller,
                                   final Context context) {
        String name = PreferenceManager.getDefaultSharedPreferences(context).getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE);

        SharedPreferences.Editor prefEditor = context.getSharedPreferences(name, Activity.MODE_PRIVATE).edit();

        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            String prefKey = "" + element.elementId;
            try {
                prefEditor.putString(prefKey, element.getConfiguration().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefEditor.apply();
    }

    public static void loadFromPreferences(final KeyBoardController controller, final Context context) {
        String name = PreferenceManager.getDefaultSharedPreferences(context).getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE);

        SharedPreferences pref = context.getSharedPreferences(name, Activity.MODE_PRIVATE);

        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            String prefKey = "" + element.elementId;

            String jsonConfig = pref.getString(prefKey, null);
            if (jsonConfig != null) {
                try {
                    element.loadConfiguration(new JSONObject(jsonConfig));
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            }
        }
    }
}
