package io.benwiegand.atvremote.phone.network;

import static io.benwiegand.atvremote.phone.network.SocketUtil.tryClose;
import static io.benwiegand.atvremote.phone.util.ErrorUtil.getLightStackTrace;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.security.KeyManagementException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.benwiegand.atvremote.phone.auth.ssl.CorruptedKeystoreException;
import io.benwiegand.atvremote.phone.protocol.PairingManager;
import io.benwiegand.atvremote.phone.protocol.RequiresPairingException;
import io.benwiegand.atvremote.phone.stuff.SerialInt;

public class ConnectionService extends Service {
    private static final String TAG = ConnectionService.class.getSimpleName();

    private record ConnectionSpec(String deviceName, String hostname, int port, boolean pairing) {}

    private final IBinder binder = new ConnectionServiceBinder();

    private boolean dead = false;

    // threads
    private ThreadPoolExecutor connectionThreadPool;

    // connection
    private final Object lock = new Object();
    private final SerialInt connectionSerial = new SerialInt();
    private ConnectionManager connectionManager = null;
    private ConnectionSpec connectionSpec = null;
    private ConnectionSpec establishedConnectionSpec = null;
    private TVReceiverConnection connection = null;
    private TVReceiverConnectionCallback connectionCallback = null;

    // ui
    private Callback uiCallback = null;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
        connectionThreadPool = new ThreadPoolExecutor(0, 2, 5, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
        synchronized (lock) {
            dead = true;
            if (connection != null) new Thread(
                    () -> tryClose(connection))
                    .start();
            connectionThreadPool.shutdown();
        }
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
            connectionThreadPool.execute(runnable);
        } catch (RejectedExecutionException e) {
            if (dead) {
                Log.v(TAG, "cannot execute in executor because service is dead");
                Log.d(TAG, "RejectedExecutionException: " + e.getMessage());
                return;
            }

            Log.e(TAG, "executor refuses to execute, but service hasn't destroyed it yet", e);
            throw e;
        }
    }

    /**
     * determines if connect() should try to open a new connection and close the old one (if any).
     * conditions:
     * <ul>
     *     <li>there is no connection</li>
     *     <li>the connection is dead</li>
     *     <li>the connection spec doesn't match the current connections connection spec</li>
     * </ul>
     * @return true if conditions are met
     */
    private boolean shouldReconnectLocked() {
        return connectionSpec != establishedConnectionSpec || connection == null || connection.isDead();
    }


    /**
     * <p>
     *     connects to the currently set connectionSpec, unless a connection to it is already established.
     *     uses shouldReconnectLocked() to determine if it will kill the old connection and make a new one.
     * </p>
     * <p>
     *     if two instances of this method are running at the same time, a serial is used to ensure no race
     *     condition happens. regardless, such situations should be avoided even though they are handled.
     * </p>
     * <b>do not run on main thread</b>
     */
    private void connect() {
        // use a serial to invalidate competing connections to avoid needing to lock for the entire connection init
        int serial;
        TVReceiverConnection oldConnection, newConnection;
        synchronized (lock) {
            if (!shouldReconnectLocked()) {
                Log.i(TAG, "already connected, refusing to reconnect");
                return;
            }

            oldConnection = connection;
            connectionCallback = new ConnectionCallback(); // rotate callback to avoid events from previous connection
            if (oldConnection != null && !oldConnection.isDead())
                callCallback(c -> c.onDisconnected(null));

            // call the callback before setting null to ensure it doesn't get orphaned in a crash
            connection = null;

            serial = connectionSerial.advance();
        }

        // connection happens outside of lock, because the lock is locked on the main thread too
        try {
            if (oldConnection != null)
                tryClose(oldConnection);

            if (connectionSpec.pairing()) {
                newConnection = connectionManager.startPairingToTV(connectionSpec.hostname(), connectionSpec.port(), connectionCallback);
            } else {
                newConnection = connectionManager.connectToTV(connectionSpec.hostname(), connectionSpec.port(), connectionCallback);
            }
        } catch (Throwable t) {
            if (t instanceof IOException || t instanceof RequiresPairingException) {
                Log.e(TAG, "error while connecting:\n" + getLightStackTrace(t));
            } else {
                Log.e(TAG, "error while connecting", t);
            }

            synchronized (lock) {
                if (!connectionSerial.isValid(serial)) { // this connection (including its errors) is irrelevant
                    Log.w(TAG, "not calling error callback because a new connection has started while this one was connecting");
                    return;
                }
                callCallback(c -> c.onConnectError(t));
            }
            return;
        }

        // ensure only the most recently run connect() wins
        synchronized (lock) {
            if (!dead && connectionSerial.isValid(serial)) {
                establishedConnectionSpec = connectionSpec;
                connection = newConnection;
                callCallback(c -> c.onConnected(connection));
                return;
            } else if (!dead) {
                Log.w(TAG, "another connection has started while connecting this one, silently abandoning it");
            }
        }

        tryClose(newConnection);
    }

    // do not run on main thread
    private void disconnect() {
        TVReceiverConnection oldConnection;
        synchronized (lock) {
            oldConnection = connection;
            connection = null;
        }

        // don't lock during disconnection
        if (oldConnection == null) return;
        tryClose(oldConnection);
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
            Log.d(TAG, "register()");
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
         * unregisters the UI Callback. if a disconnect is true and a connection is active, it will
         * be killed.
         * @param oldUiCallback the callback
         * @param disconnect if true, disconnect active connection if any
         */
        public void unregister(Callback oldUiCallback, boolean disconnect) {
            Log.d(TAG, "unregister() disconnect = " + disconnect);
            synchronized (lock) {
                if (oldUiCallback != uiCallback) {
                    Log.w(TAG, "cannot unregister old ui callback, it was never registered or has already been replaced!");
                    return;
                }

                uiCallback = null;
                if (disconnect)
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
                scheduleLocked(ConnectionService.this::connect);
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

        public boolean isInitialized() {
            synchronized (lock) {
                return connectionManager != null;
            }
        }

    }
}