package io.benwiegand.atvremote.phone.ui.view;

import android.os.VibrationEffect;

import java.util.function.Consumer;

import io.benwiegand.atvremote.phone.protocol.KeyEventType;

public interface RemoteButton {
    enum DownUpFeedbackType {
        LONG_PRESSABLE,
        RAPID_FIRE,
        SINGLE_CLICKABLE
    }

    void setDownUpKeyEvent(Consumer<KeyEventType> onEvent, int repeatDelay, int repeatInterval, DownUpFeedbackType feedbackType);
    void setClickKeyEvent(Runnable onClick, Runnable onLongClick);
    void setClickKeyEvent(Runnable onClick);

}
