package com.limelight.binding.input.genshin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Toast;

import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen multi-touch Genshin Impact Mobile style overlay layered above StreamView.
 *
 * - Supports 5+ simultaneous touch pointers independently tracked by pointerId:
 *   e.g. left joystick + right camera drag + Attack hold + E/Q + character switch.
 * - Blocks all touch events from leaking to Moonlight's AbsoluteTouchContext/RelativeTouchContext.
 * - Supports in-game Edit Mode (Move, Resize, per-element Opacity, Reset, Save to GenshinProfile).
 * - Respects display cutouts/notches and navigation bar safe areas.
 */
public class GenshinOverlayView extends View {

    public enum EditMode {
        NONE,
        MOVE,
        RESIZE
    }

    private enum PointerRole {
        BUTTON,
        JOYSTICK,
        CAMERA,
        EDIT_ELEMENT,
        TOOLBAR
    }

    private static class PointerBinding {
        final PointerRole role;
        final GenshinButton button;
        float lastX;
        float lastY;
        Runnable longPressRunnable;

        PointerBinding(PointerRole role, GenshinButton button, float x, float y) {
            this.role = role;
            this.button = button;
            this.lastX = x;
            this.lastY = y;
        }
    }

    private final GenshinInputHandler inputHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private GenshinProfile profile;
    private final List<GenshinButton> buttons = new ArrayList<>();
    private GenshinJoystick joystick;

    // Active pointer tracking (supports 10+ simultaneous fingers)
    private final SparseArray<PointerBinding> pointerBindings = new SparseArray<>();
    private int cameraPointerId = -1;

    // Display safe area (accounts for notch / DisplayCutout / nav bar)
    private final RectF safeRect = new RectF();
    private int insetLeft = 0;
    private int insetTop = 0;
    private int insetRight = 0;
    private int insetBottom = 0;

    // Visual & behavior configuration
    private int globalOpacity = 75;
    private float globalSizeScale = 1.0f;
    private boolean showSecondaryRow = true;

    // Small in-game utility pills (top-center)
    private final RectF btnEditRect = new RectF();
    private final RectF btnCollapseRect = new RectF();

    // Edit Mode state & floating toolbar rects
    private EditMode editMode = EditMode.NONE;
    private Object selectedEditTarget = null; // GenshinButton or GenshinJoystick
    private float editStartTouchX, editStartTouchY;
    private float editStartCx, editStartCy, editStartSize;

    private final RectF tbMoveRect = new RectF();
    private final RectF tbResizeRect = new RectF();
    private final RectF tbOpMinusRect = new RectF();
    private final RectF tbOpPlusRect = new RectF();
    private final RectF tbResetRect = new RectF();
    private final RectF tbDoneRect = new RectF();

    // Reusable Paints (zero allocations in onDraw)
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GenshinOverlayView(Context context, GenshinInputHandler inputHandler, PreferenceConfiguration prefConfig) {
        super(context);
        this.inputHandler = inputHandler;
        setFocusable(false);
        setFocusableInTouchMode(false);
        applyPreferences(prefConfig);
    }

    public void applyPreferences(PreferenceConfiguration prefConfig) {
        if (prefConfig == null) {
            return;
        }
        this.globalOpacity = Math.max(15, Math.min(100, prefConfig.genshinOpacity));
        this.globalSizeScale = Math.max(0.6f, Math.min(1.5f, prefConfig.genshinButtonScale / 100f));
        this.showSecondaryRow = prefConfig.genshinShowSecondaryBar;

        inputHandler.setCameraSensitivity(prefConfig.genshinCameraSensitivity / 100f);
        inputHandler.setSprintToggleMode(prefConfig.genshinSprintToggle);
        inputHandler.setSprintUseRmb(prefConfig.genshinSprintUseRmb);

        loadProfile(prefConfig.genshinProfile);
    }

    public void loadProfile(String profileName) {
        releaseAllInputs();
        this.profile = GenshinProfile.load(getContext(), profileName);
        rebuildElementsFromProfile();
        updateGeometry();
        invalidate();
    }

    private void rebuildElementsFromProfile() {
        buttons.clear();
        joystick = new GenshinJoystick(profile.getLayout(GenshinProfile.ELEMENT_JOYSTICK_ID));
        for (GenshinMapping.Action action : GenshinMapping.Action.values()) {
            GenshinProfile.ElementLayout el = profile.getLayout(action.id);
            if (el != null) {
                GenshinButton btn = new GenshinButton(action, el);
                btn.visible = !action.secondary || showSecondaryRow;
                buttons.add(btn);
            }
        }
    }

    public void releaseAllInputs() {
        // Cancel any pending long-press runnables
        for (int i = 0; i < pointerBindings.size(); i++) {
            PointerBinding b = pointerBindings.valueAt(i);
            if (b.longPressRunnable != null) {
                mainHandler.removeCallbacks(b.longPressRunnable);
                b.longPressRunnable = null;
            }
        }
        pointerBindings.clear();
        cameraPointerId = -1;

        for (GenshinButton btn : buttons) {
            btn.activePointerId = -1;
            btn.pressed = false;
            btn.toggledActive = false;
            btn.longPressTriggered = false;
        }
        if (joystick != null) {
            joystick.resetToRest();
        }
        inputHandler.releaseAll();
        invalidate();
    }

    public boolean isInEditMode() {
        return editMode != EditMode.NONE;
    }

    public void setEditMode(EditMode mode) {
        if (this.editMode == mode) {
            return;
        }
        releaseAllInputs();
        this.editMode = mode;
        if (mode == EditMode.NONE) {
            selectedEditTarget = null;
        } else if (selectedEditTarget == null && !buttons.isEmpty()) {
            selectedEditTarget = buttons.get(0);
        }
        invalidate();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    insetLeft = cutout.getSafeInsetLeft();
                    insetTop = cutout.getSafeInsetTop();
                    insetRight = cutout.getSafeInsetRight();
                    insetBottom = cutout.getSafeInsetBottom();
                } else {
                    insetLeft = insetTop = insetRight = insetBottom = 0;
                }
            }
            updateGeometry();
            invalidate();
        }
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        releaseAllInputs();
        updateGeometry();
    }

    private void updateGeometry() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        // Small safe margin even on notchless screens so edge buttons aren't clipped
        float padX = Math.max(insetLeft, w * 0.012f);
        float padRight = Math.max(insetRight, w * 0.012f);
        float padTop = Math.max(insetTop, h * 0.015f);
        float padBottom = Math.max(insetBottom, h * 0.015f);

        safeRect.set(padX, padTop, Math.max(padX + 100f, w - padRight), Math.max(padTop + 100f, h - padBottom));

        if (joystick != null) {
            joystick.updatePixelBounds(safeRect, globalSizeScale);
        }
        for (GenshinButton btn : buttons) {
            btn.visible = !btn.action.secondary || showSecondaryRow || editMode != EditMode.NONE;
            btn.updatePixelBounds(safeRect, globalSizeScale);
        }

        // Top-center compact utility pills (Edit + Hide/Show Secondary)
        float pillH = Math.max(32f, safeRect.height() * 0.068f);
        float pillW = Math.max(66f, safeRect.height() * 0.14f);
        float centerX = safeRect.centerX();
        float topY = safeRect.top + pillH * 0.15f;
        btnCollapseRect.set(centerX - pillW - 6f, topY, centerX - 6f, topY + pillH);
        btnEditRect.set(centerX + 6f, topY, centerX + pillW + 6f, topY + pillH);

        // Edit mode toolbar centered near top
        float tbH = Math.max(38f, safeRect.height() * 0.085f);
        float tbW = Math.max(64f, safeRect.width() * 0.095f);
        float gap = 8f;
        float totalW = tbW * 6 + gap * 5;
        float startX = safeRect.centerX() - totalW * 0.5f;
        float tbY = safeRect.top + 8f;

        tbMoveRect.set(startX, tbY, startX + tbW, tbY + tbH);
        tbResizeRect.set(tbMoveRect.right + gap, tbY, tbMoveRect.right + gap + tbW, tbY + tbH);
        tbOpMinusRect.set(tbResizeRect.right + gap, tbY, tbResizeRect.right + gap + tbW, tbY + tbH);
        tbOpPlusRect.set(tbOpMinusRect.right + gap, tbY, tbOpMinusRect.right + gap + tbW, tbY + tbH);
        tbResetRect.set(tbOpPlusRect.right + gap, tbY, tbOpPlusRect.right + gap + tbW, tbY + tbH);
        tbDoneRect.set(tbResetRect.right + gap, tbY, tbResetRect.right + gap + tbW, tbY + tbH);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (safeRect.width() <= 0 || safeRect.height() <= 0) {
            return;
        }

        boolean editing = (editMode != EditMode.NONE);

        // Subtle edit mode tint & safe-area guide
        if (editing) {
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb(65, 10, 14, 24));
            canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2f);
            strokePaint.setColor(Color.argb(120, 120, 210, 255));
            canvas.drawRect(safeRect, strokePaint);
        }

        if (joystick != null) {
            joystick.draw(canvas, fillPaint, strokePaint, textPaint,
                    globalOpacity, editing, selectedEditTarget == joystick);
        }

        boolean sprintUseRmb = inputHandler.isSprintUseRmb();
        for (GenshinButton btn : buttons) {
            if (btn.action == GenshinMapping.Action.SPRINT) {
                btn.toggledActive = inputHandler.isSprintToggleMode() && inputHandler.isSprintToggledOn();
            }
            btn.draw(canvas, fillPaint, strokePaint, textPaint,
                    globalOpacity, editing, selectedEditTarget == btn, sprintUseRmb);
        }

        if (editing) {
            drawEditToolbar(canvas);
        } else {
            drawPillButton(canvas, btnCollapseRect, showSecondaryRow ? "BAR:ON" : "BAR:OFF", false);
            drawPillButton(canvas, btnEditRect, "EDIT", false);
        }
    }

    private void drawEditToolbar(Canvas canvas) {
        drawPillButton(canvas, tbMoveRect, "MOVE", editMode == EditMode.MOVE);
        drawPillButton(canvas, tbResizeRect, "SIZE", editMode == EditMode.RESIZE);
        drawPillButton(canvas, tbOpMinusRect, "OP-", false);
        drawPillButton(canvas, tbOpPlusRect, "OP+", false);
        drawPillButton(canvas, tbResetRect, "RESET", false);
        drawPillButton(canvas, tbDoneRect, "SAVE", true);
    }

    private void drawPillButton(Canvas canvas, RectF rect, String label, boolean highlighted) {
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(highlighted ? Color.argb(215, 45, 165, 115) : Color.argb(150, 20, 25, 36));
        float r = rect.height() * 0.4f;
        canvas.drawRoundRect(rect, r, r, fillPaint);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);
        strokePaint.setColor(highlighted ? Color.argb(255, 220, 255, 235) : Color.argb(170, 190, 205, 230));
        canvas.drawRoundRect(rect, r, r, strokePaint);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(rect.height() * 0.42f);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(label, rect.centerX(), rect.centerY() + textPaint.getTextSize() * 0.35f, textPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            requestUnbufferedDispatch(event);
        }

        if (editMode != EditMode.NONE) {
            return handleEditTouch(event);
        }

        int actionMasked = event.getActionMasked();
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = event.getActionIndex();
                int pointerId = event.getPointerId(idx);
                float x = event.getX(idx);
                float y = event.getY(idx);
                handlePointerDown(pointerId, x, y);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                int count = event.getPointerCount();
                for (int i = 0; i < count; i++) {
                    int pointerId = event.getPointerId(i);
                    PointerBinding binding = pointerBindings.get(pointerId);
                    if (binding == null) {
                        continue;
                    }
                    float x = event.getX(i);
                    float y = event.getY(i);
                    if (binding.role == PointerRole.JOYSTICK && joystick != null && joystick.activePointerId == pointerId) {
                        joystick.onPointerMove(x, y);
                        inputHandler.updateJoystick(joystick.normX, joystick.normY);
                    } else if (binding.role == PointerRole.CAMERA && cameraPointerId == pointerId) {
                        float dx = x - binding.lastX;
                        float dy = y - binding.lastY;
                        binding.lastX = x;
                        binding.lastY = y;
                        inputHandler.sendCameraDelta(dx, dy);
                    }
                }
                invalidate();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = event.getActionIndex();
                int pointerId = event.getPointerId(idx);
                handlePointerUp(pointerId, false);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                releaseAllInputs();
                break;
            }
        }
        // Always consume touches so nothing leaks to Moonlight's default touch/mouse handlers
        return true;
    }

    private void handlePointerDown(int pointerId, float x, float y) {
        // 1. Top utility pills (Collapse / Edit)
        if (btnEditRect.contains(x, y)) {
            pointerBindings.put(pointerId, new PointerBinding(PointerRole.TOOLBAR, null, x, y));
            setEditMode(EditMode.MOVE);
            updateGeometry();
            invalidate();
            return;
        }
        if (btnCollapseRect.contains(x, y)) {
            pointerBindings.put(pointerId, new PointerBinding(PointerRole.TOOLBAR, null, x, y));
            showSecondaryRow = !showSecondaryRow;
            updateGeometry();
            invalidate();
            return;
        }

        // 2. Check buttons (highest priority)
        GenshinButton hitBtn = findButtonAt(x, y);
        if (hitBtn != null) {
            if (hitBtn.activePointerId != -1) {
                return;
            }
            hitBtn.activePointerId = pointerId;
            hitBtn.pressed = true;
            hitBtn.longPressTriggered = false;

            final PointerBinding binding = new PointerBinding(PointerRole.BUTTON, hitBtn, x, y);
            pointerBindings.put(pointerId, binding);

            if (hitBtn.action.inputType == GenshinMapping.InputType.CHARACTER_SLOT) {
                final int slot = hitBtn.action.characterSlot;
                Runnable longPress = new Runnable() {
                    @Override
                    public void run() {
                        if (hitBtn.pressed && hitBtn.activePointerId == pointerId) {
                            hitBtn.longPressTriggered = true;
                            inputHandler.triggerCharacterBurst(slot);
                            invalidate();
                        }
                    }
                };
                binding.longPressRunnable = longPress;
                mainHandler.postDelayed(longPress, GenshinMapping.CHAR_BURST_LONG_PRESS_MS);
            } else {
                inputHandler.onActionDown(hitBtn.action);
            }
            invalidate();
            return;
        }

        // 3. Left half empty area -> floating joystick (if not already held)
        float midX = safeRect.centerX();
        if (x < midX && joystick != null && joystick.activePointerId == -1) {
            joystick.onPointerDown(pointerId, x, y, safeRect);
            pointerBindings.put(pointerId, new PointerBinding(PointerRole.JOYSTICK, null, x, y));
            invalidate();
            return;
        }

        // 4. Right half empty area -> relative camera drag
        if (x >= midX && cameraPointerId == -1) {
            cameraPointerId = pointerId;
            inputHandler.resetCameraRemainder();
            pointerBindings.put(pointerId, new PointerBinding(PointerRole.CAMERA, null, x, y));
        }
    }

    private void handlePointerUp(int pointerId, boolean cancelled) {
        PointerBinding binding = pointerBindings.get(pointerId);
        if (binding == null) {
            return;
        }
        pointerBindings.remove(pointerId);

        if (binding.longPressRunnable != null) {
            mainHandler.removeCallbacks(binding.longPressRunnable);
            binding.longPressRunnable = null;
        }

        switch (binding.role) {
            case BUTTON: {
                GenshinButton btn = binding.button;
                if (btn != null && btn.activePointerId == pointerId) {
                    boolean wasLongPressed = btn.longPressTriggered;
                    btn.activePointerId = -1;
                    btn.pressed = false;
                    btn.longPressTriggered = false;

                    if (btn.action.inputType == GenshinMapping.InputType.CHARACTER_SLOT) {
                        if (!cancelled && !wasLongPressed) {
                            inputHandler.tapCharacterSlot(btn.action.characterSlot);
                        }
                    } else {
                        inputHandler.onActionUp(btn.action);
                    }
                }
                break;
            }
            case JOYSTICK: {
                if (joystick != null && joystick.activePointerId == pointerId) {
                    joystick.resetToRest();
                    inputHandler.releaseJoystick();
                }
                break;
            }
            case CAMERA: {
                if (cameraPointerId == pointerId) {
                    cameraPointerId = -1;
                    inputHandler.resetCameraRemainder();
                }
                break;
            }
            case TOOLBAR:
            case EDIT_ELEMENT:
                break;
        }
        invalidate();
    }

    private GenshinButton findButtonAt(float x, float y) {
        // Iterate in reverse so top-drawn buttons win if overlapping
        for (int i = buttons.size() - 1; i >= 0; i--) {
            GenshinButton btn = buttons.get(i);
            if (btn.hitTest(x, y)) {
                return btn;
            }
        }
        return null;
    }

    private boolean handleEditTouch(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                if (tbMoveRect.contains(x, y)) {
                    editMode = EditMode.MOVE;
                    invalidate();
                    return true;
                }
                if (tbResizeRect.contains(x, y)) {
                    editMode = EditMode.RESIZE;
                    invalidate();
                    return true;
                }
                if (tbOpMinusRect.contains(x, y)) {
                    adjustSelectedOpacity(-10);
                    return true;
                }
                if (tbOpPlusRect.contains(x, y)) {
                    adjustSelectedOpacity(10);
                    return true;
                }
                if (tbResetRect.contains(x, y)) {
                    profile.resetToDefaults();
                    profile.save(getContext());
                    rebuildElementsFromProfile();
                    updateGeometry();
                    Toast.makeText(getContext(), "Genshin layout reset to default", Toast.LENGTH_SHORT).show();
                    invalidate();
                    return true;
                }
                if (tbDoneRect.contains(x, y)) {
                    profile.save(getContext());
                    setEditMode(EditMode.NONE);
                    updateGeometry();
                    Toast.makeText(getContext(), "Genshin layout saved", Toast.LENGTH_SHORT).show();
                    invalidate();
                    return true;
                }

                // Select a button or joystick
                GenshinButton hitBtn = findButtonAt(x, y);
                if (hitBtn != null) {
                    selectedEditTarget = hitBtn;
                    editStartTouchX = x;
                    editStartTouchY = y;
                    editStartCx = hitBtn.layout.centerX;
                    editStartCy = hitBtn.layout.centerY;
                    editStartSize = hitBtn.layout.sizePct;
                    invalidate();
                    return true;
                }
                if (joystick != null && joystick.hitTestRest(x, y)) {
                    selectedEditTarget = joystick;
                    editStartTouchX = x;
                    editStartTouchY = y;
                    editStartCx = joystick.layout.centerX;
                    editStartCy = joystick.layout.centerY;
                    editStartSize = joystick.layout.sizePct;
                    invalidate();
                    return true;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (selectedEditTarget == null || safeRect.width() <= 0 || safeRect.height() <= 0) {
                    return true;
                }
                GenshinProfile.ElementLayout targetLayout = getSelectedLayout();
                if (targetLayout == null) {
                    return true;
                }

                float dx = x - editStartTouchX;
                float dy = y - editStartTouchY;
                if (editMode == EditMode.MOVE) {
                    targetLayout.centerX = clamp(editStartCx + dx / safeRect.width(), 0.03f, 0.97f);
                    targetLayout.centerY = clamp(editStartCy + dy / safeRect.height(), 0.06f, 0.94f);
                } else if (editMode == EditMode.RESIZE) {
                    float delta = (dx - dy) / safeRect.height() * 0.5f;
                    float minSz = (selectedEditTarget == joystick) ? 0.16f : 0.065f;
                    float maxSz = (selectedEditTarget == joystick) ? 0.45f : 0.30f;
                    targetLayout.sizePct = clamp(editStartSize + delta, minSz, maxSz);
                }
                updateGeometry();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return true;
    }

    private GenshinProfile.ElementLayout getSelectedLayout() {
        if (selectedEditTarget instanceof GenshinButton) {
            return ((GenshinButton) selectedEditTarget).layout;
        }
        if (selectedEditTarget instanceof GenshinJoystick) {
            return ((GenshinJoystick) selectedEditTarget).layout;
        }
        return null;
    }

    private void adjustSelectedOpacity(int delta) {
        GenshinProfile.ElementLayout targetLayout = getSelectedLayout();
        if (targetLayout == null) {
            return;
        }
        int current = (targetLayout.opacity >= 0) ? targetLayout.opacity : globalOpacity;
        targetLayout.opacity = Math.max(15, Math.min(100, current + delta));
        invalidate();
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}
