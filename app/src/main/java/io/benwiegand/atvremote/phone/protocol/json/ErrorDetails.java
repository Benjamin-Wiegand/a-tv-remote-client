package io.benwiegand.atvremote.phone.protocol.json;

import io.benwiegand.atvremote.phone.protocol.RemoteProtocolException;

public record ErrorDetails(String text) {
    public RemoteProtocolException toException() {
        return new RemoteProtocolException(text());
    }

}
