package io.benwiegand.atvremote.phone.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RotateDrawable;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;

import java.util.function.Consumer;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.protocol.KeyEventType;
import io.benwiegand.atvremote.phone.ui.buttonhandler.ButtonHandler;
import io.benwiegand.atvremote.phone.ui.buttonhandler.DownUpHandler;

public class RemoteButton extends AppCompatImageButton {

    private static final VibrationEffect CLICK_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
    private static final VibrationEffect LONG_CLICK_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);

    private static final VibrationEffect DOWN_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
    private static final VibrationEffect UP_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);

    private Vibrator vibrator;

    private float drawableRotation = 0;
    private ButtonHandler buttonHandler = new ButtonHandler(super::onTouchEvent, super::performClick) {};

    public RemoteButton(Context context) {
        super(context);
        init(null);
    }

    public RemoteButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public RemoteButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        vibrator = getContext().getSystemService(Vibrator.class);
        if (attrs == null) return;

        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            if (attrs.getAttributeNameResource(i) != R.attr.rotateDrawable) continue;

            drawableRotation = attrs.getAttributeFloatValue(i, 0);
            if (drawableRotation == 0) break;
            setImageDrawableInternal(rotateDrawable(getDrawable()));
            break;
        }
    }

    private Drawable rotateDrawable(Drawable drawable) {
        if (drawableRotation == 0 || drawable == null) return drawable;
        RotateDrawable rotateDrawable = new RotateDrawable();
        rotateDrawable.setDrawable(drawable);
        rotateDrawable.setToDegrees(drawableRotation);
        rotateDrawable.setLevel(10000); // MAX_LEVEL (100% of lerp in RotateDrawable.onLevelChange())
        return rotateDrawable;
    }

    private void setImageDrawableInternal(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
       setImageDrawableInternal(rotateDrawable(drawable));
    }

    public void setDownUpKeyEvent(Consumer<KeyEventType> onEvent, int repeatDelay, int repeatInterval) {
        buttonHandler = new DownUpHandler(
                super::onTouchEvent,
                super::performClick,
                () -> {
                    onEvent.accept(KeyEventType.DOWN);
                    vibrator.vibrate(DOWN_VIBRATION_EFFECT);
                },
                () -> {
                    onEvent.accept(KeyEventType.UP);
                    vibrator.vibrate(UP_VIBRATION_EFFECT);
                },
                repeatDelay,
                repeatInterval);
        setLongClickable(false);
    }

    public void setDownUpKeyEvent(Consumer<KeyEventType> onEvent) {
        setDownUpKeyEvent(onEvent, -1, -1);
    }

    public void setClickKeyEvent(Runnable onClick, Runnable onLongClick) {
        buttonHandler = new DownUpHandler(
                super::onTouchEvent,
                super::performClick,
                () -> {
                    onClick.run();
                    vibrator.vibrate(CLICK_VIBRATION_EFFECT);
                },
                () -> {/* do nothing */});
        setOnLongClickListener(v -> {
            onLongClick.run();
            vibrator.vibrate(LONG_CLICK_VIBRATION_EFFECT);
            return true;
        });
    }

    public void setClickKeyEvent(Runnable onClick) {
        setClickKeyEvent(onClick, () -> {});
        setLongClickable(false);
    }

    @SuppressLint("ClickableViewAccessibility") // it does call performClick() just not directly
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return buttonHandler.onTouchEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility") // also calls performClick() indirectly
    @Override
    public boolean performClick() {
        return buttonHandler.performClick();
    }

}
