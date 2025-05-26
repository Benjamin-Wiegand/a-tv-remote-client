package io.benwiegand.atvremote.phone.stuff;

public interface ThrowingFunction<T, U> {
    U apply(T t) throws Throwable;
}
