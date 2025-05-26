package io.benwiegand.atvremote.phone.stuff;

public interface ThrowingConsumer<T> {
    void accept(T t) throws Throwable;
}
