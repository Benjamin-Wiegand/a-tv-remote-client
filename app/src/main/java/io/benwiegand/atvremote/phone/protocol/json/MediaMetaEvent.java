package io.benwiegand.atvremote.phone.protocol.json;

public record MediaMetaEvent(
        String id,
        String title,
        String subtitle,
        String sourceName,
        Long length
) { }
