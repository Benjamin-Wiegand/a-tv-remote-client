package io.benwiegand.atvremote.phone.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.function.BiFunction;

import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.control.MouseCurve;

public class TrackpadSurface extends View {
    private static final String TAG = TrackpadSurface.class.getSimpleName();

    private final Object mouseTranslationUpdateLock = new Object();
    private boolean readyForUpdate = true;
    private final MouseCurve curve = MouseCurve.ACCEL_LOG10; // todo
    private BiFunction<Integer, Integer, Sec<Void>> mouseTranslationConsumer = null;
    private float cachedDeltaX = 0;
    private float cachedDeltaY = 0;

    public TrackpadSurface(Context context) {
        super(context);
    }

    public TrackpadSurface(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TrackpadSurface(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setMouseTranslationConsumer(BiFunction<Integer, Integer, Sec<Void>> mouseTranslationConsumer) {
        this.mouseTranslationConsumer = mouseTranslationConsumer;
    }

    @SuppressLint("ClickableViewAccessibility") // controlling a trackpad through talkback would be miserable
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        calculateMouseTranslation(event);
        handleMouseTranslationUpdate();
        return true;
    }

    private void calculateMouseTranslation(MotionEvent event) {
        if (mouseTranslationConsumer == null) return;
        if (event.getAction() != MotionEvent.ACTION_MOVE) return;
        if (event.getHistorySize() < 1) return;

        // use float to preserve sub-pixel movements
        float x = event.getX() - event.getHistoricalX(0);
        float y = event.getY() - event.getHistoricalY(0);

        // apply curve to make the downsides of trackpads less painful
        x = curve.apply(x);
        y = curve.apply(y);

        synchronized (mouseTranslationUpdateLock) {
            cachedDeltaX += x;
            cachedDeltaY += y;
        }
    }

    private void handleMouseTranslationUpdate() {
        int deltaX, deltaY;
        synchronized (mouseTranslationUpdateLock) {
            deltaX = (int) cachedDeltaX;
            deltaY = (int) cachedDeltaY;
            if (deltaX == 0 && deltaY == 0) return; // nothing significant enough to update

            if (!readyForUpdate) return; // don't overwhelm the connection
            readyForUpdate = false;

            // preserve sub-pixel offset
            cachedDeltaX -= deltaX;
            cachedDeltaY -= deltaY;
        }

        try {
            Sec<Void> operation = mouseTranslationConsumer.apply(deltaX, deltaY);
            if (operation == null) return;

            operation
                    .doOnResult(v -> {
                        readyForUpdate = true;
                        Log.d(TAG, "mouse translation updated: " + deltaX + ", " + deltaY);
                        handleMouseTranslationUpdate(); // do it back-to-back
                    })
                    .doOnError(t -> {
                        readyForUpdate = true;
                        Log.w(TAG, "mouse translation update failed", t);
                    })
                    .callMeWhenDone();

        } catch (Throwable t) {
            Log.wtf(TAG, "exception while setting up mouse translation update (this isn't supposed to happen)", t);
            readyForUpdate = true; // set this to true to avoid soft-lock
        }
    }


}