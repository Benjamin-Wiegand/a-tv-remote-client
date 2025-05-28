package io.benwiegand.atvremote.phone.dummytv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static io.benwiegand.atvremote.phone.helper.TestUtil.busyWait;
import static io.benwiegand.atvremote.phone.helper.TestUtil.catchAll;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.INIT_OP_CONNECT;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.INIT_OP_PAIR;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.OP_CONFIRM;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.OP_PING;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.OP_TRY_PAIRING_CODE;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.OP_UNAUTHORIZED;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.VERSION_1;

import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;

import io.benwiegand.atvremote.phone.network.EventJuggler;
import io.benwiegand.atvremote.phone.network.TCPReader;
import io.benwiegand.atvremote.phone.network.TCPWriter;
import io.benwiegand.atvremote.phone.protocol.OperationDefinition;
import io.benwiegand.atvremote.phone.protocol.RemoteProtocolException;

public class FakeTvConnection {
    private static final String TAG = FakeTvConnection.class.getSimpleName();

    public static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final int SOCKET_AUTH_TIMEOUT = 3000;
    private static final long KEEPALIVE_INTERVAL = 5000;
    private static final long KEEPALIVE_TIMEOUT = KEEPALIVE_INTERVAL * 2;

    public static final String TEST_ERROR_CODE_INVALID = "THE PAIRING CODE WAS WRONG, BUT THIS MESSAGE IS HOPEFULLY RIGHT";
    public static final String TEST_TOKEN = "TEST_TOKEN_1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final int TEST_CODE = 123456;
    public static final int TEST_INCORRECT_CODE = 696969;

    private EventJuggler eventJuggler = null;
    private final SSLSocket socket;

    private final Thread thread = new Thread(this::run);
    private final Runnable onDie;
    private boolean dead = false;
    private boolean die = false;

    public FakeTvConnection(SSLSocket socket, Runnable onDie) {
        this.socket = socket;
        this.onDie = onDie;
    }

    public void start() {
        thread.start();
    }

    public void stop(long timeout) {
        die = true;
        eventJuggler.close();
        busyWait(() -> dead, 100, timeout);
        assertTrue("expecting connection to die within timeout", dead);
    }

    private void run() {
        catchAll(() -> {
            try {
                Log.i(TAG, "Connection from " + socket.getRemoteSocketAddress());

                socket.startHandshake();

                TCPReader reader = TCPReader.createFromStream(socket.getInputStream(), CHARSET);
                TCPWriter writer = TCPWriter.createFromStream(socket.getOutputStream(), CHARSET);
                eventJuggler = new EventJuggler(InstrumentationRegistry.getInstrumentation().getTargetContext(), reader, writer, this::onSocketDeath, KEEPALIVE_INTERVAL, KEEPALIVE_TIMEOUT);

                assertEquals("protocol version 1",
                        VERSION_1, reader.nextLine(SOCKET_AUTH_TIMEOUT));

                writer.sendLine(OP_CONFIRM);

                String initOp = reader.nextLine(SOCKET_AUTH_TIMEOUT);
                Log.d(TAG, "init op: " + initOp);

                try {
                    switch (initOp) {
                        case INIT_OP_CONNECT -> doConnect(socket, reader, writer);
                        case INIT_OP_PAIR -> doPair(socket, reader, writer);
                        default ->
                                throw new AssertionError("Expected INIT_OP_CONNECT or INIT_OP_PAIR but got " + initOp);
                    }
                } catch (IOException e) {
                    Log.i(TAG, "lost connection with client", e);
                }
            } catch (SSLHandshakeException e) {
                Log.v(TAG, "SSL error, client most likely failed certificate validation (not paired)", e);
                dead = true;
                onDie.run();
            }
        });
    }

    private void onSocketDeath(Throwable throwable) {
        Log.d(TAG, "Socket Death!", throwable);
        dead = true;
        onDie.run();
        catchAll(socket::close);
    }

    private void close() {
        catchAll(socket::close);
    }

    private void doPair(SSLSocket socket, TCPReader reader, TCPWriter writer) throws IOException, InterruptedException {
        // todo: test errors here
        writer.sendLine(OP_CONFIRM);

        eventJuggler.start(new OperationDefinition[]{
                new OperationDefinition(OP_TRY_PAIRING_CODE, codeString -> {
                    int code = Integer.parseInt(codeString);
                    Log.d(TAG, "got paring code: " + code);

                    if (code == TEST_CODE) {
                        return TEST_TOKEN;
                    } else {
                        throw new RemoteProtocolException(TEST_ERROR_CODE_INVALID);
                    }
                }, true),
                new OperationDefinition(OP_PING, () -> {})
        });

    }

    private void doConnect(SSLSocket socket, TCPReader reader, TCPWriter writer) throws IOException, InterruptedException {
        String auth = reader.nextLine(SOCKET_AUTH_TIMEOUT);
        if (TEST_TOKEN.equals(auth)) {
            Log.v(TAG, "auth valid");
            writer.sendLine(OP_CONFIRM);
        } else {
            Log.v(TAG, "auth rejected: " + auth);
            writer.sendLine(OP_UNAUTHORIZED);
            close();
            return;
        }

        eventJuggler.start(new OperationDefinition[]{
                // todo: write actual operations
                new OperationDefinition(OP_PING, () -> {})
        });
    }
}
