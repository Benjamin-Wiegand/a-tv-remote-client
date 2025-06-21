package io.benwiegand.atvremote.phone.control;

import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.protocol.KeyEventType;

public interface InputHandler {

    Sec<Void> dpadDown(KeyEventType type);
    Sec<Void> dpadUp(KeyEventType type);
    Sec<Void> dpadLeft(KeyEventType type);
    Sec<Void> dpadRight(KeyEventType type);
    Sec<Void> dpadSelect(KeyEventType type);
    Sec<Void> dpadLongPress();

    Sec<Void> navHome(KeyEventType type);
    Sec<Void> navBack(KeyEventType type);
    Sec<Void> navRecent(KeyEventType type);
    Sec<Void> navNotifications(KeyEventType type);
    Sec<Void> navQuickSettings();

    Sec<Void> volumeUp(KeyEventType type);
    Sec<Void> volumeDown(KeyEventType type);
    Sec<Void> toggleMute(KeyEventType type);

    Sec<Void> playPause(KeyEventType type);
    Sec<Void> nextTrack(KeyEventType type);
    Sec<Void> prevTrack(KeyEventType type);
    Sec<Void> skipBackward(KeyEventType type);
    Sec<Void> skipForward(KeyEventType type);

    boolean softKeyboardEnabled();
    boolean softKeyboardVisible();
    Sec<Void> showSoftKeyboard();
    Sec<Void> hideSoftKeyboard();
    Sec<Void> setSoftKeyboardEnabled(boolean enabled);
    Sec<Void> keyboardInput(String input);

    boolean cursorSupported();
    Sec<Void> showCursor();
    Sec<Void> hideCursor();
    Sec<Void> cursorMove(int x, int y);
    Sec<Void> leftClick(KeyEventType type);
    Sec<Void> cursorContext();

    Sec<Void> scrollVertical(double trajectory, boolean glide);
    Sec<Void> scrollHorizontal(double trajectory, boolean glide);

    Sec<Void> pressExtraButton(String button);

    // text
    Sec<Boolean> commitText(String input, int newCursorPosition);
    Sec<Boolean> deleteSurroundingText(int beforeLength, int afterLength);
    Sec<Boolean> sendKeyEvent(int keyCode, KeyEventType type);
}
