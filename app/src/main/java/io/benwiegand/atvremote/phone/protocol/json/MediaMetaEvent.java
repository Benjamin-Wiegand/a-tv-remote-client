package io.benwiegand.atvremote.phone.protocol.json;

import androidx.annotation.Nullable;

public record MediaMetaEvent(
        String id,
        @Nullable String title,
        @Nullable String subtitle,
        @Nullable String sourceName,
        @Nullable Long length
) { }
