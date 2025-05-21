package io.benwiegand.atvremote.phone.protocol;

import io.benwiegand.atvremote.phone.R;

public class UnauthorizedException extends RemoteProtocolException {
    public UnauthorizedException() {
        super(R.string.protocol_error_unauthorized, "tv replied: unauthorized");
    }
}
