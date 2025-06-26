package io.benwiegand.atvremote.phone.protocol.json;

import androidx.annotation.Nullable;

public record MediaPositionEvent(
        String id,
        @Nullable Long position,
        @Nullable Long bufferedPosition
) { }
