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
    private final int repeatInterval;
    private final int repeatDelay;
    private final SerialInt holdSerial = new SerialInt();
    private boolean clickCancelled = false;

    /**
     * @param superOnTouchEvent the original onTouchEvent()
     * @param superPerformClick the original performClick()
     * @param onButtonDown runnable to be run on button down
     * @param onButtonUp runnable to be run on button up
     * @param repeatDelay delay after which to repeat onButtonDown. pass -1 to disable repeating
     * @param repeatInterval interval at which to repeat onButtonDown when held
     */
    public DownUpHandler(
            Function<MotionEvent, Boolean> superOnTouchEvent,
            Supplier<Boolean> superPerformClick,
            Runnable onButtonDown,
            Runnable onButtonUp,
            int repeatDelay,
            int repeatInterval) {
        super(superOnTouchEvent, superPerformClick);
        this.onButtonDown = onButtonDown;
        this.onButtonUp = onButtonUp;
        this.repeatInterval = repeatInterval;
        this.repeatDelay = repeatDelay;
    }

    /**
     * same as {@link DownUpHandler#DownUpHandler(Function, Supplier, Runnable, Runnable, int, int)} but passes -1 repeatDelay to disable repeating.
     * @param superOnTouchEvent the original onTouchEvent()
     * @param superPerformClick the original performClick()
     * @param onButtonDown runnable to be run on button down
     * @param onButtonUp runnable to be run on button up
     */
    public DownUpHandler(
            Function<MotionEvent, Boolean> superOnTouchEvent,
            Supplier<Boolean> superPerformClick,
            Runnable onButtonDown,
            Runnable onButtonUp) {
        this(superOnTouchEvent, superPerformClick, onButtonDown, onButtonUp, -1, -1);
    }

    private void handleHold(int serial) {
        if (!holdSerial.isValid(serial)) return;
        performClick();
        handler.postDelayed(() -> handleHold(serial), repeatInterval);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case ACTION_DOWN, ACTION_POINTER_DOWN, ACTION_BUTTON_PRESS -> {
                if (repeatDelay == -1) {
                    performClick();
                    break;
                }
                int serial = holdSerial.get();
                handler.postDelayed(() -> handleHold(serial), repeatDelay);
                performClick();
            }
            case ACTION_UP, ACTION_POINTER_UP, ACTION_BUTTON_RELEASE, ACTION_CANCEL -> {
                holdSerial.advance();
                clickCancelled = true;
                onButtonUp.run();
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
        onButtonDown.run();
        return super.performClick();
    }
}
