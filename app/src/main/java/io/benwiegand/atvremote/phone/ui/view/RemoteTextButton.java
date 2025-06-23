package io.benwiegand.atvremote.phone.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatButton;

import java.util.function.Consumer;

import io.benwiegand.atvremote.phone.protocol.KeyEventType;
import io.benwiegand.atvremote.phone.ui.buttonhandler.ButtonHandler;
import io.benwiegand.atvremote.phone.ui.buttonhandler.DownUpHandler;

public class RemoteTextButton extends AppCompatButton implements RemoteButton {

    private Vibrator vibrator;
    private ButtonHandler buttonHandler = new ButtonHandler(super::onTouchEvent, super::performClick) {};

    public RemoteTextButton(Context context) {
        super(context);
        init();
    }

    public RemoteTextButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RemoteTextButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        vibrator = getContext().getSystemService(Vibrator.class);
    }

    @Override
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

    @Override
    public void setDownUpKeyEvent(Consumer<KeyEventType> onEvent) {
        setDownUpKeyEvent(onEvent, -1, -1);
    }

    @Override
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

    @Override
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
