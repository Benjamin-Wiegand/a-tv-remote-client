package io.benwiegand.atvremote.phone.dummytv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static io.benwiegand.atvremote.phone.helper.TestUtil.catchAll;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.INIT_OP_CONNECT;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.INIT_OP_PAIR;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.OP_CONFIRM;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.OP_PING;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.OP_UNAUTHORIZED;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.VERSION_1;

import android.util.Log;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;

import io.benwiegand.atvremote.phone.network.TCPReader;
import io.benwiegand.atvremote.phone.network.TCPWriter;

public class FakeTvConnection {
    private static final String TAG = FakeTvConnection.class.getSimpleName();

    public static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final int SOCKET_AUTH_TIMEOUT = 3000;
    private static final long KEEPALIVE_INTERVAL = 5000;
    private static final long KEEPALIVE_TIMEOUT = KEEPALIVE_INTERVAL * 2;

    public static final String TEST_TOKEN = "TEST_TOKEN_1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final int TEST_CODE = 123456;
    public static final int TEST_INCORRECT_CODE = 696969;

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
        catchAll(() -> thread.join(timeout));
        assertTrue("expecting connection to die within timeout", dead);
    }

    private void run() {
        catchAll(() -> {
            try {
                Log.i(TAG, "Connection from " + socket.getRemoteSocketAddress());

                socket.startHandshake();

                TCPReader reader = TCPReader.createFromStream(socket.getInputStream(), CHARSET);
                TCPWriter writer = TCPWriter.createFromStream(socket.getOutputStream(), CHARSET);

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

                Log.i(TAG, "end of connection for " + socket.getRemoteSocketAddress());
                assertTrue("socket should be closed at end of test", socket.isClosed());

            } catch (SSLHandshakeException e) {
                Log.v(TAG, "SSL error, client most likely failed certificate validation (not paired)", e);
            } finally {
                dead = true;
                onDie.run();
            }
        });
    }

    private void doPair(SSLSocket socket, TCPReader reader, TCPWriter writer) throws IOException, InterruptedException {
        String line;
        do {
            writer.sendLine(OP_CONFIRM);
            line = reader.nextLine(KEEPALIVE_TIMEOUT);
            assertNotNull("expecting a ping before the timeout", line);
        } while (!die && line.equals(OP_PING));

        if (die) {
            socket.close();
            return;
        }

        int code = Integer.parseInt(line);
        Log.d(TAG, "got paring code: " + code);

        if (code == TEST_CODE) {
            writer.sendLine(TEST_TOKEN);
        } else {
            writer.sendLine(OP_UNAUTHORIZED);
        }

        socket.close();
    }

    private void doConnect(SSLSocket socket, TCPReader reader, TCPWriter writer) throws IOException, InterruptedException {
        while (!die) {
            String line = reader.nextLine(KEEPALIVE_TIMEOUT);
            assertNotNull("expecting a ping before the timeout", line);

            // always confirm op
            String[] opLine = line.split(" ", 2);
            Log.v(TAG, "got op: " + Arrays.toString(opLine));
            writer.sendLine(OP_CONFIRM);
        }

        socket.close();
    }
}
