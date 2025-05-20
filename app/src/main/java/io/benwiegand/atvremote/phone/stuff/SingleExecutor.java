package io.benwiegand.atvremote.phone.stuff;

import android.util.Log;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * can execute a single thing at a single time in a single thread.
 * multiple events at the same time will queue up.
 * exceptions in queued events are logged but don't cause a crash.
 */
public class SingleExecutor implements Executor {
    private static final String TAG = SingleExecutor.class.getSimpleName();

    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final Thread thread = new Thread(this::loop);
    private boolean dead = false;

    public void start() {
        thread.start();
    }

    @Override
    public void execute(Runnable command) {
        synchronized (queue) {
            if (dead) throw new IllegalStateException("cannot execute, already destroyed");
            queue.add(command);
            queue.notify();
        }
    }

    private void loop() {
        try {
            while (!dead) {
                Runnable event = queue.poll();

                if (event == null) {
                    synchronized (queue) {
                        if (queue.isEmpty() && !dead)
                            queue.wait(10000);
                        continue;
                    }
                }

                try {
                    event.run();
                } catch (Throwable t) {
                    Log.wtf(TAG, "exception during event", t);
                }
            }
        } catch (InterruptedException ignored) {
            Log.d(TAG, "interrupted");
        } finally {
            dead = true;
            Log.d(TAG, "exiting event loop");

            if (!queue.isEmpty()) {
                Log.v(TAG, queue.size() + " events will not be run due to exit");
            }
        }
    }

    public void destroy() {
        synchronized (queue) {
            dead = true;
            queue.notify();
        }
    }
}
