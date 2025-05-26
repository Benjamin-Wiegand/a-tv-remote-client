package io.benwiegand.atvremote.phone.network.event;

import android.os.SystemClock;

import io.benwiegand.atvremote.phone.async.SecAdapter;

public record InFlightEvent(SecAdapter<EventResult> adapter, long enqueuedAt) {

    public boolean isExpired(long timeout) {
        return SystemClock.elapsedRealtime() - enqueuedAt() - timeout > 0;
    }
}
