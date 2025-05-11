package io.benwiegand.atvremote.phone.protocol;

import androidx.annotation.StringRes;

import io.benwiegand.atvremote.phone.ui.ErrorMessageException;

public class RemoteProtocolException extends ErrorMessageException {
    public RemoteProtocolException(String message) {
        super(message);
    }

    public RemoteProtocolException(@StringRes int stringResMessage, String message) {
        super(stringResMessage, message);
    }

}
