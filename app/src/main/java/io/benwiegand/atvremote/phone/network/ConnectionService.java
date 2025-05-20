package io.benwiegand.atvremote.phone.network;

import static io.benwiegand.atvremote.phone.network.SocketUtil.tryClose;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.security.KeyManagementException;
import java.util.function.Consumer;

import io.benwiegand.atvremote.phone.auth.ssl.CorruptedKeystoreException;
import io.benwiegand.atvremote.phone.protocol.PairingManager;
import io.benwiegand.atvremote.phone.stuff.SingleExecutor;

public class ConnectionService extends Service {
    private static final String TAG = ConnectionService.class.getSimpleName();

    private record ConnectionSpec(String deviceName, String hostname, int port, boolean pairing) {}

    private final IBinder binder = new ConnectionServiceBinder();

    private boolean dead = false;

    // threads
    private final SingleExecutor executor = new SingleExecutor();

    // connection
    private final Object lock = new Object();
    private ConnectionManager connectionManager = null;
    private ConnectionSpec connectionSpec = null;
    private TVReceiverConnection connection = null;
    private TVReceiverConnectionCallback connectionCallback = null;

    // ui
    private Callback uiCallback = null;

    @Override
    public void onCreate() {
        super.onCreate();
        executor.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dead = true;
        if (connection != null) tryClose(connection);
        executor.destroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public interface Callback {

        /**
         * service has completed initialization and is ready to be used.
         * if this isn't the case, onServiceInitError will be called instead.
         */
        void onServiceInit();

        /**
         * an error happened during service init.
         * this must be called if onServiceInit isn't.
         * @param t the exception
         * @param possiblyKeystoreInit the exception could be related to the keystore
         */
        void onServiceInitError(Throwable t, boolean possiblyKeystoreInit);

        /**
         * called right after a socket is established with the TV, but before the handshake happens.
         * this is purely informational and might be skipped if the requested connection is already
         * open!
         */
        void onSocketConnected();

        /**
         * the connection is established, has completed init, and is ready to be used.
         * called right around when the connection loop is run and inputs can be processed.
         * @param connection the connection, now usable
         */
        void onConnected(TVReceiverConnection connection);

        /**
         * an error has happened during connection init.
         * this is called for errors before onConnected(), errors afterward are provided to
         * onDisconnected().
         * @param t the exception
         */
        void onConnectError(Throwable t);

        /**
         * when a disconnection happens (after onConnected())
         * @param t the error, or null if clean disconnect
         */
        void onDisconnected(Throwable t);

    }

    private void callCallback(Consumer<Callback> call) {
        Callback uiCallback = this.uiCallback;
        if (uiCallback == null) return;

        try {
            call.accept(uiCallback);
        } catch (Throwable t) {
            Log.wtf(TAG, "ui callback threw!!!", t);
            throw t;
        }
    }

    private void initService() {
        synchronized (lock) {
            initServiceLocked();
        }
    }

    private void initServiceLocked() {
        try {
            initConnectionManagerLocked();
        } catch (Throwable t) {
            Log.e(TAG, "error in init");
            callCallback(c -> c.onServiceInitError(t, true));
            return;
        }

        callCallback(Callback::onServiceInit);
    }
    
    private void initConnectionManagerLocked() throws IOException, CorruptedKeystoreException, KeyManagementException {
        if (connectionManager != null) return;
        ConnectionManager conman = new ConnectionManager(this);
        conman.initializeSSL();
        connectionManager = conman;
    }

    // for running things not on the main thread
    private void scheduleLocked(Runnable runnable) {
        try {
            executor.execute(runnable);
        } catch (IllegalStateException e) {
            if (dead) {
                Log.v(TAG, "cannot execute in executor because service is dead");
                Log.d(TAG, "IllegalStateException: " + e.getMessage());
                return;
            }

            Log.e(TAG, "executor refuses to execute, but service hasn't destroyed it yet", e);
            throw e;
        }
    }

    // do not run on main thread
    private void connectLocked() {
        if (connection != null && !connection.isDead()) return;
        connectionCallback = new ConnectionCallback(); // rotate callback to avoid events from previous connection

        try {
            if (connectionSpec.pairing()) {
                connection = connectionManager.startPairingToTV(connectionSpec.hostname(), connectionSpec.port(), connectionCallback);
            } else {
                connection = connectionManager.connectToTV(connectionSpec.hostname(), connectionSpec.port(), connectionCallback);
            }
        } catch (Throwable t) {
            Log.e(TAG, "error while connecting", t);
            callCallback(c -> c.onConnectError(t));
            return;
        }

        callCallback(c -> c.onConnected(connection));
    }

    // do not run on main thread
    private void disconnect() {
        synchronized (lock) {
            disconnectLocked();
        }
    }

    // do not run on main thread
    private void disconnectLocked() {
        if (connection == null) return;
        tryClose(connection);
    }

    // do not run on main thread
    private void reconnect() {
        synchronized (lock) {
            disconnectLocked();
            connectLocked();
        }
    }

    private class ConnectionCallback implements TVReceiverConnectionCallback {

        private boolean invalid() {
            return connectionCallback != this;
        }

        @Override
        public void onSocketConnected() {
            if (invalid()) return;
            callCallback(Callback::onSocketConnected);
        }

        @Override
        public void onDisconnected(Throwable t) {
            synchronized (lock) {
                if (invalid()) return;

                callCallback(c -> c.onDisconnected(t));
                connection = null;
                connectionCallback = null;
            }
        }
    }

    public class ConnectionServiceBinder extends Binder {

        /**
         * registers the provided UI Callback
         * @param newUiCallback the callback
         */
        public void register(Callback newUiCallback) {
            synchronized (lock) {
                if (uiCallback == newUiCallback)
                    Log.w(TAG, "the same ui callback was registered twice");
                if (uiCallback != null)
                    Log.w(TAG, "new ui callback added before old one unregistered");

                uiCallback = newUiCallback;
            }
        }

        /**
         * initializes the service.
         * this should be called after register() because the result is provided in the callback:
         * <ul>
         *     <li>if init fails, onServiceInitError() is called</li>
         *     <li>if init succeeds, onServiceInit() is called</li>
         * </ul>
         */
        public void init() {
            initService();
        }

        /**
         * unregisters the UI Callback. if a connection is active, it will be killed.
         * @param oldUiCallback the callback
         */
        public void unregister(Callback oldUiCallback) {
            synchronized (lock) {
                if (oldUiCallback != uiCallback) {
                    Log.w(TAG, "cannot unregister old ui callback, it was never registered or has already been replaced!");
                    return;
                }

                uiCallback = null;
                scheduleLocked(ConnectionService.this::disconnect);
            }
        }

        /**
         * transfer context to foreground notification
         */
        public void background() {
            // todo
        }

        /**
         * transfer context back from foreground notification
         */
        public void foreground() {
            // todo
        }

        /**
         * opens a connection to the given TV if one does not already exist.
         * init() must be called first.
         * the result is provided to the registered UI Callback:
         * <ul>
         *     <li>onSocketConnected() is called if/when a socket is opened to the TV (an already open connection will bypass this)</li>
         *     <li>regardless, onConnected() is called if an initialized connection is available</li>
         *     <li>onConnectError() is called if the connection fails initialization (at any layer)</li>
         *     <li>onDisconnected() is called at any point after a connection is initialized if it dies</li>
         * </ul>
         * @param deviceName the device name
         * @param hostname the hostname
         * @param port the port number
         * @param forPairing if the connection is for pairing
         */
        public void connect(String deviceName, String hostname, int port, boolean forPairing) {
            synchronized (lock) {
                if (connectionManager == null) throw new IllegalStateException("must init first");

                ConnectionSpec spec = new ConnectionSpec(deviceName, hostname, port, forPairing);
                if (spec.equals(connectionSpec) && connection != null && !connection.isDead()) {
                    Log.d(TAG, "already connected, refusing to reconnect");
                    callCallback(c -> c.onConnected(connection));
                    return;
                }

                connectionSpec = spec;
                // reconnect ensures any existing connection dies, connect will refuse regardless of the connectionSpec being different
                scheduleLocked(ConnectionService.this::reconnect);
            }
        }

        public void disconnect() {
            synchronized (lock) {
                scheduleLocked(ConnectionService.this::disconnect);
            }
        }

        public PairingManager getPairingManager() {
            synchronized (lock) {
                if (connectionManager == null) return null;
                return connectionManager.getPairingManager();
            }
        }

        public void refreshCertificates() {
            synchronized (lock) {
                if (connectionManager == null) return;
                try {
                    connectionManager.refreshCertificates();
                } catch (Throwable t) {
                    Log.e(TAG, "failed to refresh certificates, de-initializing", t);
                    // forces the keys to be reloaded regardless
                    connectionManager = null;
                    if (connection != null) scheduleLocked(this::disconnect);
                }
            }
        }

    }
}