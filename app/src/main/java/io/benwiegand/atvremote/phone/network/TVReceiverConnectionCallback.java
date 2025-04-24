package io.benwiegand.atvremote.phone.network;

public interface TVReceiverConnectionCallback {

    void onSocketConnected();
    void onConnected();
    void onReadyStateChanged(boolean ready);
    void onDisconnected();
}
