package io.benwiegand.atvremote.phone.protocol;

import androidx.annotation.StringRes;

import io.benwiegand.atvremote.phone.ui.ErrorMessageException;

public class RemoteProtocolException extends ErrorMessageException {
    private final boolean receivedOverConnection;

    public RemoteProtocolException(String message, boolean receivedOverConnection) {
        super(message);
        this.receivedOverConnection = receivedOverConnection;
    }

    public RemoteProtocolException(@StringRes int stringResMessage, String message, boolean receivedOverConnection) {
        super(stringResMessage, message);
        this.receivedOverConnection = receivedOverConnection;
    }

    public RemoteProtocolException(String message) {
        this(message, false);
    }

    public RemoteProtocolException(@StringRes int stringResMessage, String message) {
        this(stringResMessage, message, false);
    }

    public boolean isReceivedOverConnection() {
        return receivedOverConnection;
    }
}
