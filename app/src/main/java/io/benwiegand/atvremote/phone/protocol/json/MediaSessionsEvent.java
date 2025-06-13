package io.benwiegand.atvremote.phone.protocol.json;

public record MediaSessionsEvent(
        String[] activeSessionIds
) { }
