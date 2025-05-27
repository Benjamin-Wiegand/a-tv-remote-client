package io.benwiegand.atvremote.phone.network.event;

public record QueuedDisconnection() implements QueuedOutput {
    @Override
    public Type type() {
        return Type.DISCONNECTION;
    }
}
