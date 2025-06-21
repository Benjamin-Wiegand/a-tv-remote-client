package io.benwiegand.atvremote.phone.ui.buttonhandler;

import android.view.MotionEvent;

import java.util.function.Function;
import java.util.function.Supplier;

public abstract class ButtonHandler {
    private final Function<MotionEvent, Boolean> superOnTouchEvent;
    private final Supplier<Boolean> superPerformClick;

    public ButtonHandler(Function<MotionEvent, Boolean> superOnTouchEvent, Supplier<Boolean> superPerformClick) {
        this.superOnTouchEvent = superOnTouchEvent;
        this.superPerformClick = superPerformClick;
    }

    public boolean onTouchEvent(MotionEvent event) {
        return superOnTouchEvent.apply(event);
    }

    public boolean performClick() {
        return superPerformClick.get();
    }
}
