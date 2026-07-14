//轮盘按键
package com.limelight.binding.input.advance_setting.element;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.advance_setting.PageDeviceController;
import com.limelight.binding.input.advance_setting.superpage.ElementEditText;
import com.limelight.binding.input.advance_setting.superpage.NumberSeekbar;
import com.limelight.binding.input.advance_setting.superpage.SuperPageLayout;
import com.limelight.utils.AppDialogStyler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class WheelPad extends Element {

    private static final String EXTRA_SEGMENT_ALIGNMENT = "segmentAlignment";
    private static final String SEGMENT_ALIGNMENT_CENTER = "center";
    private static final String SEGMENT_ALIGNMENT_EDGE = "edge";

    private final PageDeviceController pageDeviceController;
    private final WheelPad wheelPad;

    private int diameter;
    private int segmentCount;
    private int innerRadiusPercent;
    private boolean isPopupMode;
    private String centerText;
    private List<String> segmentValues;
    private List<String> segmentNames;
    private int normalColor;
    private int pressedColor;
    private int backgroundColor;
    private int thick;
    private int normalTextColor;
    private int pressedTextColor;
    private int centerTextColor;  // 中心文字颜色
    private int textSizePercent;  // 分区文字大小百分比
    private int centerTextSizePercent; // 选择预览文字大小百分比

    private int triggerTextSizePercent; // 触发器文字大小百分比


    private final int screenWidth;
    private final int screenHeight;

    private final Paint paintBorder = new Paint();
    private final Paint paintSegmentFill = new Paint();
    private final Paint paintSegmentFillPressed = new Paint();
    private final Paint paintBackground = new Paint();
    private final Paint paintText = new Paint();
    private final Paint paintEdit = new Paint();
    private final Paint paintCenterText = new Paint();
    private final Paint paintGlow = new Paint(); // 用于激活分区的发光效果
    private final RectF rect = new RectF();
    private final Path textPath = new Path();

    private int activeIndex = -1;
    private int lastActiveIndex = -1;
    private boolean isWheelActive = false;
    private boolean popupAtScreenCenter;
    private boolean previewGroupChildren;
    private boolean alignSegmentEdge;
    // 用于追踪当前悬停的组按键，以实现子按键预览
    private GroupButton hoveredGroupButton = null;

    private SuperPageLayout wheelPadPage;
    private NumberSeekbar centralXNumberSeekbar;
    private NumberSeekbar centralYNumberSeekbar;
    private LinearLayout valuesContainer;

    public WheelPad(Map<String, Object> attributesMap,
                    ElementController controller,
                    PageDeviceController pageDeviceController, Context context) {
        super(attributesMap, controller, context);
        this.pageDeviceController = pageDeviceController;
        this.wheelPad = this;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Game) context).getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        this.screenWidth = displayMetrics.widthPixels;
        this.screenHeight = displayMetrics.heightPixels;

        super.centralXMax = displayMetrics.widthPixels;
        super.centralXMin = 0;
        super.centralYMax = displayMetrics.heightPixels;
        super.centralYMin = 0;
        super.widthMax = displayMetrics.widthPixels;
        super.widthMin = 150;
        super.heightMax = displayMetrics.heightPixels;
        super.heightMin = 150;

        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setAntiAlias(true);
        paintSegmentFill.setStyle(Paint.Style.FILL);
        paintSegmentFill.setAntiAlias(true);
        paintSegmentFillPressed.setStyle(Paint.Style.FILL);
        paintSegmentFillPressed.setAntiAlias(true);
        paintBackground.setStyle(Paint.Style.FILL);
        paintBackground.setAntiAlias(true);
        paintText.setColor(0xFFFFFFFF);
        paintText.setTextSize(30);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setAntiAlias(true);
        paintEdit.setStyle(Paint.Style.STROKE);
        paintEdit.setStrokeWidth(4);
        paintEdit.setPathEffect(new DashPathEffect(new float[]{10, 20}, 0));
        paintCenterText.setColor(0xFFFFFFFF);
        paintCenterText.setTextSize(60);
        paintCenterText.setTextAlign(Paint.Align.CENTER);
        paintCenterText.setAntiAlias(true);
        paintCenterText.setFakeBoldText(true);
        
        // 初始化发光效果画笔
        paintGlow.setStyle(Paint.Style.FILL);
        paintGlow.setAntiAlias(true);

        this.diameter = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_WIDTH)).intValue();

        Object textObj = attributesMap.get(COLUMN_STRING_ELEMENT_TEXT);
        this.centerText = (textObj != null) ? (String) textObj : "";
        this.isPopupMode = !this.centerText.isEmpty();

        thick = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_THICK)).intValue();
        normalColor = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_NORMAL_COLOR)).intValue();
        pressedColor = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_PRESSED_COLOR)).intValue();
        backgroundColor = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_BACKGROUND_COLOR)).intValue();
        segmentCount = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_MODE)).intValue();
        innerRadiusPercent = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_SENSE)).intValue();

        // Load the popup behavior flag. Default to true (new behavior) for old configs.
        Object popupFlagObj = attributesMap.get(COLUMN_INT_ELEMENT_FLAG1);
        this.popupAtScreenCenter = (popupFlagObj == null) || ((Long) popupFlagObj).intValue() == 1;

        if (attributesMap.containsKey("extra_attributes")) {
            String extraAttrsJsonString = (String) attributesMap.get("extra_attributes");
            if (extraAttrsJsonString != null && !extraAttrsJsonString.isEmpty()) {
                try {
                    JsonObject extraAttrs = new Gson().fromJson(extraAttrsJsonString, JsonObject.class);

                    // 从 extraAttrs 中安全地读取值
                    if (extraAttrs.has("normalTextColor")) {
                        this.normalTextColor = extraAttrs.get("normalTextColor").getAsInt();
                    } else {
                        this.normalTextColor = 0xFFFFFFFF;
                    }

                    if (extraAttrs.has("centerTextColor")) {
                        this.centerTextColor = extraAttrs.get("centerTextColor").getAsInt();
                    } else {
                        this.centerTextColor = 0xFFFFFFFF;
                    }

                    if (extraAttrs.has("pressedTextColor")) {
                        this.pressedTextColor = extraAttrs.get("pressedTextColor").getAsInt();
                    } else {
                        this.pressedTextColor = 0xFFFFFFFF;
                    }

                    if (extraAttrs.has("textSizePercent")) {
                        this.textSizePercent = extraAttrs.get("textSizePercent").getAsInt();
                    } else {
                        this.textSizePercent = 35;
                    }

                    if (extraAttrs.has("centerTextSizePercent")) {
                        this.centerTextSizePercent = extraAttrs.get("centerTextSizePercent").getAsInt();
                    } else {
                        this.centerTextSizePercent = 60;
                    }
                    if (extraAttrs.has("triggerTextSizePercent")) {
                        this.triggerTextSizePercent = extraAttrs.get("triggerTextSizePercent").getAsInt();
                    } else {
                        // 提供一个合理的默认值，通常应该比中心预览文字小
                        this.triggerTextSizePercent = 40;
                    }
                    if (extraAttrs.has("previewGroupChildren")) {
                        this.previewGroupChildren = extraAttrs.get("previewGroupChildren").getAsBoolean();
                    } else {
                        this.previewGroupChildren = true; // 默认为开启
                    }
                    if (extraAttrs.has(EXTRA_SEGMENT_ALIGNMENT)) {
                        this.alignSegmentEdge = SEGMENT_ALIGNMENT_EDGE.equals(
                                extraAttrs.get(EXTRA_SEGMENT_ALIGNMENT).getAsString());
                    } else {
                        this.alignSegmentEdge = false;
                    }

                } catch (Exception e) {
                    // JSON 解析失败，使用默认值
                    initializeDefaultFontAttributes();
                }
            } else {
                // 字段存在但为空，使用默认值
                initializeDefaultFontAttributes();
            }
        } else {
            // 字段不存在（旧数据），使用默认值
            initializeDefaultFontAttributes();
        }

        String valuesString = (String) attributesMap.get(COLUMN_STRING_ELEMENT_VALUE);
        segmentValues = new ArrayList<>();
        segmentNames = new ArrayList<>();
        String[] segments = valuesString.split(",");
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            String[] parts = segment.split("\\|", 2);
            segmentValues.add(parts[0]);
            if (parts.length > 1) {
                segmentNames.add(parts[1]);
            } else {
                segmentNames.add("");
            }
        }
    }

    private void initializeDefaultFontAttributes() {
        this.normalTextColor = 0xFFFFFFFF;
        this.pressedTextColor = 0xFFFFFFFF;
        this.centerTextColor = 0xFFFFFFFF;
        this.textSizePercent = 35;
        this.centerTextSizePercent = 60;
        this.triggerTextSizePercent = 40;
        this.previewGroupChildren = true;
        this.alignSegmentEdge = false;
    }

    public boolean isBeingEdited() {
        if (elementController != null) {
            SuperPageLayout currentPage = elementController.getCurrentEditingPage();
            return this.wheelPadPage != null && currentPage == this.wheelPadPage;
        }
        return false;
    }

    private List<ElementController.SendEventHandler> getHandlersForValueNow(String value) {
        List<ElementController.SendEventHandler> handlers = new ArrayList<>();
        if (value == null || value.isEmpty() || value.equals("null")) {
            return handlers;
        }

        String[] keyValues = value.split("\\+");
        for (String singleKeyValue : keyValues) {
            ElementController.SendEventHandler handler = elementController.getSendEventHandler(singleKeyValue.trim());
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        ElementController.Mode currentMode = elementController.getMode();
        boolean isTheOneBeingEdited = isBeingEdited();
        // 屏幕中心预览
        if (isPopupMode && popupAtScreenCenter) {
            drawInactivePopupCenter(canvas);
            boolean shouldDrawCentralPreview =
                    (currentMode == ElementController.Mode.Normal && isWheelActive) ||
                            (currentMode == ElementController.Mode.Edit && isTheOneBeingEdited);

            if (shouldDrawCentralPreview) {
                canvas.save();
                float translateX = (screenWidth / 2.0f) - getElementCentralX();
                float translateY = (screenHeight / 2.0f) - getElementCentralY();
                canvas.translate(translateX, translateY);
                drawFullWheel(canvas);

                // 在绘制完轮盘后，如果悬停在组按键上，则绘制其子按键预览
                // 如果开启了预览功能，则绘制子按键
                if (previewGroupChildren) {
                    drawHoveredGroupButtonChildren(canvas, translateX, translateY);
                }

                canvas.restore();
            }

        } else {
            boolean shouldDrawTriggerInsteadOfFullWheel = isPopupMode && !popupAtScreenCenter &&
                    currentMode == ElementController.Mode.Normal && !isWheelActive;
            if (shouldDrawTriggerInsteadOfFullWheel) {
                drawInactivePopupCenter(canvas);
            } else {
                // 原地预览
                drawFullWheel(canvas);
                // 检查是否为激活的 "原地弹出模式"，如果是，则添加组按键预览
                boolean isActiveOnSitePopup = isPopupMode && !popupAtScreenCenter &&
                        currentMode == ElementController.Mode.Normal && isWheelActive;

                if (isActiveOnSitePopup) {
                    // 因为是原地绘制，没有对画布进行平移，所以平移量为0
                    // 如果开启了预览功能，并且是激活的原地弹出模式，则绘制
                    if (previewGroupChildren && isActiveOnSitePopup) {
                        drawHoveredGroupButtonChildren(canvas, 0, 0);
                    }
                }
            }
        }

        if (currentMode == ElementController.Mode.Edit || currentMode == ElementController.Mode.Select) {
            rect.left = rect.top = 2;
            rect.right = getWidth() - 2;
            rect.bottom = getHeight() - 2;
            paintEdit.setColor(editColor);
            canvas.drawRect(rect, paintEdit);
        }
    }

    /**
     * 在轮盘预览模式下，绘制当前悬停的组按键的子按键。
     * @param canvas 画布。对于屏幕中心模式，其坐标系已平移；对于原地模式，坐标系为WheelPad的视图坐标系。
     * @param wheelTranslateX 轮盘绘制时在X轴上的平移量 (原地弹出时为0)。
     * @param wheelTranslateY 轮盘绘制时在Y轴上的平移量 (原地弹出时为0)。
     */
    private void drawHoveredGroupButtonChildren(Canvas canvas, float wheelTranslateX, float wheelTranslateY) {
        if (hoveredGroupButton == null) {
            return;
        }

        List<Long> childIds = hoveredGroupButton.getChildIds();
        if (childIds == null || childIds.isEmpty()) {
            return;
        }

        for (Long childId : childIds) {
            Element child = elementController.findElementById(childId);
            if (child != null) {
                canvas.save();

                // 计算子元素相对于当前画布坐标系原点的位置。
                // 1. child.getLeft() 是子元素在屏幕上的绝对X坐标。
                // 2. this.getLeft() 是WheelPad视图在屏幕上的绝对X坐标。
                // 3. wheelTranslateX 是为了将轮盘居中而额外平移的量。
                // 最终，(this.getLeft() + wheelTranslateX) 就是当前画布原点在屏幕上的绝对X坐标。
                // 两者相减，得到子元素应该在当前画布的哪个相对位置绘制。
                float childDrawX = child.getLeft() - (this.getLeft() + wheelTranslateX);
                float childDrawY = child.getTop() - (this.getTop() + wheelTranslateY);

                // 将画布平移到子元素应该被绘制的相对位置。
                canvas.translate(childDrawX, childDrawY);

                // 调用子元素的绘制方法。
                // onElementDraw 期望画布原点已经位于元素的左上角，我们刚刚通过translate实现了这一点。
                child.onElementDraw(canvas);

                canvas.restore();
            }
        }
    }

    private void drawInactivePopupCenter(Canvas canvas) {
        float centerX = getWidth() / 2.0f;
        float centerY = getHeight() / 2.0f;
        float fillOuterRadius = (getWidth() / 2.0f) - thick;
        if (fillOuterRadius < 0) fillOuterRadius = 0;
        float fillInnerRadius = fillOuterRadius * (innerRadiusPercent / 100.0f);
        float borderInnerDrawRadius = fillInnerRadius + (thick / 2.0f);

        paintBorder.setColor(normalColor);
        paintBorder.setStrokeWidth(thick);
        // 柔和内圈高亮轮廓
        paintBorder.setShadowLayer(thick * 0.6f, 0, 0, (normalColor & 0x00FFFFFF) | 0x33000000);

        paintSegmentFill.setColor(0x80000000);
        canvas.drawCircle(centerX, centerY, fillInnerRadius, paintSegmentFill);

        rect.set(centerX - borderInnerDrawRadius, centerY - borderInnerDrawRadius, centerX + borderInnerDrawRadius, centerY + borderInnerDrawRadius);
        canvas.drawOval(rect, paintBorder);
        // 清除阴影以免影响后续绘制
        paintBorder.setShadowLayer(0, 0, 0, 0);

        // 应用中心文字的颜色和大小 ---
        paintCenterText.setColor(this.centerTextColor);
        // 触发器文字大小改为基于内圈直径计算，随中心分区大小联动
        float triggerTextSize = (fillInnerRadius * 2) * (this.triggerTextSizePercent / 100.0f);
        paintCenterText.setTextSize(triggerTextSize);
        paintCenterText.setShadowLayer(Math.max(2, thick * 0.3f), 0, Math.max(1, thick * 0.2f), 0x88000000);

        float textY = centerY - ((paintCenterText.descent() + paintCenterText.ascent()) / 2);
        canvas.drawText(centerText, centerX, textY, paintCenterText);
    }

    private void drawFullWheel(Canvas canvas) {
        ElementController.Mode currentMode = elementController.getMode();
        float centerX = getWidth() / 2.0f;
        float centerY = getHeight() / 2.0f;

        float fillOuterRadius = (getWidth() / 2.0f) - thick;
        if (fillOuterRadius < 0) fillOuterRadius = 0;
        float fillInnerRadius = fillOuterRadius * (innerRadiusPercent / 100.0f);
        float borderOuterDrawRadius = (getWidth() / 2.0f) - (thick / 2.0f);
        if (borderOuterDrawRadius < 0) borderOuterDrawRadius = 0;
        float borderInnerDrawRadius = fillInnerRadius + (thick / 2.0f);

        paintBorder.setColor(normalColor);
        paintBorder.setStrokeWidth(thick);
        paintBackground.setColor(0xFF000000);

        paintSegmentFill.setColor(backgroundColor);
        paintSegmentFillPressed.setColor(pressedColor);

        paintText.setColor(this.normalTextColor);
        // 字体大小基于内外半径的差值（环带厚度）来计算，这样更直观
        float ringThickness = fillOuterRadius - fillInnerRadius;
        float segmentTextSize = ringThickness * (this.textSizePercent / 100.0f);
        paintText.setTextSize(segmentTextSize);

        float sweepAngle = WheelPadGeometry.sweepAngleDegrees(segmentCount);

        rect.set(centerX - fillOuterRadius, centerY - fillOuterRadius, centerX + fillOuterRadius, centerY + fillOuterRadius);
        for (int i = 0; i < segmentCount; i++) {
            float startAngle = WheelPadGeometry.segmentStartAngleDegrees(i, segmentCount, alignSegmentEdge);
            Paint currentFillPaint = (i == activeIndex) ? paintSegmentFillPressed : paintSegmentFill;
            canvas.drawArc(rect, startAngle, sweepAngle, true, currentFillPaint);
            
            // 为激活分区添加发光效果
            if (i == activeIndex) {
                paintGlow.setColor(Color.argb(60, Color.red(pressedColor), Color.green(pressedColor), Color.blue(pressedColor)));
                paintGlow.setShadowLayer(fillOuterRadius * 0.15f, 0, 0, pressedColor);
                canvas.drawArc(rect, startAngle, sweepAngle, true, paintGlow);
                paintGlow.setShadowLayer(0, 0, 0, 0); // 清除阴影
            }
        }

        canvas.drawCircle(centerX, centerY, fillInnerRadius, paintBackground);

        float textRadius = fillInnerRadius + (fillOuterRadius - fillInnerRadius) / 2.0f;
        for (int i = 0; i < segmentCount; i++) {
            float startAngle = WheelPadGeometry.segmentStartAngleDegrees(i, segmentCount, alignSegmentEdge);
            float textAngle = startAngle + sweepAngle / 2.0f; // 分区中心角度（以上方为-90度起点）

            if (i < segmentValues.size()) {
                String displayName;
                if (i < segmentNames.size() && segmentNames.get(i) != null && !segmentNames.get(i).isEmpty()) {
                    displayName = segmentNames.get(i);
                } else {
                    displayName = getDisplayStringForValue(segmentValues.get(i));
                }

                // 根据激活状态切换文字颜色与粗体
                if (i == activeIndex) {
                    paintText.setColor(this.pressedTextColor);
                    paintText.setFakeBoldText(true);
                } else {
                    paintText.setColor(this.normalTextColor);
                    paintText.setFakeBoldText(false);
                }

                // 计算文字中心点坐标（保持文字水平，不旋转）
                double textRad = Math.toRadians(textAngle);
                float textCenterX = centerX + (float) (textRadius * Math.cos(textRad));
                float textCenterY = centerY + (float) (textRadius * Math.sin(textRad));

                // 将中心点转换为基线位置，使用字体度量保证垂直居中
                Paint.FontMetrics fm = paintText.getFontMetrics();
                float baselineY = textCenterY - (fm.ascent + fm.descent) / 2.0f;

                canvas.drawText(displayName, textCenterX, baselineY, paintText);
            }
        }

        rect.set(centerX - borderOuterDrawRadius, centerY - borderOuterDrawRadius, centerX + borderOuterDrawRadius, centerY + borderOuterDrawRadius);
        canvas.drawOval(rect, paintBorder);
        rect.set(centerX - borderInnerDrawRadius, centerY - borderInnerDrawRadius, centerX + borderInnerDrawRadius, centerY + borderInnerDrawRadius);
        canvas.drawOval(rect, paintBorder);

        for (int i = 0; i < segmentCount; i++) {
            float angle = WheelPadGeometry.segmentStartAngleDegrees(i, segmentCount, alignSegmentEdge);
            double angleRad = Math.toRadians(angle);
            float startX = centerX + (float) (fillInnerRadius * Math.cos(angleRad));
            float startY = centerY + (float) (fillInnerRadius * Math.sin(angleRad));
            float endX = centerX + (float) (fillOuterRadius * Math.cos(angleRad));
            float endY = centerY + (float) (fillOuterRadius * Math.sin(angleRad));
            canvas.drawLine(startX, startY, endX, endY, paintBorder);
        }

        // 在中心模式时，为外圈描边添加柔和阴影以增强层次
        if (isPopupMode && popupAtScreenCenter) {
            paintBorder.setShadowLayer(Math.max(2, thick * 0.6f), 0, 0, 0x55000000);
        }

        boolean showCenterTextInNormalMode = isPopupMode && activeIndex != -1 && currentMode == ElementController.Mode.Normal;
        boolean showCenterTextInEditMode = currentMode == ElementController.Mode.Edit && isBeingEdited();

        if (showCenterTextInNormalMode || showCenterTextInEditMode) {
            String displayName;

            if (showCenterTextInNormalMode) {
                // 正常模式下，显示当前选中的分区文本
                if (activeIndex < segmentValues.size()) {
                    if (activeIndex < segmentNames.size() && segmentNames.get(activeIndex) != null && !segmentNames.get(activeIndex).isEmpty()) {
                        displayName = segmentNames.get(activeIndex);
                    } else {
                        displayName = getDisplayStringForValue(segmentValues.get(activeIndex));
                    }
                } else {
                    displayName = "预览"; // 备用
                }
            } else {
                // 编辑模式下，显示固定的示例文本
                displayName = "预览";
            }

            // 应用中心文字的颜色和大小 (这部分逻辑不变)
            paintCenterText.setColor(this.centerTextColor);
            float centerTextSize = fillInnerRadius * 2 * (this.centerTextSizePercent / 100.0f);
            paintCenterText.setTextSize(centerTextSize);
            paintCenterText.setShadowLayer(Math.max(2, centerTextSize * 0.06f), 0, Math.max(1, centerTextSize * 0.04f), 0x88000000);

            float textY = centerY - ((paintCenterText.descent() + paintCenterText.ascent()) / 2);
            canvas.drawText(displayName, centerX, textY, paintCenterText);
        }

        // 清除描边阴影，避免影响外部绘制
        if (isPopupMode && popupAtScreenCenter) {
            paintBorder.setShadowLayer(0, 0, 0, 0);
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        if (elementController.getMode() != ElementController.Mode.Normal) {
            return true;
        }

        if (!isPopupMode) {
            handleDirectModeTouchEvent(event);
            return true;
        }

        if (isWheelActive) {
            handlePopupModeTouchEvent(event);
            return true;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            float centerX = getWidth() / 2.0f;
            float centerY = getHeight() / 2.0f;
            float outerRadius = (getWidth() / 2.0f) - thick;
            if (outerRadius < 0) outerRadius = 0;
            float innerRadius = outerRadius * (innerRadiusPercent / 100.0f);
            float dx = x - centerX;
            float dy = y - centerY;
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance <= innerRadius) {
                handlePopupModeTouchEvent(event);
                return true;
            }
        }
        return false;
    }

    private void handleDirectModeTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            if (action == MotionEvent.ACTION_DOWN) {
                elementController.buttonVibrator();
            }
            float x = event.getX();
            float y = event.getY();
            float centerX = getWidth() / 2.0f;
            float centerY = getHeight() / 2.0f;
            float outerRadius = (getWidth() / 2.0f) - thick;
            if (outerRadius < 0) outerRadius = 0;
            float innerRadius = outerRadius * (innerRadiusPercent / 100.0f);
            float dx = x - centerX;
            float dy = y - centerY;
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance > innerRadius && distance < outerRadius) {
                activeIndex = WheelPadGeometry.segmentIndexForVector(dx, dy, segmentCount, alignSegmentEdge);
            } else {
                activeIndex = -1;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            activeIndex = -1;
        }
        updateSendingState();
        invalidate();
    }

    private void handlePopupModeTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float centerX = getWidth() / 2.0f;
        float centerY = getHeight() / 2.0f;

        float outerRadius = (getWidth() / 2.0f) - thick;
        if (outerRadius < 0) outerRadius = 0;
        float innerRadius = outerRadius * (innerRadiusPercent / 100.0f);

        float dx = x - centerX;
        float dy = y - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (distance <= innerRadius) {
                    isWheelActive = true;
                    elementController.buttonVibrator();
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (isWheelActive) {
                    // 清除上一帧的悬停状态
                    hoveredGroupButton = null;

                    if (distance > innerRadius) {
                        activeIndex = WheelPadGeometry.segmentIndexForVector(dx, dy, segmentCount, alignSegmentEdge);

                        // 检查当前分区是否为组按键，如果是，则获取其实例用于预览
                        if (activeIndex != -1 && activeIndex < segmentValues.size()) {
                            String value = segmentValues.get(activeIndex);
                            if (value != null && value.startsWith("gb")) {
                                try {
                                    long groupId = Long.parseLong(value.substring(2));
                                    Element element = elementController.findElementById(groupId);
                                    if (element instanceof GroupButton) {
                                        hoveredGroupButton = (GroupButton) element;
                                    }
                                } catch (Exception e) {
                                    // 如果ID解析失败或找不到元素，确保悬停状态被清除
                                    hoveredGroupButton = null;
                                }
                            }
                        }
                    } else {
                        activeIndex = -1;
                    }
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isWheelActive) {
                    if (activeIndex != -1 && activeIndex < segmentValues.size()) {
                        String value = segmentValues.get(activeIndex);
                        // 即时获取 handlers
                        List<ElementController.SendEventHandler> handlers = getHandlersForValueNow(value);

                        // 按下
                        for (ElementController.SendEventHandler handler : handlers) {
                            handler.sendEvent(true);
                        }

                        // 加入 30 毫秒延迟后再发送释放事件，防止按键时间过短被游戏忽略
                        if (!handlers.isEmpty()) {
                            elementController.getHandler().postDelayed(() -> {
                                for (int i = handlers.size() - 1; i >= 0; i--) {
                                    handlers.get(i).sendEvent(false);
                                }
                            }, 30); // 30 毫秒的持续时间
                        }
                    }
                    isWheelActive = false;
                    activeIndex = -1;
                    // 手指抬起，清除组按键预览
                    hoveredGroupButton = null;
                    invalidate();
                }
                break;
        }
    }

    private void updateSendingState() {
        if (activeIndex != lastActiveIndex) {
            // --- 释放上一个 ---
            if (lastActiveIndex != -1 && lastActiveIndex < segmentValues.size()) {
                String lastValue = segmentValues.get(lastActiveIndex);
                // 即时获取 handlers
                List<ElementController.SendEventHandler> lastHandlers = getHandlersForValueNow(lastValue);
                for (int i = lastHandlers.size() - 1; i >= 0; i--) {
                    lastHandlers.get(i).sendEvent(false);
                }
            }

            // --- 按下新的 ---
            if (activeIndex != -1 && activeIndex < segmentValues.size()) {
                String activeValue = segmentValues.get(activeIndex);
                // 即时获取 handlers
                List<ElementController.SendEventHandler> activeHandlers = getHandlersForValueNow(activeValue);
                for (ElementController.SendEventHandler handler : activeHandlers) {
                    handler.sendEvent(true);
                }
            }
            lastActiveIndex = activeIndex;
        }
    }

    @Override
    protected SuperPageLayout getInfoPage() {
        if (wheelPadPage == null) {
            wheelPadPage = (SuperPageLayout) LayoutInflater.from(getContext()).inflate(R.layout.page_wheel_pad_clean, null);
            centralXNumberSeekbar = wheelPadPage.findViewById(R.id.page_wheel_pad_central_x);
            centralYNumberSeekbar = wheelPadPage.findViewById(R.id.page_wheel_pad_central_y);
            valuesContainer = wheelPadPage.findViewById(R.id.page_wheel_pad_values_container);
        }

        // Find all views
        ElementEditText centerTextInput = wheelPadPage.findViewById(R.id.page_wheel_pad_center_text);
        final Switch popupSwitch = wheelPadPage.findViewById(R.id.page_wheel_pad_popup_at_center_switch);

        final Switch previewGroupSwitch = wheelPadPage.findViewById(R.id.page_wheel_pad_preview_group_switch);

        final LinearLayout popupOptionsContainer = wheelPadPage.findViewById(R.id.page_wheel_pad_popup_options_container);

        NumberSeekbar sizeNumberSeekbar = wheelPadPage.findViewById(R.id.page_wheel_pad_size);
        NumberSeekbar thickNumberSeekbar = wheelPadPage.findViewById(R.id.page_wheel_pad_thick);
        NumberSeekbar layerNumberSeekbar = wheelPadPage.findViewById(R.id.page_wheel_pad_layer);
        ElementEditText normalColorElementEditText = wheelPadPage.findViewById(R.id.page_wheel_pad_normal_color);
        ElementEditText pressedColorElementEditText = wheelPadPage.findViewById(R.id.page_wheel_pad_pressed_color);
        ElementEditText backgroundColorElementEditText = wheelPadPage.findViewById(R.id.page_wheel_pad_background_color);
        Button copyButton = wheelPadPage.findViewById(R.id.page_wheel_pad_copy);
        Button deleteButton = wheelPadPage.findViewById(R.id.page_wheel_pad_delete);
        NumberSeekbar segmentCountNumberSeekbar = wheelPadPage.findViewById(R.id.page_wheel_pad_segment_count);
        Spinner segmentAlignmentSpinner = wheelPadPage.findViewById(R.id.page_wheel_pad_segment_alignment);
        NumberSeekbar innerRadiusNumberSeekbar = wheelPadPage.findViewById(R.id.page_wheel_pad_inner_radius);

        NumberSeekbar textSizeNumberSeekbar = wheelPadPage.findViewById(R.id.page_wheel_pad_text_size);
        ElementEditText normalTextColorElementEditText = wheelPadPage.findViewById(R.id.page_wheel_pad_normal_text_color);
        ElementEditText pressedTextColorElementEditText = wheelPadPage.findViewById(R.id.page_wheel_pad_pressed_text_color);
        NumberSeekbar centerTextSizeNumberSeekbar = wheelPadPage.findViewById(R.id.page_wheel_pad_center_text_size);
        ElementEditText centerTextColorElementEditText = wheelPadPage.findViewById(R.id.page_wheel_pad_center_text_color);
        NumberSeekbar triggerTextSizeNumberSeekbar = wheelPadPage.findViewById(R.id.page_wheel_pad_trigger_text_size);

        setupCommonControls(centerTextInput, sizeNumberSeekbar, thickNumberSeekbar, layerNumberSeekbar,
                normalColorElementEditText, pressedColorElementEditText, backgroundColorElementEditText,
                copyButton, deleteButton);

        // Setup the switch
        popupSwitch.setChecked(this.popupAtScreenCenter);
        popupSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.popupAtScreenCenter = isChecked;
            invalidate();
            save();
        });

        // 设置预览开关
        previewGroupSwitch.setChecked(this.previewGroupChildren);
        previewGroupSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.previewGroupChildren = isChecked;
            // 预览开关不需要实时 invalidate()，因为它只在轮盘激活时生效
            save();
        });

        // Set the initial visibility based on the current mode
        boolean isPopup = !this.centerText.isEmpty();
        popupOptionsContainer.setVisibility(isPopup ? View.VISIBLE : View.GONE);

        // Override the listener to control visibility of the container
        centerTextInput.setOnTextChangedListener(text -> {
            setCenterText(text);
            boolean isPopupNow = !text.isEmpty();
            // Show/Hide the entire container based on popup mode
            popupOptionsContainer.setVisibility(isPopupNow ? View.VISIBLE : View.GONE);
            invalidate();
            save();
        });

        segmentCountNumberSeekbar.setValueWithNoCallBack(segmentCount);
        segmentCountNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setSegmentCount(progress);
                updateValuesContainerUI();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                save();
            }
        });

        ArrayAdapter<String> alignmentAdapter = new ArrayAdapter<>(
                getContext(),
                R.layout.crown_spinner_item,
                Arrays.asList(
                        getContext().getString(R.string.wheel_alignment_center),
                        getContext().getString(R.string.wheel_alignment_edge)
                )
        );
        alignmentAdapter.setDropDownViewResource(R.layout.crown_spinner_item);
        segmentAlignmentSpinner.setAdapter(alignmentAdapter);
        segmentAlignmentSpinner.setSelection(alignSegmentEdge ? 1 : 0, false);
        segmentAlignmentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean newAlignSegmentEdge = position == 1;
                if (alignSegmentEdge != newAlignSegmentEdge) {
                    alignSegmentEdge = newAlignSegmentEdge;
                    invalidate();
                    save();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        innerRadiusNumberSeekbar.setValueWithNoCallBack(innerRadiusPercent);
        innerRadiusNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setInnerRadiusPercent(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                save();
            }
        });

        // 分区文字大小
        textSizeNumberSeekbar.setProgressMin(10); // 10%
        textSizeNumberSeekbar.setProgressMax(100); // 100%
        textSizeNumberSeekbar.setValueWithNoCallBack(this.textSizePercent);
        textSizeNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textSizePercent = progress;
                invalidate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                save();
            }
        });

        // 中心文字大小
        centerTextSizeNumberSeekbar.setProgressMin(10); // 10%
        centerTextSizeNumberSeekbar.setProgressMax(150); // 150%
        centerTextSizeNumberSeekbar.setValueWithNoCallBack(this.centerTextSizePercent);
        centerTextSizeNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                centerTextSizePercent = progress;
                invalidate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                save();
            }
        });

        CrownColorPickerBinder.bind(this, normalTextColorElementEditText, () -> this.normalTextColor, color -> {
            this.normalTextColor = color;
            invalidate();
        });
        CrownColorPickerBinder.bind(this, pressedTextColorElementEditText, () -> this.pressedTextColor, color -> {
            this.pressedTextColor = color;
            invalidate();
        });
        CrownColorPickerBinder.bind(this, centerTextColorElementEditText, () -> this.centerTextColor, color -> {
            this.centerTextColor = color;
            invalidate();
        });
        // --- 设置触发器文字大小的 seekbar ---
        triggerTextSizeNumberSeekbar.setProgressMin(5);
        triggerTextSizeNumberSeekbar.setProgressMax(150);
        triggerTextSizeNumberSeekbar.setValueWithNoCallBack(this.triggerTextSizePercent);
        triggerTextSizeNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                triggerTextSizePercent = progress;
                invalidate(); // 实时预览
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                save(); // 保存设置
            }
        });

        updateValuesContainerUI();
        return wheelPadPage;
    }

    @Override
    public void save() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_INT_ELEMENT_WIDTH, this.diameter);
        contentValues.put(COLUMN_INT_ELEMENT_HEIGHT, this.diameter);
        contentValues.put(COLUMN_STRING_ELEMENT_TEXT, this.centerText);
        contentValues.put(COLUMN_INT_ELEMENT_LAYER, layer);
        contentValues.put(COLUMN_INT_ELEMENT_CENTRAL_X, getElementCentralX());
        contentValues.put(COLUMN_INT_ELEMENT_CENTRAL_Y, getElementCentralY());
        contentValues.put(COLUMN_INT_ELEMENT_THICK, thick);
        contentValues.put(COLUMN_INT_ELEMENT_NORMAL_COLOR, normalColor);
        contentValues.put(COLUMN_INT_ELEMENT_PRESSED_COLOR, pressedColor);
        contentValues.put(COLUMN_INT_ELEMENT_BACKGROUND_COLOR, backgroundColor);
        contentValues.put(COLUMN_INT_ELEMENT_MODE, segmentCount);
        contentValues.put(COLUMN_INT_ELEMENT_SENSE, innerRadiusPercent);
        contentValues.put(COLUMN_INT_ELEMENT_FLAG1, popupAtScreenCenter ? 1 : 0);

        contentValues.put("extra_attributes", new Gson().toJson(buildExtraAttributes()));

        List<String> combinedSegments = new ArrayList<>();
        for (int i = 0; i < segmentValues.size(); i++) {
            String value = segmentValues.get(i);
            String name = (i < segmentNames.size()) ? segmentNames.get(i) : "";
            if (name != null && !name.isEmpty()) {
                combinedSegments.add(value + "|" + name);
            } else {
                combinedSegments.add(value);
            }
        }
        contentValues.put(COLUMN_STRING_ELEMENT_VALUE, String.join(",", combinedSegments));
        elementController.updateElement(elementId, contentValues);
    }

    public static ContentValues getInitialInfo() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_INT_ELEMENT_TYPE, ELEMENT_TYPE_WHEEL_PAD);
        contentValues.put(COLUMN_STRING_ELEMENT_VALUE, "k51,k32,k47,k29,k45,k33,k46,k31");
        contentValues.put(COLUMN_INT_ELEMENT_WIDTH, 400);
        contentValues.put(COLUMN_INT_ELEMENT_HEIGHT, 400);
        contentValues.put(COLUMN_STRING_ELEMENT_TEXT, "");
        contentValues.put(COLUMN_INT_ELEMENT_LAYER, 40);
        contentValues.put(COLUMN_INT_ELEMENT_CENTRAL_X, 250);
        contentValues.put(COLUMN_INT_ELEMENT_CENTRAL_Y, 250);
        contentValues.put(COLUMN_INT_ELEMENT_THICK, 5);
        contentValues.put(COLUMN_INT_ELEMENT_NORMAL_COLOR, 0xF0888888);
        contentValues.put(COLUMN_INT_ELEMENT_PRESSED_COLOR, 0xF00000FF);
        contentValues.put(COLUMN_INT_ELEMENT_BACKGROUND_COLOR, 0xAA444444);
        contentValues.put(COLUMN_INT_ELEMENT_MODE, 8);
        contentValues.put(COLUMN_INT_ELEMENT_SENSE, 30);
        contentValues.put(COLUMN_INT_ELEMENT_FLAG1, 1);

        JsonObject extraAttrs = new JsonObject();
        // **为所有新的字体属性设置默认值**
        extraAttrs.addProperty("normalTextColor", 0xFFFFFFFF); // White
        extraAttrs.addProperty("pressedTextColor", 0xFFFFFFFF); // White
        extraAttrs.addProperty("centerTextColor", 0xFFFFFFFF); // White
        extraAttrs.addProperty("textSizePercent", 35); // 35%
        extraAttrs.addProperty("centerTextSizePercent", 60); // 60%
        extraAttrs.addProperty("triggerTextSizePercent", 40);
        extraAttrs.addProperty("previewGroupChildren", true);
        extraAttrs.addProperty(EXTRA_SEGMENT_ALIGNMENT, SEGMENT_ALIGNMENT_CENTER);
        // 将 JsonObject 转换为字符串，并存入通用的 "extra_attributes" 列
        contentValues.put("extra_attributes", new Gson().toJson(extraAttrs));

        return contentValues;
    }

    private void setupCommonControls(ElementEditText centerTextInput, NumberSeekbar size, NumberSeekbar thick, NumberSeekbar layer,
                                     ElementEditText normalColor, ElementEditText pressedColor, ElementEditText backgroundColor,
                                     Button copy, Button delete) {

        centerTextInput.setTextWithNoTextChangedCallBack(this.centerText);
        // NOTE: The listener is set/overridden in getInfoPage() to handle the switch logic
        centerTextInput.setOnTextChangedListener(text -> {
            setCenterText(text);
            save();
        });

        centralXNumberSeekbar.setProgressMin(centralXMin);
        centralXNumberSeekbar.setProgressMax(centralXMax);
        centralXNumberSeekbar.setValueWithNoCallBack(getElementCentralX());
        centralXNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                setElementCentralX(p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                save();
            }
        });
        centralYNumberSeekbar.setProgressMin(centralYMin);
        centralYNumberSeekbar.setProgressMax(centralYMax);
        centralYNumberSeekbar.setValueWithNoCallBack(getElementCentralY());
        centralYNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                setElementCentralY(p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                save();
            }
        });
        size.setProgressMax(Math.min(widthMax, heightMax));
        size.setProgressMin(widthMin);
        size.setValueWithNoCallBack(this.diameter);
        size.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                setDiameter(p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                save();
            }
        });
        thick.setValueWithNoCallBack(this.thick);
        thick.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                setElementThick(p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                save();
            }
        });
        layer.setValueWithNoCallBack(this.layer);
        layer.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean f) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                setElementLayer(s.getProgress());
                save();
            }
        });

        CrownColorPickerBinder.bind(this, normalColor, () -> this.normalColor, this::setElementNormalColor);
        CrownColorPickerBinder.bind(this, pressedColor, () -> this.pressedColor, this::setElementPressedColor);
        CrownColorPickerBinder.bind(this, backgroundColor, () -> this.backgroundColor, this::setElementBackgroundColor);

        copy.setOnClickListener(v -> {
            ContentValues cv = new ContentValues();
            cv.put(COLUMN_INT_ELEMENT_TYPE, ELEMENT_TYPE_WHEEL_PAD);
            cv.put(COLUMN_INT_ELEMENT_WIDTH, this.diameter);
            cv.put(COLUMN_INT_ELEMENT_HEIGHT, this.diameter);
            cv.put(COLUMN_STRING_ELEMENT_TEXT, this.centerText);
            cv.put(COLUMN_INT_ELEMENT_LAYER, this.layer);
            cv.put(COLUMN_INT_ELEMENT_CENTRAL_X, Math.max(Math.min(getElementCentralX() + getElementWidth() / 2, centralXMax), centralXMin));
            cv.put(COLUMN_INT_ELEMENT_CENTRAL_Y, getElementCentralY());
            cv.put(COLUMN_INT_ELEMENT_THICK, this.thick);
            cv.put(COLUMN_INT_ELEMENT_NORMAL_COLOR, this.normalColor);
            cv.put(COLUMN_INT_ELEMENT_PRESSED_COLOR, this.pressedColor);
            cv.put(COLUMN_INT_ELEMENT_BACKGROUND_COLOR, this.backgroundColor);
            cv.put(COLUMN_INT_ELEMENT_MODE, this.segmentCount);
            cv.put(COLUMN_INT_ELEMENT_SENSE, this.innerRadiusPercent);
            cv.put(COLUMN_INT_ELEMENT_FLAG1, this.popupAtScreenCenter ? 1 : 0);

            cv.put("extra_attributes", new Gson().toJson(buildExtraAttributes()));

            List<String> combinedSegments = new ArrayList<>();
            for (int i = 0; i < segmentValues.size(); i++) {
                String value = segmentValues.get(i);
                String name = (i < segmentNames.size()) ? segmentNames.get(i) : "";
                if (name != null && !name.isEmpty()) {
                    combinedSegments.add(value + "|" + name);
                } else {
                    combinedSegments.add(value);
                }
            }
            cv.put(COLUMN_STRING_ELEMENT_VALUE, String.join(",", combinedSegments));
            elementController.addElement(cv);
        });
        delete.setOnClickListener(v -> {
            elementController.toggleInfoPage(wheelPadPage);
            elementController.deleteElement(wheelPad);
        });
    }

    protected void setDiameter(int newDiameter) {
        this.diameter = newDiameter;
        setElementWidth(newDiameter);
        setElementHeight(newDiameter);
        invalidate();
    }

    private JsonObject buildExtraAttributes() {
        JsonObject extraAttrs = new JsonObject();
        extraAttrs.addProperty("normalTextColor", this.normalTextColor);
        extraAttrs.addProperty("pressedTextColor", this.pressedTextColor);
        extraAttrs.addProperty("centerTextColor", this.centerTextColor);
        extraAttrs.addProperty("textSizePercent", this.textSizePercent);
        extraAttrs.addProperty("centerTextSizePercent", this.centerTextSizePercent);
        extraAttrs.addProperty("triggerTextSizePercent", this.triggerTextSizePercent);
        extraAttrs.addProperty("previewGroupChildren", this.previewGroupChildren);
        extraAttrs.addProperty(EXTRA_SEGMENT_ALIGNMENT,
                this.alignSegmentEdge ? SEGMENT_ALIGNMENT_EDGE : SEGMENT_ALIGNMENT_CENTER);
        return extraAttrs;
    }

    protected void setSegmentCount(int count) {
        if (count < 2) count = 2;
        this.segmentCount = count;
        while (segmentValues.size() < count) {
            segmentValues.add("null");
            segmentNames.add("");
        }
        while (segmentValues.size() > count) {
            segmentValues.remove(segmentValues.size() - 1);
            segmentNames.remove(segmentNames.size() - 1);
        }
        invalidate();
    }

    protected void setSegmentValue(int index, String value) {
        if (index >= 0 && index < segmentValues.size()) {
            segmentValues.set(index, value);
            invalidate();
        }
    }

    protected void setInnerRadiusPercent(int percent) {
        this.innerRadiusPercent = percent;
        invalidate();
    }

    protected void setElementThick(int thick) {
        this.thick = thick;
        invalidate();
    }

    protected void setElementNormalColor(int normalColor) {
        this.normalColor = normalColor;
        invalidate();
    }

    protected void setElementPressedColor(int pressedColor) {
        this.pressedColor = pressedColor;
        invalidate();
    }

    protected void setElementBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
        invalidate();
    }

    protected void setElementNormalTextColor(int normalTextColor) {
        this.normalTextColor = normalTextColor;
        invalidate();
    }

    protected void setElementPressedTextColor(int pressedTextColor) {
        this.pressedTextColor = pressedTextColor;
        invalidate();
    }

    protected void setElementCenterTextColor(int centerTextColor) {
        this.centerTextColor = centerTextColor;
        invalidate();
    }

    protected void setCenterText(String text) {
        this.centerText = text;
        this.isPopupMode = !text.isEmpty();
        invalidate();
    }

    @Override
    protected void updatePage() {
        if (wheelPadPage != null) {
            centralXNumberSeekbar.setValueWithNoCallBack(getElementCentralX());
            centralYNumberSeekbar.setValueWithNoCallBack(getElementCentralY());
        }
    }

    private String getDisplayStringForValue(String value) {
        if (value == null || value.isEmpty() || value.equals("null")) {
            return getContext().getString(R.string.empty_value);
        }

        if (value.startsWith("gb")) {
            try {
                long elementId = Long.parseLong(value.substring(2));
                Element element = elementController.findElementById(elementId);
                if (element instanceof GroupButton) {
                    return getContext().getString(R.string.wheel_value_group, ((GroupButton) element).getText());
                } else {
                    return getContext().getString(R.string.wheel_value_invalid_group);
                }
            } catch (NumberFormatException e) {
                return getContext().getString(R.string.wheel_value_bad_group_id);
            }
        }

        String[] singleKeyValues = value.split("\\+");
        List<String> keyNames = new ArrayList<>();
        for (String singleKeyValue : singleKeyValues) {
            keyNames.add(pageDeviceController.getKeyNameByValue(singleKeyValue));
        }
        return String.join(" + ", keyNames);
    }

    private void updateKeyCombinationDisplay(LinearLayout keysContainer, List<String> currentKeys, Button addButton, Button addGroupButton, final AlertDialog dialog) {
        keysContainer.removeAllViews();
        Context context = keysContainer.getContext();
        boolean isGroup = !currentKeys.isEmpty() && currentKeys.get(0).startsWith("gb");

        // 根据是否已选择组按键，更新按钮的可见性
        if (isGroup) {
            addButton.setVisibility(View.GONE);
            addGroupButton.setVisibility(View.GONE);
        } else {
            addButton.setVisibility(View.VISIBLE);
            addGroupButton.setVisibility(View.VISIBLE);
        }

        if (currentKeys.isEmpty()) {
            TextView emptyText = new TextView(context);
            emptyText.setText(R.string.wheel_empty_keys);
            emptyText.setPadding(0, 10, 0, 10);
            keysContainer.addView(emptyText);
        } else if (isGroup) {
            // --- 显示组按键 ---
            try {
                long elementId = Long.parseLong(currentKeys.get(0).substring(2));
                Element element = elementController.findElementById(elementId);
                if (element instanceof GroupButton) {
                    final GroupButton groupButton = (GroupButton) element;
                    final SuperPageLayout groupButtonSettingsPage = groupButton.getInfoPage();
                    Button groupBtnDisplay = new Button(context);
                    groupBtnDisplay.setText(context.getString(
                            R.string.wheel_group_button_action,
                            context.getString(R.string.wheel_value_group, groupButton.getText())));
                    groupBtnDisplay.setAllCaps(false);

                    // 单击打开组按键设置页
                    groupBtnDisplay.setOnClickListener(v -> {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                        final SuperPageLayout wheelPadSettingsPage = wheelPad.getInfoPage();
                        groupButtonSettingsPage.setLastPage(wheelPadSettingsPage);
                        groupButtonSettingsPage.setPageReturnListener(new SuperPageLayout.ReturnListener() {
                            @Override
                            public void returnCallBack() {
                                elementController.getSuperPagesController().openNewPage(wheelPadSettingsPage);
                            }
                        });
                        elementController.getSuperPagesController().openNewPage(groupButtonSettingsPage);
                    });

                    // 长按删除
                    groupBtnDisplay.setOnLongClickListener(v -> {
                        // 清空当前分区的按键设置
                        currentKeys.clear();

                        // 更新UI以反映移除
                        updateKeyCombinationDisplay(keysContainer, currentKeys, addButton, addGroupButton, dialog);

                        Toast.makeText(context, R.string.wheel_group_removed, Toast.LENGTH_SHORT).show();

                        return true; // 返回true表示事件已被消费
                    });
                    keysContainer.addView(groupBtnDisplay);
                } else {
                    // 如果找到了元素但类型不对，也显示错误
                    currentKeys.clear();
                    TextView errorText = new TextView(context);
                    errorText.setText(context.getString(R.string.wheel_group_wrong_type, elementId));
                    keysContainer.addView(errorText);
                    updateKeyCombinationDisplay(keysContainer, currentKeys, addButton, addGroupButton, dialog);
                }
            } catch (Exception e) {
                // 如果解析或查找失败，显示错误信息并允许重新选择
                currentKeys.clear();
                TextView errorText = new TextView(context);
                errorText.setText(R.string.wheel_group_missing);
                keysContainer.addView(errorText);
            }
        } else {
            // --- 显示普通按键列表 ---
            for (int i = 0; i < currentKeys.size(); i++) {
                final int keyIndex = i;
                TextView keyView = new TextView(context);
                keyView.setText(context.getString(
                        R.string.wheel_key_tap_remove,
                        pageDeviceController.getKeyNameByValue(currentKeys.get(keyIndex))));
                keyView.setTextSize(16);
                keyView.setBackgroundResource(R.drawable.enabled_square);
                keyView.setPadding(20, 15, 20, 15);
                keyView.setGravity(Gravity.CENTER);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 5, 0, 5);
                keyView.setLayoutParams(params);

                keyView.setOnClickListener(v -> {
                    currentKeys.remove(keyIndex);
                    updateKeyCombinationDisplay(keysContainer, currentKeys, addButton, addGroupButton, dialog);
                });
                keysContainer.addView(keyView);
            }
        }
    }

    private void showKeyCombinationDialog(Context context, final int index, final TextView valueText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppDialogStyle);
        builder.setTitle(context.getString(R.string.wheel_edit_keys_title, index + 1));

        LinearLayout dialogLayout = new LinearLayout(context);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(30, 20, 30, 20);

        LinearLayout keysContainer = new LinearLayout(context);
        keysContainer.setOrientation(LinearLayout.VERTICAL);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        scrollParams.setMargins(0, 0, 0, 20);
        scrollView.setLayoutParams(scrollParams);
        scrollView.addView(keysContainer);

        String currentValue = segmentValues.get(index);
        final List<String> currentKeys = new ArrayList<>();
        if (currentValue != null && !currentValue.isEmpty() && !currentValue.equals("null")) {
            // 组按键只有一个值 "gb<ID>", 普通按键用 "+" 分隔
            if (currentValue.startsWith("gb")) {
                currentKeys.add(currentValue);
            } else {
                currentKeys.addAll(Arrays.asList(currentValue.split("\\+")));
            }
        }

        // --- 新增按钮布局和按钮 ---
        LinearLayout buttonContainer = new LinearLayout(context);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(Gravity.CENTER);

        Button addButton = new Button(context);
        addButton.setText(R.string.wheel_add_key);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        buttonParams.setMarginEnd(10);
        addButton.setLayoutParams(buttonParams);

        Button addGroupButton = new Button(context);
        addGroupButton.setText(R.string.wheel_add_group_key);
        LinearLayout.LayoutParams groupButtonParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        groupButtonParams.setMarginStart(10);
        addGroupButton.setLayoutParams(groupButtonParams);

        buttonContainer.addView(addButton);
        buttonContainer.addView(addGroupButton);

        dialogLayout.addView(scrollView);
        dialogLayout.addView(buttonContainer);

        final AlertDialog dialog = builder.create();

        // 初始状态更新
        updateKeyCombinationDisplay(keysContainer, currentKeys, addButton, addGroupButton, dialog);
        dialog.setView(dialogLayout);

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.dialog_button_ok), (d, which) -> {
            String finalValue;
            if (currentKeys.isEmpty()) {
                finalValue = "null";
            } else if (currentKeys.get(0).startsWith("gb")) {
                finalValue = currentKeys.get(0);
            } else {
                finalValue = String.join("+", currentKeys);
            }
            setSegmentValue(index, finalValue);
            valueText.setText(getDisplayStringForValue(finalValue));
            save();
        });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(R.string.dialog_button_cancel), (d, which) -> d.dismiss());

        // "添加组按键" 的点击逻辑
        addGroupButton.setOnClickListener(v -> {
            // 1. 查找所有现有的 GroupButton
            List<GroupButton> groupButtons = new ArrayList<>();
            for (Element el : elementController.getElements()) {
                if (el instanceof GroupButton) {
                    groupButtons.add((GroupButton) el);
                }
            }

            // 2. 创建选项列表
            final List<String> options = new ArrayList<>();
            for (GroupButton gb : groupButtons) {
                options.add(gb.getText() + " (ID: " + gb.elementId + ")");
            }
            options.add(context.getString(R.string.wheel_create_new_group));

            // 3. 显示选择对话框
            AlertDialog groupSelectionDialog = new AlertDialog.Builder(context, R.style.AppDialogStyle)
                    .setTitle(R.string.wheel_select_group_title)
                    .setItems(options.toArray(new String[0]), (selectionDialog, which) -> {

                        String selectedValue = null;
                        Element newElementToEdit = null;

                        if (which == options.size() - 1) {
                            // --- 用户选择 "创建新的" ---
                            ContentValues cv = GroupButton.getInitialInfo();
                            Element newElement = elementController.addElement(cv);
                            if (newElement != null) {
                                selectedValue = "gb" + newElement.elementId;
                                newElementToEdit = newElement; // 记录下来，以便后续打开其设置页
                            }
                        } else {
                            // --- 用户选择一个现有的 ---
                            GroupButton selectedGb = groupButtons.get(which);
                            selectedValue = "gb" + selectedGb.elementId;
                        }

                        if (selectedValue != null) {
                            // 立即设置值并保存
                            setSegmentValue(index, selectedValue);
                            save();

                            // 更新主设置页面上该分区的显示文本
                            valueText.setText(getDisplayStringForValue(selectedValue));

                            // 关闭“编辑按键”这个主弹窗
                            dialog.dismiss();

                            // 如果是新创建的，提示用户去配置
                            if (newElementToEdit != null) {
                                Toast.makeText(context, R.string.wheel_new_group_created, Toast.LENGTH_LONG).show();
                                elementController.toggleInfoPage(newElementToEdit.getInfoPage());
                            }
                        }
                    })
                    .show();
            AppDialogStyler.INSTANCE.applySystemChoiceList(groupSelectionDialog, context);
        });

        addButton.setOnClickListener(v -> {
            dialog.hide();
            PageDeviceController.DeviceCallBack deviceCallBack = new PageDeviceController.DeviceCallBack() {
                @Override
                public void OnKeyClick(TextView key) {
                    currentKeys.add(key.getTag().toString());
                    dialog.show();
                    updateKeyCombinationDisplay(keysContainer, currentKeys, addButton, addGroupButton, dialog);
                }
            };
            pageDeviceController.open(deviceCallBack, View.VISIBLE, View.VISIBLE, View.VISIBLE);
        });

        dialog.show();
        AppDialogStyler.INSTANCE.apply(dialog, context);
    }

    private void updateValuesContainerUI() {
        if (valuesContainer == null) return;
        valuesContainer.removeAllViews();
        for (int i = 0; i < segmentCount; i++) {
            if (i >= segmentValues.size()) continue;
            View valueView = LayoutInflater.from(getContext()).inflate(R.layout.item_key_value, valuesContainer, false);
            TextView title = valueView.findViewById(R.id.item_key_value_title);
            ElementEditText nameInput = valueView.findViewById(R.id.item_key_value_name);
            TextView valueText = valueView.findViewById(R.id.item_key_value_value);

            title.setText(getContext().getString(R.string.wheel_section_title, i + 1));
            final int index = i;

            if (index < segmentNames.size()) {
                nameInput.setTextWithNoTextChangedCallBack(segmentNames.get(index));
            }
            nameInput.setOnTextChangedListener(text -> {
                if (index < segmentNames.size()) {
                    segmentNames.set(index, text);
                    invalidate();
                    save();
                }
            });

            valueText.setText(getDisplayStringForValue(segmentValues.get(i)));

            valueText.setOnClickListener(v -> {
                showKeyCombinationDialog(getContext(), index, valueText);
            });
            valuesContainer.addView(valueView);
        }
    }

}
