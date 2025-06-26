package io.benwiegand.atvremote.phone.protocol.json;

import androidx.annotation.Nullable;

public record MediaStateEvent(
        String id,
        @Nullable Integer state,
        @Nullable Boolean playing,
        @Nullable Boolean paused
) { }
