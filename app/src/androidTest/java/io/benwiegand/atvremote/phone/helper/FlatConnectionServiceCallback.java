package io.benwiegand.atvremote.phone.helper;

import io.benwiegand.atvremote.phone.network.ConnectionService;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;

public class FlatConnectionServiceCallback extends CallbackFlattener implements ConnectionService.Callback {
    @Override
    public void onServiceInit() {
        onCall("onServiceInit");
    }

    @Override
    public void onServiceInitError(Throwable t, boolean possiblyKeystoreInit) {
        onCall("onServiceInitError", t, possiblyKeystoreInit);
    }

    @Override
    public void onSocketConnected() {
        onCall("onSocketConnected");
    }

    @Override
    public void onConnected(TVReceiverConnection connection) {
        onCall("onConnected", connection);
    }

    @Override
    public void onConnectError(Throwable t) {
        onCall("onConnectError", t);
    }

    @Override
    public void onDisconnected(Throwable t) {
        onCall("onDisconnected", t);
    }
}
