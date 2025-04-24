package io.benwiegand.atvremote.phone.protocol;

public class RequiresPairingException extends Exception {

    public RequiresPairingException(String message) {
        super(message);
    }

    public RequiresPairingException(Throwable cause) {
        super(cause);
    }
}
