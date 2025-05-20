package io.benwiegand.atvremote.phone.network;

import static io.benwiegand.atvremote.phone.network.SocketUtil.tryClose;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.*;

import android.util.Log;

import com.google.gson.Gson;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocket;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.auth.ssl.CorruptedKeystoreException;
import io.benwiegand.atvremote.phone.auth.ssl.KeyUtil;
import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.async.SecAdapter;
import io.benwiegand.atvremote.phone.control.InputHandler;
import io.benwiegand.atvremote.phone.control.OperationQueueEntry;
import io.benwiegand.atvremote.phone.protocol.RemoteProtocolException;
import io.benwiegand.atvremote.phone.protocol.RequiresPairingException;
import io.benwiegand.atvremote.phone.protocol.json.ErrorDetails;

public class TVReceiverConnection implements Closeable {
    private static final String TAG = TVReceiverConnection.class.getSimpleName();

    private static final Gson gson = new Gson();

    private static final int SOCKET_AUTH_TIMEOUT = 3000;
    private static final long KEEPALIVE_INTERVAL = 5000;
    private static final long RESPONSE_TIMEOUT = 2500;

    private final ThreadPoolExecutor resultCallbackThreadPool;
    private final Queue<OperationQueueEntry> operationQueue;
    private final InputHandler inputForwarder;
    private final SSLSocket socket;
    private final Thread thread;
    private final String token;
    private boolean dead;
    private boolean init;

    private TCPWriter writer;
    private TCPReader reader;

    private final TVReceiverConnectionCallback callback;

    TVReceiverConnection(SSLSocket socket, TVReceiverConnectionCallback callback, String token) {
        resultCallbackThreadPool = new ThreadPoolExecutor(4, 8, 500, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>());
        operationQueue = new ConcurrentLinkedQueue<>();
        inputForwarder = new InputForwarder();
        this.socket = socket;
        this.callback = callback;
        this.thread = new Thread(this::run);
        this.token = token;
        dead = false;
        init = false;
    }

    TVReceiverConnection(SSLSocket socket, TVReceiverConnectionCallback callback) {
        resultCallbackThreadPool = new ThreadPoolExecutor(4, 8, 500, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>());
        operationQueue = new ConcurrentLinkedQueue<>();
        inputForwarder = new InputForwarder();
        this.socket = socket;
        this.callback = callback;
        this.thread = new Thread(this::run);
        this.token = null;
        dead = false;
        init = false;
    }

    public InputHandler getInputForwarder() {
        return inputForwarder;
    }

    void init() throws IOException, RequiresPairingException {
        if (dead) throw new IllegalStateException("init() called after death");
        if (init) throw new IllegalStateException("init() called twice");
        init = true;

        try {
            Log.i(TAG, "connected to " + socket.getRemoteSocketAddress());
            callback.onSocketConnected();

            reader = TCPReader.createFromStream(socket.getInputStream(), CHARSET);
            writer = TCPWriter.createFromStream(socket.getOutputStream(), CHARSET);

            writer.sendLine(VERSION_1);

            String line = reader.nextLine(SOCKET_AUTH_TIMEOUT);
            if (line == null) throw new RuntimeException("TV didn't respond to version code. Make sure you have the right TV or try restarting the service on the TV.");
            switch (line) {
                case OP_CONFIRM -> Log.v(TAG, "agreed upon protocol v1");
                case OP_UNSUPPORTED -> {
                    Log.e(TAG, "tv responded: unsupported protocol");
                    throw new RuntimeException("unsupported protocol version, try updating the app on this device and/or your TV");
                }
                default -> {
                    Log.e(TAG, "unexpected response from TV");
                    throw new RuntimeException("unexpected response");
                }
            }

            // if no auth token provided, initiate pairing
            boolean pairing = token == null;
            if (pairing) {
                Log.v(TAG, "initiating pairing");
                writer.sendLine(INIT_OP_PAIR);
            } else {
                Log.v(TAG, "authenticating");
                writer.sendLines(INIT_OP_CONNECT, token);
            }

            line = reader.nextLine(SOCKET_AUTH_TIMEOUT);
            if (line == null) throw new RuntimeException("TV didn't respond to initial operation");

            String[] response = line.split(" ", 2);
            if (response.length == 0) throw new RuntimeException("empty response from TV");

            switch (response[0]) {
                case OP_CONFIRM -> Log.v(TAG, "handshake completed");
                case OP_UNAUTHORIZED -> {
                    Log.e(TAG, "unauthorized");
                    if (pairing) throw new RuntimeException("pairing is disabled on TV");
                    else throw new RequiresPairingException("TV rejected auth token");
                }
                case OP_ERR -> {
                    String json = response.length == 1 ? null : response[1];
                    throw parseError(json);
                }
                default -> {
                    Log.e(TAG, "unexpected response from tv");
                    throw new RuntimeException("unexpected response");
                }
            }

            Log.i(TAG, "tv connected: " + socket.getRemoteSocketAddress());
            thread.start();

        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted");
            tryClose(this);
            throw new RuntimeException("Connection deceased");
        } catch (Throwable t) {
            Log.e(TAG, "error during connection init", t);
            tryClose(this);
            throw t;
        }
    }

    private void run() {
        try {
            connectionLoop();
        } catch (SocketException e) {
            Log.e(TAG, "socket died", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException in connection", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "connection loop interrupted", e);
        } catch (Throwable t) {
            Log.e(TAG, "unexpected error in connection", t);
        } finally {
            tryClose(this);
            callback.onDisconnected();
        }
    }


    private void connectionLoop() throws IOException, InterruptedException {
        while (!dead) {

            // pop the queue
            OperationQueueEntry entry = popOperationQueueBlocking();
            if (entry == null) {
                ping();
                continue;
            }

            SecAdapter<String> secAdapter = entry.responseAdapter();

            try {
                writer.sendLine(entry.operation());
                String result = reader.nextLine(RESPONSE_TIMEOUT);

                if (result == null) {
                    IOException exception = new IOException("response timed out");
                    resultCallbackThreadPool.execute(() -> secAdapter.throwError(exception));
                    throw exception;
                }

                // thread pool so the socket loop doesn't block here
                resultCallbackThreadPool.execute(() -> secAdapter.provideResult(result));
            } catch (Throwable t) {
                resultCallbackThreadPool.execute(() -> secAdapter.throwError(t));
                throw t;
            }

        }
    }

    public Certificate getCertificate() throws CorruptedKeystoreException {
        if (dead) throw new IllegalStateException("can't get fingerprint after death");
        return KeyUtil.getRemoteCertificate(socket);
    }

    private OperationQueueEntry popOperationQueueBlocking() throws InterruptedException {
        synchronized (operationQueue) {
            OperationQueueEntry entry = operationQueue.poll();
            if (entry != null) return entry;

            operationQueue.wait(TVReceiverConnection.KEEPALIVE_INTERVAL);
            return operationQueue.poll();
        }
    }

    private void ping() throws IOException, InterruptedException {
        assert init;
        if (dead) throw new IOException("this connection is dead");

//        Log.d(TAG, "ping!");
        writer.sendLine(OP_PING);
        String result = reader.nextLine(RESPONSE_TIMEOUT);
        if (!OP_CONFIRM.equals(result))
            throw new IOException("sent ping but didn't get a pong");
//        Log.d(TAG, "pong!");
    }

    private RemoteProtocolException parseError(String json) {
        Log.e(TAG, "error response: " + json);
        if (json == null)
            return new RemoteProtocolException(R.string.protocol_error_unspecified, "tv gave no error details");

        return gson.fromJson(json, ErrorDetails.class).toException();
    }

    private Sec<Void> addBasicOperation(String operation) {
        synchronized (operationQueue) {
            SecAdapter.SecWithAdapter<String> secWithAdapter = SecAdapter.createThreadless();

            operationQueue.add(new OperationQueueEntry(secWithAdapter.secAdapter(), operation));
            operationQueue.notifyAll();

            return secWithAdapter.sec()
                    .map(r -> {
                        String[] response = r.split(" ", 2);
                        if (response.length == 0) throw new RuntimeException("empty response from TV");

                        return switch (response[0]) {
                            case OP_CONFIRM -> null;
                            case OP_ERR -> {
                                String json = response.length == 1 ? null : response[1];
                                throw parseError(json);
                            }
                            case OP_UNSUPPORTED -> {
                                Log.e(TAG, "operation unsupported");
                                throw new RemoteProtocolException(R.string.protocol_error_unspecified, "operation not supported by tv");
                            }
                            default -> throw new RemoteProtocolException(R.string.protocol_error_response_invalid, "unexpected response from tv");
                        };
                    });
        }
    }

    private Sec<Void> addBasicOperation(String... operation) {
        return addBasicOperation(String.join(" ", operation));
    }

    public Sec<String> sendPairingCode(String code) {
        synchronized (operationQueue) {
            SecAdapter.SecWithAdapter<String> secWithAdapter = SecAdapter.createThreadless();

            operationQueue.add(new OperationQueueEntry(secWithAdapter.secAdapter(), code));
            operationQueue.notifyAll();

            return secWithAdapter.sec()
                    .map(r -> switch (r) {
                        case OP_UNAUTHORIZED -> {
                            Log.e(TAG, "bad pairing code");
                            throw new RemoteProtocolException(R.string.protocol_error_pairing_code_invalid, "bad pairing code");
                        }
                        case OP_UNSUPPORTED -> {
                            Log.e(TAG, "operation unsupported");
                            throw new RemoteProtocolException(R.string.protocol_error_unspecified, "operation not supported by tv");
                        }
                        default -> r;
                    });
        }
    }

    public class InputForwarder implements InputHandler {

        @Override
        public Sec<Void> dpadDown() {
            return addBasicOperation(OP_DPAD_DOWN);
        }

        @Override
        public Sec<Void> dpadUp() {
            return addBasicOperation(OP_DPAD_UP);
        }

        @Override
        public Sec<Void> dpadLeft() {
            return addBasicOperation(OP_DPAD_LEFT);
        }

        @Override
        public Sec<Void> dpadRight() {
            return addBasicOperation(OP_DPAD_RIGHT);
        }

        @Override
        public Sec<Void> dpadSelect() {
            return addBasicOperation(OP_DPAD_SELECT);
        }

        @Override
        public Sec<Void> dpadLongPress() {
            return addBasicOperation(OP_DPAD_LONG_PRESS);
        }

        @Override
        public Sec<Void> navHome() {
            return addBasicOperation(OP_NAV_HOME);
        }

        @Override
        public Sec<Void> navBack() {
            return addBasicOperation(OP_NAV_BACK);
        }

        @Override
        public Sec<Void> navRecent() {
            return addBasicOperation(OP_NAV_RECENT);
        }

        @Override
        public Sec<Void> navApps() {
            return addBasicOperation(OP_NAV_APPS);
        }

        @Override
        public Sec<Void> navNotifications() {
            return addBasicOperation(OP_NAV_NOTIFICATIONS);
        }

        @Override
        public Sec<Void> navQuickSettings() {
            return addBasicOperation(OP_NAV_QUICK_SETTINGS);
        }

        @Override
        public Sec<Void> volumeUp() {
            return addBasicOperation(OP_VOLUME_UP);
        }

        @Override
        public Sec<Void> volumeDown() {
            return addBasicOperation(OP_VOLUME_DOWN);
        }

        @Override
        public Sec<Void> mute() {
            return addBasicOperation(OP_MUTE);
        }

        @Override
        public Sec<Void> pause() {
            return addBasicOperation(OP_PAUSE);
        }

        @Override
        public Sec<Void> nextTrack() {
            return addBasicOperation(OP_NEXT_TRACK);
        }

        @Override
        public Sec<Void> prevTrack() {
            return addBasicOperation(OP_PREV_TRACK);
        }

        @Override
        public Sec<Void> skipBackward() {
            return addBasicOperation(OP_SKIP_BACKWARD);
        }

        @Override
        public Sec<Void> skipForward() {
            return addBasicOperation(OP_SKIP_FORWARD);
        }

        @Override
        public boolean softKeyboardEnabled() {
            // TODO
            return false;
        }

        @Override
        public boolean softKeyboardVisible() {
            // TODO
            return false;
        }

        @Override
        public Sec<Void> showSoftKeyboard() {
            return null;
            // TODO

        }

        @Override
        public Sec<Void> hideSoftKeyboard() {
            return null;
            // TODO

        }

        @Override
        public Sec<Void> setSoftKeyboardEnabled(boolean enabled) {
            return null;
            // TODO

        }

        @Override
        public Sec<Void> keyboardInput(String input) {
            return null;
            // TODO

        }

        @Override
        public boolean cursorSupported() {
            // TODO
            return false;
        }

        @Override
        public Sec<Void> showCursor() {
            return addBasicOperation(OP_CURSOR_SHOW);
        }

        @Override
        public Sec<Void> hideCursor() {
            return addBasicOperation(OP_CURSOR_HIDE);
        }

        @Override
        public Sec<Void> cursorMove(int x, int y) {
            return addBasicOperation(OP_CURSOR_MOVE, String.valueOf(x), String.valueOf(y));
        }

        @Override
        public Sec<Void> cursorDown() {
            return addBasicOperation(OP_CURSOR_DOWN);
        }

        @Override
        public Sec<Void> cursorUp() {
            return addBasicOperation(OP_CURSOR_UP);
        }

        @Override
        public Sec<Void> cursorContext() {
            return null;
            // TODO

        }

        @Override
        public Sec<Void> scrollVertical(double trajectory, boolean glide) {
            return null;
            // TODO

        }

        @Override
        public Sec<Void> scrollHorizontal(double trajectory, boolean glide) {
            return null;
            // TODO

        }
    }

    public boolean isDead() {
        return dead;
    }

    public void close() {
        Log.d(TAG, "close()");
        dead = true;
        thread.interrupt();

        OperationQueueEntry entry;
        while ((entry = operationQueue.poll()) != null) {
            try {
                entry.responseAdapter().throwError(new IOException("connection closed"));
            } catch (IllegalStateException e) {
                // todo: maybe close() is being called multiple times across multiple threads
                // todo: check if the poll() fix works
                Log.wtf(TAG, "(bug) popped queue item isn't popped (but it had to be popped)", e);
                throw e;
            }
        }

        resultCallbackThreadPool.shutdown();

        tryClose(socket);
        tryClose(reader);
        tryClose(writer);
    }

}
