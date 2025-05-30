package io.benwiegand.atvremote.phone.control;

import io.benwiegand.atvremote.phone.async.Sec;

public interface InputHandler {

    Sec<Void> dpadDown();
    Sec<Void> dpadUp();
    Sec<Void> dpadLeft();
    Sec<Void> dpadRight();
    Sec<Void> dpadSelect();
    Sec<Void> dpadLongPress();

    Sec<Void> navHome();
    Sec<Void> navBack();
    Sec<Void> navRecent();
    Sec<Void> navApps();
    Sec<Void> navNotifications();
    Sec<Void> navQuickSettings();

    Sec<Void> volumeUp();
    Sec<Void> volumeDown();
    Sec<Void> mute();

    Sec<Void> pause();
    Sec<Void> nextTrack();
    Sec<Void> prevTrack();
    Sec<Void> skipBackward();
    Sec<Void> skipForward();

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
    Sec<Void> cursorDown();
    Sec<Void> cursorUp();
    Sec<Void> cursorContext();

    Sec<Void> scrollVertical(double trajectory, boolean glide);
    Sec<Void> scrollHorizontal(double trajectory, boolean glide);

    Sec<Void> pressExtraButton(String button);
}
