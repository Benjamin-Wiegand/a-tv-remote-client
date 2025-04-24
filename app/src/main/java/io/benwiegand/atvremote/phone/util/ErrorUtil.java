package io.benwiegand.atvremote.phone.util;

public class ErrorUtil {

    // todo: stack trace + custom view insertion
    // todo: also localized exception messages
    public static String generateErrorDescription(Throwable t) {
        StringBuilder sb = new StringBuilder()
                .append("\n")
                .append(t.getClass().getSimpleName())
                .append(": ")
                .append(t.getLocalizedMessage());

        while ((t = t.getCause()) != null) sb
                .append("\n")
                .append("caused by ")
                .append(t.getClass().getSimpleName())
                .append(": ")
                .append(t.getLocalizedMessage());

        return sb.toString();
    }
}
