package io.benwiegand.atvremote.phone.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatButton;

import java.util.function.Consumer;

import io.benwiegand.atvremote.phone.protocol.KeyEventType;

public class RemoteTextButton extends AppCompatButton implements RemoteButton {

    private final RemoteButtonHandler remoteButtonHandler;

    public RemoteTextButton(Context context) {
        super(context);
        remoteButtonHandler = new RemoteButtonHandler(this, super::onTouchEvent, super::performClick);
    }

    public RemoteTextButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        remoteButtonHandler = new RemoteButtonHandler(this, super::onTouchEvent, super::performClick);
    }

    public RemoteTextButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        remoteButtonHandler = new RemoteButtonHandler(this, super::onTouchEvent, super::performClick);
    }

    @Override
    public void setDownUpKeyEvent(Consumer<KeyEventType> onEvent, int repeatDelay, int repeatInterval) {
        remoteButtonHandler.setDownUpKeyEvent(onEvent, repeatDelay, repeatInterval);
    }

    @Override
    public void setDownUpKeyEvent(Consumer<KeyEventType> onEvent) {
        remoteButtonHandler.setDownUpKeyEvent(onEvent);
    }

    @Override
    public void setClickKeyEvent(Runnable onClick, Runnable onLongClick) {
        remoteButtonHandler.setClickKeyEvent(onClick, onLongClick);
    }

    @Override
    public void setClickKeyEvent(Runnable onClick) {
        remoteButtonHandler.setClickKeyEvent(onClick);
    }

    @SuppressLint("ClickableViewAccessibility") // it does call performClick() just not directly
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // the button text disappears into the 9th dimension when some soft keyboards (including
        // the aosp one) are visible, the button is tapped, and it has a ripple animation background
        // (the material button ripple counts too). calling requestLayout() un-breaks it.
        // I don't know why it disappears. but if you know then please let me know, I would like to know.
        requestLayout();

        return remoteButtonHandler.onTouchEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility") // also calls performClick() indirectly
    @Override
    public boolean performClick() {
        return remoteButtonHandler.performClick();
    }
}
