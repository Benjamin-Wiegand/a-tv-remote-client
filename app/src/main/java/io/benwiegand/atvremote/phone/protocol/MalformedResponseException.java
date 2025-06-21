package io.benwiegand.atvremote.phone.protocol;

public class MalformedResponseException extends RuntimeException {

    public MalformedResponseException(String message, Throwable cause) {
        super(message, cause);
    }

    public MalformedResponseException(String message) {
        super(message);
    }
}
