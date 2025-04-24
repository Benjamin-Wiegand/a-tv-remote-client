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
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatImageButton;

import io.benwiegand.atvremote.phone.stuff.SerialInt;

public class RemoteButton extends AppCompatImageButton {

    private int repeatInterval = -1;
    private int repeatDelay = 1000;
    private final SerialInt holdSerial = new SerialInt();
    private boolean holdTriggered = false;
    private boolean clickCancelled = false;

    public RemoteButton(Context context) {
        super(context, null);
    }

    public RemoteButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RemoteButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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
