package io.benwiegand.atvremote.phone.protocol.json;

public record MediaPositionEvent(
        String id,
        Long position,
        Long bufferedPosition
) { }
