package io.benwiegand.atvremote.phone.protocol.json;

import android.content.Context;

import java.text.MessageFormat;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.protocol.RemoteProtocolException;
import io.benwiegand.atvremote.phone.ui.ErrorMessageException;

public record ErrorDetails(String text) {
    public RemoteProtocolException toException() {
        return new RemoteProtocolException(text(), true);
    }

    public static ErrorDetails fromException(Context context, Throwable t) {
        if (t instanceof ErrorMessageException e) {
            return new ErrorDetails(e.getLocalizedMessage(context));
        }
        return new ErrorDetails(MessageFormat.format(
                context.getString(R.string.protocol_error_unexpected),
                t.getClass().getSimpleName(),
                t.getLocalizedMessage()));
    }
}
