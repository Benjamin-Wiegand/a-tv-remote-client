package io.benwiegand.atvremote.phone.network.event;

public interface QueuedOutput {
    enum Type {
        EVENT,
        RESPONSE,
        DISCONNECTION,
    }

    Type type();
}
