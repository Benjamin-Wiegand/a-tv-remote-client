package io.benwiegand.atvremote.phone.protocol.stream;

import android.util.Log;

import java.util.HashSet;
import java.util.function.Consumer;

public class IncomingEventStream {
    private static final String TAG = IncomingEventStream.class.getSimpleName();

    private final HashSet<Consumer<String>> listeners = new HashSet<>();

    IncomingEventStream() {

    }

    void onIncomingStreamedEvent(String extra) {
        synchronized (listeners) {
            for (Consumer<String> listener : listeners) {
                try {
                    listener.accept(extra);
                } catch (Throwable t) {
                    Log.wtf(TAG, "uncaught exception in listener", t);
                }
            }
        }
    }

    public void registerListener(Consumer<String> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void unregisterListener(Consumer<String> listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public boolean hasRegisteredListeners() {
        synchronized (listeners) {
            return !listeners.isEmpty();
        }
    }

    public int totalRegisteredListeners() {
        synchronized (listeners) {
            return listeners.size();
        }
    }
}
