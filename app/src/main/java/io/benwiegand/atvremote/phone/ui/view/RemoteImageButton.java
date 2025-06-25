package io.benwiegand.atvremote.phone.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RotateDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;

import java.util.function.Consumer;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.protocol.KeyEventType;

public class RemoteImageButton extends AppCompatImageButton implements RemoteButton {

    private float drawableRotation = 0;
    private final RemoteButtonHandler remoteButtonHandler;

    public RemoteImageButton(Context context) {
        super(context);
        init(null);
        remoteButtonHandler = new RemoteButtonHandler(this, super::onTouchEvent, super::performClick);
    }

    public RemoteImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
        remoteButtonHandler = new RemoteButtonHandler(this, super::onTouchEvent, super::performClick);
    }

    public RemoteImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
        remoteButtonHandler = new RemoteButtonHandler(this, super::onTouchEvent, super::performClick);
    }

    private void init(AttributeSet attrs) {
        if (attrs == null) return;

        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            if (attrs.getAttributeNameResource(i) != R.attr.rotateDrawable) continue;

            drawableRotation = attrs.getAttributeFloatValue(i, 0);
            if (drawableRotation == 0) break;
            setImageDrawableInternal(rotateDrawable(getDrawable()));
            break;
        }
    }

    private Drawable rotateDrawable(Drawable drawable) {
        if (drawableRotation == 0 || drawable == null) return drawable;
        RotateDrawable rotateDrawable = new RotateDrawable();
        rotateDrawable.setDrawable(drawable);
        rotateDrawable.setToDegrees(drawableRotation);
        rotateDrawable.setLevel(10000); // MAX_LEVEL (100% of lerp in RotateDrawable.onLevelChange())
        return rotateDrawable;
    }

    private void setImageDrawableInternal(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        setImageDrawableInternal(rotateDrawable(drawable));
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
        return remoteButtonHandler.onTouchEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility") // also calls performClick() indirectly
    @Override
    public boolean performClick() {
        return remoteButtonHandler.performClick();
    }

}
