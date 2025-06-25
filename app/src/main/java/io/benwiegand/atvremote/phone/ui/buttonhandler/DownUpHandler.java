package io.benwiegand.atvremote.phone.ui.buttonhandler;

import static android.view.MotionEvent.ACTION_BUTTON_PRESS;
import static android.view.MotionEvent.ACTION_BUTTON_RELEASE;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import java.util.function.Function;
import java.util.function.Supplier;

import io.benwiegand.atvremote.phone.stuff.SerialInt;

/**
 * has separate triggers for button down/up.
 * optionally repeats down event when held.
 */
public class DownUpHandler extends ButtonHandler {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable onButtonDown;
    private final Runnable onButtonUp;
    private final Runnable onButtonClick;
    private final int repeatInterval;
    private final int repeatDelay;
    private final SerialInt holdSerial = new SerialInt();
    private boolean clickCancelled = false;
    private boolean accessibilityClick = true;

    /**
     * @param superOnTouchEvent the original onTouchEvent()
     * @param superPerformClick the original performClick()
     * @param onButtonDown runnable to be run on button down
     * @param onButtonUp runnable to be run on button up
     * @param onButtonClick runnable to be run on button click (usually for accessibility)
     * @param repeatDelay delay after which to repeat onButtonDown. pass -1 to disable repeating
     * @param repeatInterval interval at which to repeat onButtonDown when held
     */
    public DownUpHandler(
            Function<MotionEvent, Boolean> superOnTouchEvent,
            Supplier<Boolean> superPerformClick,
            Runnable onButtonDown,
            Runnable onButtonUp,
            Runnable onButtonClick,
            int repeatDelay,
            int repeatInterval) {
        super(superOnTouchEvent, superPerformClick);
        this.onButtonDown = onButtonDown;
        this.onButtonUp = onButtonUp;
        this.onButtonClick = onButtonClick;
        this.repeatInterval = repeatInterval;
        this.repeatDelay = repeatDelay;
    }

    /**
     * same as {@link DownUpHandler#DownUpHandler(Function, Supplier, Runnable, Runnable, Runnable, int, int)} but passes -1 repeatDelay to disable repeating.
     * @param superOnTouchEvent the original onTouchEvent()
     * @param superPerformClick the original performClick()
     * @param onButtonDown runnable to be run on button down
     * @param onButtonUp runnable to be run on button up
     * @param onButtonClick runnable to be run on button click (usually for accessibility)
     */
    public DownUpHandler(
            Function<MotionEvent, Boolean> superOnTouchEvent,
            Supplier<Boolean> superPerformClick,
            Runnable onButtonDown,
            Runnable onButtonUp,
            Runnable onButtonClick) {
        this(superOnTouchEvent, superPerformClick, onButtonDown, onButtonUp, onButtonClick, -1, -1);
    }

    private void handleHold(int serial) {
        if (!holdSerial.isValid(serial)) return;
        performClickInternal();
        handler.postDelayed(() -> handleHold(serial), repeatInterval);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case ACTION_DOWN, ACTION_POINTER_DOWN, ACTION_BUTTON_PRESS -> {
                if (repeatDelay == -1) {
                    performClickInternal();
                    break;
                }
                int serial = holdSerial.get();
                handler.postDelayed(() -> handleHold(serial), repeatDelay);
                performClickInternal();
            }
            case ACTION_UP, ACTION_POINTER_UP, ACTION_BUTTON_RELEASE, ACTION_CANCEL -> {
                holdSerial.advance();
                clickCancelled = true;
                onButtonUp.run();

                accessibilityClick = false; // super.onTouchEvent() is about to call performClick()
            }

        }
        return super.onTouchEvent(event);
    }

    public void performClickInternal() {
        if (clickCancelled)
            clickCancelled = false;
        onButtonDown.run();
        super.performClick();
    }

    @Override
    public boolean performClick() {
        if (!accessibilityClick) {   // this was not for accessibility
            accessibilityClick = true;
            return false;
        }
        onButtonClick.run();
        return super.performClick();
    }
}
