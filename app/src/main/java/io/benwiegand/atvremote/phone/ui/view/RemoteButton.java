package io.benwiegand.atvremote.phone.ui.view;

import static android.view.MotionEvent.ACTION_BUTTON_PRESS;
import static android.view.MotionEvent.ACTION_BUTTON_RELEASE;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RotateDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.stuff.SerialInt;

public class RemoteButton extends AppCompatImageButton {

    private int repeatInterval = -1;
    private int repeatDelay = 1000;
    private float drawableRotation = 0;
    private final SerialInt holdSerial = new SerialInt();
    private boolean holdTriggered = false;
    private boolean clickCancelled = false;

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

    public void setRepeat(int repeatDelay, int repeatInterval) {
        this.repeatDelay = repeatDelay;
        this.repeatInterval = repeatInterval;
    }

    @SuppressLint("ClickableViewAccessibility") // it does call performClick() just not directly
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case ACTION_DOWN, ACTION_POINTER_DOWN, ACTION_BUTTON_PRESS -> {
                if (repeatInterval == -1) break;
                int serial = holdSerial.get();
                holdTriggered = false;
                getHandler().postDelayed(() -> handleHold(serial), repeatDelay);
            }
            case ACTION_UP, ACTION_POINTER_UP, ACTION_BUTTON_RELEASE, ACTION_CANCEL -> {
                holdSerial.advance();
                if (holdTriggered) clickCancelled = true;
            }

        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        if (clickCancelled) {
            clickCancelled = false;
            return false;
        }
        return super.performClick();
    }

    private void handleHold(int serial) {
        if (!holdSerial.isValid(serial)) return;
        holdTriggered = true;
        performClick();
        getHandler().postDelayed(() -> handleHold(serial), repeatInterval);
    }
}
