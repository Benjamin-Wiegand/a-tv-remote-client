package io.benwiegand.atvremote.phone.ui.view;

import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.benwiegand.atvremote.phone.protocol.KeyEventType;
import io.benwiegand.atvremote.phone.ui.buttonhandler.ButtonHandler;
import io.benwiegand.atvremote.phone.ui.buttonhandler.DownUpHandler;

public class RemoteButtonHandler implements RemoteButton {

    private static final VibrationEffect CLICK_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
    private static final VibrationEffect LONG_CLICK_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);

    private static final VibrationEffect DOWN_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
    private static final VibrationEffect UP_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);

    private final Function<MotionEvent, Boolean> superOnTouchEvent;
    private final Supplier<Boolean> superPerformClick;
    private final Consumer<Boolean> setLongClickable;
    private final Consumer<View.OnLongClickListener> setOnLongClickListener;
    private final Vibrator vibrator;

    private ButtonHandler buttonHandler;

    public RemoteButtonHandler(View remoteButton, Function<MotionEvent, Boolean> superOnTouchEvent, Supplier<Boolean> superPerformClick) {
        this.superOnTouchEvent = superOnTouchEvent;
        this.superPerformClick = superPerformClick;
        setLongClickable = remoteButton::setLongClickable;
        setOnLongClickListener = remoteButton::setOnLongClickListener;

        buttonHandler = new ButtonHandler(RemoteButtonHandler.this.superOnTouchEvent, RemoteButtonHandler.this.superPerformClick) {};

        vibrator = remoteButton.getContext().getSystemService(Vibrator.class);
    }

    @Override
    public void setDownUpKeyEvent(Consumer<KeyEventType> onEvent, int repeatDelay, int repeatInterval, DownUpFeedbackType feedbackType) {
        AtomicInteger downCounter = new AtomicInteger();
        buttonHandler = new DownUpHandler(
                superOnTouchEvent,
                superPerformClick,
                () -> {
                    onEvent.accept(KeyEventType.DOWN);

                    int downCount = downCounter.getAndIncrement();
                    if (downCount == 0) {
                        vibrator.vibrate(DOWN_VIBRATION_EFFECT);
                        return;
                    }
                    switch (feedbackType) {
                        case LONG_PRESSABLE -> {
                            if (downCount == 1)
                                vibrator.vibrate(LONG_CLICK_VIBRATION_EFFECT);
                        }
                        case RAPID_FIRE -> vibrator.vibrate(DOWN_VIBRATION_EFFECT);
                        case SINGLE_CLICKABLE -> {}
                    }
                },
                () -> {
                    onEvent.accept(KeyEventType.UP);
                    downCounter.set(0);
                    vibrator.vibrate(UP_VIBRATION_EFFECT);
                },
                repeatDelay,
                repeatInterval);
        setLongClickable.accept(false);
    }

    @Override
    public void setClickKeyEvent(Runnable onClick, Runnable onLongClick) {
        buttonHandler = new DownUpHandler(
                superOnTouchEvent,
                superPerformClick,
                () -> {
                    onClick.run();
                    vibrator.vibrate(CLICK_VIBRATION_EFFECT);
                },
                () -> {/* do nothing */});

        setOnLongClickListener.accept(v -> {
            onLongClick.run();
            vibrator.vibrate(LONG_CLICK_VIBRATION_EFFECT);
            return true;
        });
        setLongClickable.accept(true);
    }

    @Override
    public void setClickKeyEvent(Runnable onClick) {
        setClickKeyEvent(onClick, () -> {});
        setLongClickable.accept(false);
    }

    public boolean onTouchEvent(MotionEvent event) {
        return buttonHandler.onTouchEvent(event);
    }

    public boolean performClick() {
        return buttonHandler.performClick();
    }
}
