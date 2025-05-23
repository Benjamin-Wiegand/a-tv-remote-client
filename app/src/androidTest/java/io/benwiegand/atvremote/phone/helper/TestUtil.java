package io.benwiegand.atvremote.phone.helper;

import android.content.Context;
import android.util.Log;

import androidx.core.util.Supplier;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.benwiegand.atvremote.phone.async.Sec;

public class TestUtil {
    private static final String TAG = TestUtil.class.getSimpleName();
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }

    public interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    public static void catchAll(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Exception", t);
        }

    }

    public static <T> T catchAll(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Exception", t);
        }

    }

    public static <T> boolean block(Sec<T> sec, long timeout, TimeUnit timeUnit) {
        CountDownLatch latch = new CountDownLatch(1);

        sec
                .doOnResult(r -> latch.countDown())
                .doOnError(t -> latch.countDown())
                .callMeWhenDone();

        return catchAll(() -> latch.await(timeout, timeUnit));

    }

    public static <T> boolean block(Sec<T> sec, long timeoutMs) {
        return block(sec, timeoutMs, TimeUnit.MILLISECONDS);
    }

    public static <T> T blockAndFlatten(Sec<T> sec, long timeoutMs) throws Throwable {
        if (!block(sec, timeoutMs)) throw new TimeoutException("timed out waiting for Sec");
        return sec.getResultOrThrow();
    }

    /**
     * I think an argument can be made that this is fine inside of tests
     * @noinspection BusyWait
     */
    public static void busyWait(Supplier<Boolean> condition, long pollMs, long timeoutMs) {
        long stopTime = System.currentTimeMillis() + timeoutMs;
        // busy waiting that I'm only allowing to make tests easier to write
        while (!condition.get()) {
            try {
                long wait = Math.min(pollMs, stopTime - System.currentTimeMillis());
                if (wait < 0) break;
                Thread.sleep(pollMs);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public static boolean recursiveDelete(File file) {
        if (!file.isDirectory()) {
            Log.d(TAG, "deleting " + file);
            return file.delete();
        }

        File[] listing = file.listFiles();
        if (listing == null) {
            Log.d(TAG, "deleting " + file);
            return file.delete();
        }

        for (File child : listing) {
            recursiveDelete(child);
        }

        Log.d(TAG, "deleting " + file);
        return file.delete();
    }

    public static boolean recursiveDeleteContents(File file) {
        File[] listing = file.listFiles();
        if (listing == null) return true;

        boolean totalSuccess = true;
        for (File child : listing) {
            if (!recursiveDelete(child)) totalSuccess = false;
        }
        return totalSuccess;
    }

    public static void clearAppData(Context context) {
        Log.i(TAG, "deleting app data");
        File files = context.getFilesDir();
        File sharedPrefs = context.getDataDir().toPath().resolve("shared_prefs").toFile();

        recursiveDeleteContents(files);
        recursiveDeleteContents(sharedPrefs);
    }

}
