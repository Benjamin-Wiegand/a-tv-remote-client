package io.benwiegand.atvremote.phone.ui.view;

import android.os.VibrationEffect;

import java.util.function.Consumer;

import io.benwiegand.atvremote.phone.protocol.KeyEventType;

public interface RemoteButton {
    VibrationEffect CLICK_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
    VibrationEffect LONG_CLICK_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);
    VibrationEffect DOWN_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
    VibrationEffect UP_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);


    void setDownUpKeyEvent(Consumer<KeyEventType> onEvent, int repeatDelay, int repeatInterval);
    void setDownUpKeyEvent(Consumer<KeyEventType> onEvent);
    void setClickKeyEvent(Runnable onClick, Runnable onLongClick);
    void setClickKeyEvent(Runnable onClick);

}
