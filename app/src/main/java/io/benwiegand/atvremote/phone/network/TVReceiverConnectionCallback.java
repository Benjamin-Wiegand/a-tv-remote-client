package io.benwiegand.atvremote.phone.network;

public interface TVReceiverConnectionCallback {

    void onSocketConnected();
    void onDisconnected(Throwable t);
}
