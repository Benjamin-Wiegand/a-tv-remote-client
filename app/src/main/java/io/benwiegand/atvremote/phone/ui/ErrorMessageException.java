package io.benwiegand.atvremote.phone.ui;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

public class ErrorMessageException extends RuntimeException {
    @StringRes
    private Integer stringResMessage = null;

    public ErrorMessageException(String message) {
        super(message);
    }

    public ErrorMessageException(@StringRes int stringResMessage, String message) {
        super(message);
        this.stringResMessage = stringResMessage;
    }

    public ErrorMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public ErrorMessageException(@StringRes int stringResMessage, String message, Throwable cause) {
        super(message, cause);
        this.stringResMessage = stringResMessage;
    }

    @Nullable
    @StringRes
    public Integer getStringResMessage() {
        return stringResMessage;
    }
}
