package io.benwiegand.atvremote.phone.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static io.benwiegand.atvremote.phone.helper.TestUtil.catchAll;

import android.util.Log;

import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class CallbackFlattener {
    private static final String TAG = CallbackFlattener.class.getSimpleName();

    private final Map<String, Deque<Object[]>> callMap = new HashMap<>();

    private final Object lock = new Object();
    private CountDownLatch latch = new CountDownLatch(0);
    private int extraCalls = 0;

    public void waitForNextCall(long timeout, TimeUnit timeUnit) {
        synchronized (lock) {
            if (extraCalls > 0) {
                Log.i(TAG, "consumed call");
                extraCalls--;
                return;
            }

            Log.i(TAG, "waiting for call");
            latch = new CountDownLatch(1);
        }
        catchAll(() -> latch.await(timeout, timeUnit));
    }

    protected void onCall(String callbackName, Object... args) {
        synchronized (lock) {
            Log.d(TAG, callbackName + "() called with: " + Arrays.toString(args));

            callMap.putIfAbsent(callbackName, new LinkedList<>());
            Deque<Object[]> calls = callMap.get(callbackName);
            assert calls != null;

            calls.add(args);

            if (latch.getCount() == 0) extraCalls++;
            latch.countDown();
        }
    }

    public void assertNoMoreCalls(String msg) {
        assertEquals(msg, 0, extraCalls);
        assertCallMapEmpty(msg);
    }

    private void assertCallMapEmpty(String msg) {
        synchronized (lock) {
            int calls = callMap.values().stream()
                    .map(Deque::size)
                    .reduce(Integer::sum)
                    .orElse(0);

            assertEquals(msg, 0, calls);
        }
    }

    public Deque<Object[]> callsFor(String callbackName) {
        synchronized (lock) {
            return callMap.getOrDefault(callbackName, new LinkedList<>());
        }
    }

    public Object[] latestCallFor(String callbackName) {
        return callsFor(callbackName).poll();
    }

    public Object[] assertCallTo(String callbackName) {
        Object[] args = latestCallFor(callbackName);
        assertNotNull("expected call to " + callbackName, args);
        return args;
    }
}
