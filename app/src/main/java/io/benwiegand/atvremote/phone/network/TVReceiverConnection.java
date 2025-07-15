package io.benwiegand.atvremote.phone.network;

import static io.benwiegand.atvremote.phone.network.SocketUtil.tryClose;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.*;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;

import java.io.Closeable;
import java.io.IOException;
import java.security.cert.Certificate;
import java.util.function.Consumer;

import javax.net.ssl.SSLSocket;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.auth.ssl.CorruptedKeystoreException;
import io.benwiegand.atvremote.phone.auth.ssl.KeyUtil;
import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.control.InputHandler;
import io.benwiegand.atvremote.phone.protocol.KeyEventType;
import io.benwiegand.atvremote.phone.protocol.MalformedResponseException;
import io.benwiegand.atvremote.phone.protocol.OperationDefinition;
import io.benwiegand.atvremote.phone.protocol.RemoteProtocolException;
import io.benwiegand.atvremote.phone.protocol.RequiresPairingException;
import io.benwiegand.atvremote.phone.protocol.json.CommitTextParams;
import io.benwiegand.atvremote.phone.protocol.json.DeleteTextParams;
import io.benwiegand.atvremote.phone.protocol.json.ErrorDetails;
import io.benwiegand.atvremote.phone.protocol.json.KeyEventParams;
import io.benwiegand.atvremote.phone.protocol.json.ReceiverCapabilities;
import io.benwiegand.atvremote.phone.protocol.json.ReceiverDeviceMeta;
import io.benwiegand.atvremote.phone.protocol.json.RemoteDeviceMeta;
import io.benwiegand.atvremote.phone.protocol.stream.EventStreamSubscriptionManager;

public class TVReceiverConnection implements Closeable {
    private static final String TAG = TVReceiverConnection.class.getSimpleName();

    private static final Gson gson = new Gson();

    private static final int SOCKET_AUTH_TIMEOUT = 3000;
    public static final long KEEPALIVE_INTERVAL = 5000;
    public static final long KEEPALIVE_TIMEOUT = KEEPALIVE_INTERVAL * 2;

    private final Context context;
    private final InputHandler inputForwarder = new InputForwarder();

    private final EventStreamSubscriptionManager eventStreamSubscriptionManager = new EventStreamSubscriptionManager(this::sendOperation);

    private final SSLSocket socket;
    private TCPWriter writer = null;
    private TCPReader reader = null;
    private EventJuggler eventJuggler = null;

    private ReceiverDeviceMeta receiverDeviceMeta = null;

    private final TVReceiverConnectionCallback callback;
    private final String token;
    private boolean dead = false;
    private boolean init = false;

    /**
     * connection to the TV receiver
     * @param context context (for fetching string resources)
     * @param socket the socket for the connection
     * @param callback callback for various events
     * @param token authentication token - a null value implies pairing mode
     */
    TVReceiverConnection(Context context, SSLSocket socket, TVReceiverConnectionCallback callback, String token) {
        this.context = context;
        this.socket = socket;
        this.callback = callback;
        this.token = token;
    }

    /**
     * connection to the TV receiver but for pairing
     * @param context context (for fetching string resources)
     * @param socket the socket for the connection
     * @param callback callback for various events
     */
    TVReceiverConnection(Context context, SSLSocket socket, TVReceiverConnectionCallback callback) {
        this(context, socket, callback, null);
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
            eventJuggler = new EventJuggler(context, socket, reader, writer, this::onSocketDeath, KEEPALIVE_INTERVAL, KEEPALIVE_TIMEOUT);

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

            if (!pairing)
                exchangeMeta();

            Log.i(TAG, "tv connected: " + socket.getRemoteSocketAddress());
            eventJuggler.start(getOperationDefinitions());

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

    private void exchangeMeta() throws IOException, InterruptedException {
        writer.sendLine(OP_META + " " + gson.toJson(RemoteDeviceMeta.getDeviceMeta(context)));

        String line = reader.nextLine(SOCKET_AUTH_TIMEOUT);
        if (line == null) {
            Log.w(TAG, "metadata fetch timed out");
            return;
        }

        String[] opLine = line.split(" ", 2);

        if (opLine[0].equals(OP_META) && opLine.length == 2) {
            receiverDeviceMeta = gson.fromJson(opLine[1], ReceiverDeviceMeta.class);
            Log.v(TAG, "got metadata: " + receiverDeviceMeta);
        } else if (opLine[0].equals("!" + OP_META)) {
            Log.v(TAG, "no meta");
        } else {
            Log.w(TAG, "invalid metadata response: " + line);
        }
    }

    private void onSocketDeath(Throwable t) {
        tryClose(this);
        callback.onDisconnected(t);
    }


    public Certificate getCertificate() throws CorruptedKeystoreException {
        if (dead) throw new IllegalStateException("can't get fingerprint after death");
        return KeyUtil.getRemoteCertificate(socket);
    }

    public ReceiverCapabilities getCapabilities() {
        ReceiverCapabilities capabilities = null;
        if (receiverDeviceMeta != null)
            capabilities = receiverDeviceMeta.capabilities();

        if (capabilities == null) {
            // this ideally shouldn't happen
            Log.w(TAG, "default capabilities requested");
            assert false;
            return ReceiverCapabilities.getDefault();
        }

        return capabilities;
    }

    private RemoteProtocolException parseError(String json) {
        Log.e(TAG, "error response: " + json);
        if (json == null)
            return new RemoteProtocolException(R.string.protocol_error_unspecified, "tv gave no error details", true);

        return gson.fromJson(json, ErrorDetails.class).toException();
    }

    private Sec<String> sendOperation(String event) {
        if (eventJuggler == null) throw new IllegalStateException("connection init not finished yet");
        return eventJuggler.sendEvent(event)
                .map(r -> {
                    // parse errors
                    int iSep = r.responseLine().indexOf(' ');
                    String op, extra;
                    if (iSep > 1) {
                        op = r.responseLine().substring(0, iSep);
                        extra = r.responseLine().substring(iSep + 1);
                    } else {
                        op = r.responseLine();
                        extra = null;
                    }

                    return switch (op) {
                        case OP_CONFIRM -> extra;
                        case OP_ERR -> throw parseError(extra);
                        case OP_UNSUPPORTED -> throw new RemoteProtocolException(R.string.protocol_error_op_unsupported, "operation not supported by tv", true);
                        default -> throw new RemoteProtocolException(R.string.protocol_error_response_invalid, "unexpected response from tv", true);
                    };
                });
    }

    private Sec<Void> sendBasicOperation(String operation) {
        return sendOperation(operation)
                .map(r -> null);
    }

    private Sec<Boolean> sendOperationForBooleanResponse(String operation) {
        return sendOperation(operation)
                .map(e -> switch (e.toLowerCase()) {
                    case "true" -> true;
                    case "false" -> false;
                    default -> throw new MalformedResponseException("not a boolean: " + e);
                });
    }

    public Sec<String> sendPairingCode(String code) {
        return sendOperation(OP_TRY_PAIRING_CODE + " " + code);
    }

    /**
     * subscribes to an event stream.
     * for information about how this is handled,
     * see {@link EventStreamSubscriptionManager#subscribe(String, Consumer)}.
     * @param eventType the event type to subscribe to
     * @param listener the listener to get called on events
     * @return a Sec result for the subscription request
     */
    public Sec<Void> subscribeToEventStream(String eventType, Consumer<String> listener) {
        return eventStreamSubscriptionManager.subscribe(eventType, listener);
    }

    /**
     * unsubscribes from an event stream.
     * for information about how this is handled,
     * see {@link EventStreamSubscriptionManager#unsubscribe(String, Consumer)}.
     * @param eventType the event type to unsubscribe from
     * @param listener the listener
     * @return a Sec result for the unsubscription result/request
     */
    public Sec<Void> unsubscribeFromEventStream(String eventType, Consumer<String> listener) {
        return eventStreamSubscriptionManager.unsubscribe(eventType, listener);
    }

    public class InputForwarder implements InputHandler {

        @Override
        public Sec<Void> dpadDown(KeyEventType type) {
            return sendBasicOperation(OP_DPAD_DOWN + " " + type);
        }

        @Override
        public Sec<Void> dpadUp(KeyEventType type) {
            return sendBasicOperation(OP_DPAD_UP + " " + type);
        }

        @Override
        public Sec<Void> dpadLeft(KeyEventType type) {
            return sendBasicOperation(OP_DPAD_LEFT + " " + type);
        }

        @Override
        public Sec<Void> dpadRight(KeyEventType type) {
            return sendBasicOperation(OP_DPAD_RIGHT + " " + type);
        }

        @Override
        public Sec<Void> dpadSelect(KeyEventType type) {
            return sendBasicOperation(OP_DPAD_SELECT + " " + type);
        }

        @Override
        public Sec<Void> dpadLongPress() {
            return sendBasicOperation(OP_DPAD_LONG_PRESS);
        }

        @Override
        public Sec<Void> navHome(KeyEventType type) {
            return sendBasicOperation(OP_NAV_HOME + " " + type);
        }

        @Override
        public Sec<Void> navBack(KeyEventType type) {
            return sendBasicOperation(OP_NAV_BACK + " " + type);
        }

        @Override
        public Sec<Void> navRecent(KeyEventType type) {
            return sendBasicOperation(OP_NAV_RECENT + " " + type);
        }

        @Override
        public Sec<Void> navNotifications(KeyEventType type) {
            return sendBasicOperation(OP_NAV_NOTIFICATIONS + " " + type);
        }

        @Override
        public Sec<Void> navQuickSettings() {
            return sendBasicOperation(OP_NAV_QUICK_SETTINGS);
        }

        @Override
        public Sec<Void> volumeUp(KeyEventType type) {
            return sendBasicOperation(OP_VOLUME_UP + " " + type);
        }

        @Override
        public Sec<Void> volumeDown(KeyEventType type) {
            return sendBasicOperation(OP_VOLUME_DOWN + " " + type);
        }

        @Override
        public Sec<Void> toggleMute(KeyEventType type) {
            return sendBasicOperation(OP_MUTE_TOGGLE + " " + type);
        }

        @Override
        public Sec<Void> playPause(KeyEventType type) {
            return sendBasicOperation(OP_PLAY_PAUSE + " " + type);
        }

        @Override
        public Sec<Void> nextTrack(KeyEventType type) {
            return sendBasicOperation(OP_NEXT_TRACK + " " + type);
        }

        @Override
        public Sec<Void> prevTrack(KeyEventType type) {
            return sendBasicOperation(OP_PREV_TRACK + " " + type);
        }

        @Override
        public Sec<Void> skipBackward(KeyEventType type) {
            return sendBasicOperation(OP_SKIP_BACKWARD + " " + type);
        }

        @Override
        public Sec<Void> skipForward(KeyEventType type) {
            return sendBasicOperation(OP_SKIP_FORWARD + " " + type);
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
            return sendBasicOperation(OP_CURSOR_SHOW);
        }

        @Override
        public Sec<Void> hideCursor() {
            return sendBasicOperation(OP_CURSOR_HIDE);
        }

        @Override
        public Sec<Void> cursorMove(int x, int y) {
            return sendBasicOperation(OP_CURSOR_MOVE + " " + x + " " + y);
        }

        @Override
        public Sec<Void> leftClick(KeyEventType type) {
            return sendBasicOperation(OP_CURSOR_LEFT_BUTTON + " " + type);
        }

        @Override
        public Sec<Void> cursorContext() {
            return null;
            // TODO

        }

        @Override
        public Sec<Void> powerButton(KeyEventType type) {
            return sendBasicOperation(OP_POWER_BUTTON + " " + type);
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

        @Override
        public Sec<Void> pressExtraButton(String extra) {
            return sendBasicOperation(OP_EXTRA_BUTTON + " " + extra);
        }

        @Override
        public Sec<Boolean> commitText(String input, int newCursorPosition) {
            return sendOperationForBooleanResponse(OP_COMMIT_TEXT + " " + gson.toJson(new CommitTextParams(input, newCursorPosition)));
        }

        @Override
        public Sec<Boolean> deleteSurroundingText(int beforeLength, int afterLength) {
            return sendOperationForBooleanResponse(OP_DELETE_TEXT + " " + gson.toJson(new DeleteTextParams(beforeLength, afterLength)));
        }

        @Override
        public Sec<Boolean> sendKeyEvent(int keyCode, KeyEventType type) {
            return sendOperationForBooleanResponse(OP_KEY_EVENT + " " + gson.toJson(new KeyEventParams(keyCode, type)));
        }

        @Override
        public Sec<Boolean> performDefaultEditorAction() {
            return sendOperationForBooleanResponse(OP_KEYBOARD_DEFAULT_EDITOR_ACTION);
        }
    }

    public boolean isDead() {
        return dead;
    }

    public void close() {
        Log.d(TAG, "close()");
        dead = true;

        if (eventJuggler != null && !eventJuggler.isDead()) {
            eventJuggler.close();
        } else if (eventJuggler == null) {
            tryClose(socket);
            if (reader != null) tryClose(reader);
            if (writer != null) tryClose(writer);
        }
    }

    private OperationDefinition[] getOperationDefinitions() {
        return new OperationDefinition[] {
                new OperationDefinition(OP_EVENT_STREAM_EVENT, eventStreamSubscriptionManager::onIncomingStreamedEvent),
                new OperationDefinition(OP_PING, () -> {}),
        };
    }

}
