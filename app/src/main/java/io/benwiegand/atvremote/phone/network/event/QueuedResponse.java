package io.benwiegand.atvremote.phone.network.event;

public record QueuedResponse(String response) implements QueuedOutput {
    @Override
    public Type type() {
        return Type.RESPONSE;
    }
}
