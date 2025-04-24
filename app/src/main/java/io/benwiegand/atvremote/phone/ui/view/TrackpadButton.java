package io.benwiegand.atvremote.phone.ui.view;

import static android.view.MotionEvent.ACTION_BUTTON_PRESS;
import static android.view.MotionEvent.ACTION_BUTTON_RELEASE;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatButton;

public class TrackpadButton extends AppCompatButton {
    private Runnable onPress = null;
    private Runnable onRelease = null;

    public TrackpadButton(Context context) {
        super(context);
    }

    public TrackpadButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TrackpadButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnPress(Runnable onPress) {
        this.onPress = onPress;
    }

    public void setOnRelease(Runnable onRelease) {
        this.onRelease = onRelease;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction()) {
            case ACTION_DOWN, ACTION_POINTER_DOWN, ACTION_BUTTON_PRESS -> {
                if (onPress != null) onPress.run();
                performClick();
            }
            case ACTION_UP, ACTION_POINTER_UP, ACTION_BUTTON_RELEASE, ACTION_CANCEL -> {
                if (onRelease != null) onRelease.run();
            }

        }

        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
