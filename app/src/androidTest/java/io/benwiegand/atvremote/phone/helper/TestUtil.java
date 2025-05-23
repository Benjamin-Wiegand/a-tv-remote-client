package io.benwiegand.atvremote.phone.helper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.benwiegand.atvremote.phone.async.Sec;

public class TestUtil {
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

}
