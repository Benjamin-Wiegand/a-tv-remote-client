package io.benwiegand.atvremote.phone.network.discovery;

public class ServiceDiscoveryException extends RuntimeException {
    public ServiceDiscoveryException(String message) {
        super(message);
    }

    public ServiceDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
