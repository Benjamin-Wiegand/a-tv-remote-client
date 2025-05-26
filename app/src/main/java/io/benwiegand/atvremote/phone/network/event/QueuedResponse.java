package io.benwiegand.atvremote.phone.network.event;

public record QueuedResponse(String response) implements QueuedOutput {
    public Type type() {
        return Type.RESPONSE;
    }
}
