package com.limelight.customcontrols;

import android.util.DisplayMetrics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the custom-controls JSON layout format and converts it into
 * a flat list of {@link CustomButton} descriptors with resolved pixel positions.
 *
 * Supported JSON fields per button:
 *   name, keycodes[], dynamicX, dynamicY, width, height,
 *   bgColor, strokeColor, strokeWidth, cornerRadius, opacity,
 *   isToggle, isSwipeable, displayInGame, displayInMenu
 *
 * Dynamic position expressions use variables:
 *   ${screen_width}, ${screen_height}, ${width}, ${height},
 *   ${margin}, ${preferred_scale}, ${right}, ${bottom}, ${top}, ${left}
 */
public class CustomJsonParser {

    /** Margin in dp (default for custom control layouts). */
    private static final float MARGIN_DP = 2f;

    // ─── Public data class ───────────────────────────────────────────────────

    public static class CustomButton {
        public String  name;
        public int[]   keycodes;        // raw GLFW / special codes from JSON
        public float   x, y;            // resolved top-left pixel position
        public float   w, h;            // resolved pixel size
        public int     bgColor;
        public int     strokeColor;
        public float   strokeWidth;     // dp
        public float   cornerRadius;    // 0-100 %
        public float   opacity;
        public boolean isToggle;
        public boolean isSwipeable;
        public boolean displayInGame;
        public boolean displayInMenu;
    }

    // ─── Entry point ─────────────────────────────────────────────────────────

    /**
     * Parse a custom controls JSON string and return resolved button descriptors.
     *
     * @param json    Raw JSON string (the whole layout file).
     * @param metrics Display metrics used to convert dp → px and resolve screen vars.
     * @param scale   User preference for button size (100 = default). Pass 100f if unknown.
     * @return List of parsed, position-resolved buttons.
     */
    public static List<CustomButton> parse(String json, DisplayMetrics metrics, float scale)
            throws JSONException {

        JSONObject root = new JSONObject(json);
        JSONArray  arr  = root.optJSONArray("mControlDataList");
        List<CustomButton> result = new ArrayList<>();
        if (arr == null) return result;

        float scaledAt = (float) root.optDouble("scaledAt", 250.0);
        float preferredScale = scale; // from prefs; authors used 250 as their scale

        float screenW  = metrics.widthPixels;
        float screenH  = metrics.heightPixels;
        float marginPx = dpToPx(MARGIN_DP, metrics);

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            CustomButton btn = new CustomButton();

            btn.name         = obj.optString("name", "BTN");
            btn.bgColor      = obj.optInt("bgColor",      0x99000000);
            btn.strokeColor  = obj.optInt("strokeColor",  0xFFFFFFFF);
            btn.strokeWidth  = (float) obj.optDouble("strokeWidth", 0);
            btn.cornerRadius = (float) obj.optDouble("cornerRadius", 0);
            btn.opacity      = (float) obj.optDouble("opacity", 1.0);
            btn.isToggle     = obj.optBoolean("isToggle", false);
            btn.isSwipeable  = obj.optBoolean("isSwipeable", false);
            btn.displayInGame = obj.optBoolean("displayInGame", true);
            btn.displayInMenu = obj.optBoolean("displayInMenu", true);

            // keycodes array
            JSONArray kca = obj.optJSONArray("keycodes");
            if (kca != null) {
                btn.keycodes = new int[kca.length()];
                for (int k = 0; k < kca.length(); k++)
                    btn.keycodes[k] = kca.getInt(k);
            } else {
                btn.keycodes = new int[0];
            }

            // Size: stored in dp, scaled by preferred_scale/scaledAt
            float wDp = (float) obj.optDouble("width",  50);
            float hDp = (float) obj.optDouble("height", 50);
            // Scale from author's scale to user's preferred scale
            float wPx = dpToPx(wDp, metrics) / scaledAt * preferredScale;
            float hPx = dpToPx(hDp, metrics) / scaledAt * preferredScale;
            btn.w = wPx;
            btn.h = hPx;

            // Resolve dynamic position expressions
            String dynX = obj.optString("dynamicX", "0");
            String dynY = obj.optString("dynamicY", "0");

            btn.x = resolveExpr(dynX, screenW, screenH, wPx, hPx, marginPx, preferredScale, metrics);
            btn.y = resolveExpr(dynY, screenW, screenH, wPx, hPx, marginPx, preferredScale, metrics);

            // Clamp to screen
            btn.x = Math.max(0, Math.min(btn.x, screenW - wPx));
            btn.y = Math.max(0, Math.min(btn.y, screenH - hPx));

            result.add(btn);
        }
        return result;
    }

    // ─── Expression resolver ─────────────────────────────────────────────────

    /**
     * Resolve a dynamic position expression like:
     *   "${margin} * 3 + ${width} * 2"
     *   "0.88 * ${screen_width} - ${width}"
     *
     * We substitute variables then evaluate arithmetic with a simple parser.
     */
    private static float resolveExpr(String expr,
                                      float sw, float sh,
                                      float w,  float h,
                                      float margin,
                                      float preferredScale,
                                      DisplayMetrics dm) {
        // Substitute all known variables
        expr = expr.replace("${screen_width}",   Float.toString(sw));
        expr = expr.replace("${screen_height}",  Float.toString(sh));
        expr = expr.replace("${width}",          Float.toString(w));
        expr = expr.replace("${height}",         Float.toString(h));
        expr = expr.replace("${margin}",         Float.toString(margin));
        expr = expr.replace("${preferred_scale}",Float.toString(preferredScale));
        expr = expr.replace("${right}",          Float.toString(sw - w));
        expr = expr.replace("${bottom}",         Float.toString(sh - h));
        expr = expr.replace("${top}",            "0");
        expr = expr.replace("${left}",           "0");

        // Resolve px(N) calls — convert dp to px
        expr = resolvePxCalls(expr, dm);

        try {
            return evalArith(expr.trim());
        } catch (Exception e) {
            return 0f;
        }
    }

    /** Replace all occurrences of px(N) with the pixel value. */
    private static String resolvePxCalls(String expr, DisplayMetrics dm) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < expr.length()) {
            int px = expr.indexOf("px(", i);
            if (px < 0) { sb.append(expr.substring(i)); break; }
            sb.append(expr, i, px);
            int close = expr.indexOf(")", px + 3);
            if (close < 0) { sb.append(expr.substring(px)); break; }
            String inner = expr.substring(px + 3, close).trim();
            try {
                float val = Float.parseFloat(inner);
                sb.append(dpToPx(val, dm));
            } catch (NumberFormatException e) {
                sb.append("0");
            }
            i = close + 1;
        }
        return sb.toString();
    }

    /**
     * Minimal arithmetic evaluator supporting +, -, *, / and parentheses.
     * Handles negative numbers and float literals.
     */
    private static float evalArith(String expr) {
        return new ArithParser(expr).parse();
    }

    private static class ArithParser {
        private final String s;
        private int pos;

        ArithParser(String s) { this.s = s.replaceAll("\\s+", ""); this.pos = 0; }

        float parse() { float v = parseExpr(); return v; }

        // expr = term (('+' | '-') term)*
        private float parseExpr() {
            float v = parseTerm();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '+') { pos++; v += parseTerm(); }
                else if (c == '-') { pos++; v -= parseTerm(); }
                else break;
            }
            return v;
        }

        // term = factor (('*' | '/') factor)*
        private float parseTerm() {
            float v = parseFactor();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '*') { pos++; v *= parseFactor(); }
                else if (c == '/') { pos++; float d = parseFactor(); v = d != 0 ? v / d : 0; }
                else break;
            }
            return v;
        }

        // factor = '-' factor | '(' expr ')' | number
        private float parseFactor() {
            if (pos < s.length() && s.charAt(pos) == '-') {
                pos++;
                return -parseFactor();
            }
            if (pos < s.length() && s.charAt(pos) == '(') {
                pos++; // consume '('
                float v = parseExpr();
                if (pos < s.length() && s.charAt(pos) == ')') pos++;
                return v;
            }
            return parseNumber();
        }

        private float parseNumber() {
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.'))
                pos++;
            if (pos == start) return 0f;
            try { return Float.parseFloat(s.substring(start, pos)); }
            catch (NumberFormatException e) { return 0f; }
        }
    }

    // ─── GLFW → Moonlight VK conversion ─────────────────────────────────────

    /**
     * Convert a GLFW keycode (as stored in the layout JSON) to a
     * Moonlight VK keycode suitable for NvConnection.sendKeyboardInput().
     *
     * GLFW uses ASCII for printable keys (A=65, W=87 etc.).
     * Special keys need manual mapping.
     */
    public static short glfwToMoonlightVk(int glfw) {
        // Printable ASCII range: letters, digits, symbols — pass through directly
        if (glfw >= 32 && glfw <= 126) return (short) glfw;

        // GLFW special key codes → Java VK codes (which moonlight uses)
        switch (glfw) {
            case 256: return 27;   // GLFW_KEY_ESCAPE       → VK_ESCAPE
            case 257: return 10;   // GLFW_KEY_ENTER        → VK_ENTER
            case 258: return 9;    // GLFW_KEY_TAB          → VK_TAB
            case 259: return 8;    // GLFW_KEY_BACKSPACE    → VK_BACK_SPACE
            case 260: return 155;  // GLFW_KEY_INSERT       → VK_INSERT
            case 261: return 127;  // GLFW_KEY_DELETE       → VK_DELETE
            case 262: return 39;   // GLFW_KEY_RIGHT        → VK_RIGHT
            case 263: return 37;   // GLFW_KEY_LEFT         → VK_LEFT
            case 264: return 40;   // GLFW_KEY_DOWN         → VK_DOWN
            case 265: return 38;   // GLFW_KEY_UP           → VK_UP
            case 266: return 33;   // GLFW_KEY_PAGE_UP      → VK_PAGE_UP
            case 267: return 34;   // GLFW_KEY_PAGE_DOWN    → VK_PAGE_DOWN
            case 268: return 36;   // GLFW_KEY_HOME         → VK_HOME
            case 269: return 35;   // GLFW_KEY_END          → VK_END
            // F keys: GLFW 290=F1 … 301=F12, VK 112=F1 … 123=F12
            case 290: case 291: case 292: case 293: case 294: case 295:
            case 296: case 297: case 298: case 299: case 300: case 301:
                return (short)(112 + (glfw - 290));
            // Modifiers
            case 340: return 16;   // GLFW_KEY_LEFT_SHIFT   → VK_SHIFT
            case 341: return 17;   // GLFW_KEY_LEFT_CONTROL → VK_CONTROL
            case 342: return 18;   // GLFW_KEY_LEFT_ALT     → VK_ALT
            case 344: return 16;   // GLFW_KEY_RIGHT_SHIFT  → VK_SHIFT
            case 345: return 17;   // GLFW_KEY_RIGHT_CONTROL→ VK_CONTROL
            case 346: return 18;   // GLFW_KEY_RIGHT_ALT    → VK_ALT
            case 343: case 347: return 157; // Super/Meta  → VK_META
            default:  return 0;
        }
    }

    /**
     * Returns true if this keycode is a "special" button
     * (keyboard toggle, mouse buttons etc.) rather than a keyboard key.
     * Special codes are negative in the JSON.
     */
    public static boolean isSpecialCode(int code) { return code < 0; }

    // ─── Utility ─────────────────────────────────────────────────────────────

    private static float dpToPx(float dp, DisplayMetrics dm) {
        return dp * dm.density;
    }
}

