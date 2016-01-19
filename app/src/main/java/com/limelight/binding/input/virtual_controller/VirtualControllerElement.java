/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

public abstract class VirtualControllerElement extends View {
    protected static boolean _PRINT_DEBUG_INFORMATION = false;

    protected VirtualController virtualController;

    private final Paint paint = new Paint();

    private int normalColor = 0xF0888888;
    protected int pressedColor = 0xF00000FF;
    private int configNormalColor = 0xF0FF0000;
    private int configSelectedColor = 0xF000FF00;

    protected int startSize_x;
    protected int startSize_y;

    float position_pressed_x = 0;
    float position_pressed_y = 0;

    private enum Mode {
        Normal,
        Resize,
        Move
    }

    private Mode currentMode = Mode.Normal;

    protected VirtualControllerElement(VirtualController controller, Context context) {
        super(context);

        this.virtualController = controller;
    }

    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        int newPos_x = (int) getX() + x - pressed_x;
        int newPos_y = (int) getY() + y - pressed_y;

        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = newPos_x > 0 ? newPos_x : 0;
        layoutParams.topMargin = newPos_y > 0 ? newPos_y : 0;
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;

        requestLayout();
    }

    protected void resizeElement(int pressed_x, int pressed_y, int width, int height) {
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) getLayoutParams();

        int newHeight = height + (startSize_y - pressed_y);
        int newWidth = width + (startSize_x - pressed_x);

        layoutParams.height = newHeight > 20 ? newHeight : 20;
        layoutParams.width = newWidth > 20 ? newWidth : 20;

        requestLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentMode != Mode.Normal) {
            paint.setColor(configSelectedColor);
            paint.setStrokeWidth(10);
            paint.setStyle(Paint.Style.STROKE);

            canvas.drawRect(0, 0,
                    getWidth(), getHeight(),
                    paint);
        }

        onElementDraw(canvas);

        super.onDraw(canvas);
    }

    /*
    protected void actionShowNormalColorChooser() {
		AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
			@Override
			public void onCancel(AmbilWarnaDialog dialog)
			{}

			@Override
			public void onOk(AmbilWarnaDialog dialog, int color) {
				normalColor = color;
				invalidate();
			}
		});
		colorDialog.show();
	}

	protected void actionShowPressedColorChooser() {
		AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
			@Override
			public void onCancel(AmbilWarnaDialog dialog) {
			}

			@Override
			public void onOk(AmbilWarnaDialog dialog, int color) {
				pressedColor = color;
				invalidate();
			}
		});
		colorDialog.show();
	}
    */

    protected void actionEnableMove() {
        currentMode = Mode.Move;
    }

    protected void actionEnableResize() {
        currentMode = Mode.Resize;
    }

    protected void actionCancel() {
        currentMode = Mode.Normal;
        invalidate();
    }

    protected int getDefaultColor() {
        return (virtualController.getControllerMode() == VirtualController.ControllerMode.Configuration) ?
                configNormalColor : normalColor;
    }

    protected void showConfigurationDialog() {
        try {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());

            alertBuilder.setTitle("Configuration");

            CharSequence functions[] = new CharSequence[]{
                    "Move",
                    "Resize",
                /*election
                "Set n
                Disable color sormal color",
                "Set pressed color",
                */
                    "Cancel"
            };

            alertBuilder.setItems(functions, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case 0: { // move
                            actionEnableMove();
                            break;
                        }
                        case 1: { // resize
                            actionEnableResize();
                            break;
                        }
                    /*
                    case 2:	{ // set default color
                        actionShowNormalColorChooser();
                        break;
                    }
                    case 3:	{ // set pressed color
                        actionShowPressedColorChooser();
                        break;
                    }
                    */
                        default: { // cancel
                            actionCancel();
                            break;
                        }
                    }
                }
            });
            AlertDialog alert = alertBuilder.create();
            // show menu
            alert.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (virtualController.getControllerMode() == VirtualController.ControllerMode.Active) {
            return onElementTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                position_pressed_x = event.getX();
                position_pressed_y = event.getY();
                startSize_x = getWidth();
                startSize_y = getHeight();

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                switch (currentMode) {
                    case Move: {
                        moveElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Resize: {
                        resizeElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Normal: {
                        break;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                currentMode = Mode.Normal;
                showConfigurationDialog();
                return true;
            }
            default: {
            }
        }
        return true;
    }

    abstract protected void onElementDraw(Canvas canvas);

    abstract public boolean onElementTouchEvent(MotionEvent event);

    protected static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            System.out.println(text);
        }
    }

    public void setColors(int normalColor, int pressedColor) {
        this.normalColor = normalColor;
        this.pressedColor = pressedColor;

        invalidate();
    }

    protected final float getPercent(float value, float percent) {
        return value / 100 * percent;
    }

    protected final int getCorrectWidth() {
        return getWidth() > getHeight() ? getHeight() : getWidth();
    }

    /**
     public JSONObject getConfiguration () {
     JSONObject configuration = new JSONObject();
     return  configuration;
     }

     public  void loadConfiguration (JSONObject configuration) {
     }
     */
}
