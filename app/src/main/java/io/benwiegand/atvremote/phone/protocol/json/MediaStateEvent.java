package io.benwiegand.atvremote.phone.protocol.json;

public record MediaStateEvent(
        String id,
        Integer state,
        Boolean playing,
        Boolean paused
) { }
