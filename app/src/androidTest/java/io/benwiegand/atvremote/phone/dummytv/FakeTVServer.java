package io.benwiegand.atvremote.phone.dummytv;

import static io.benwiegand.atvremote.phone.helper.TestUtil.catchAll;

import android.util.Log;

import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

public class FakeTVServer {
    private static final String TAG = FakeTVServer.class.getSimpleName();

    // ssl
    private ServerSocketFactory serverSocketFactory;
    private FakeKeystoreManager keystoreManager;

    private int port = -1;
    private final CountDownLatch spinUpLatch = new CountDownLatch(1);

    private final LinkedList<FakeTvConnection> connections = new LinkedList<>();

    private int totalConnects = 0;
    private int totalDisconnects = 0;

    // thread
    private final Thread thread = new Thread(this::loop);
    private boolean dead = false;

    public void start() {
        Log.i(TAG, "starting fake TV receiver");
        catchAll(() -> {
            keystoreManager = new FakeKeystoreManager();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keystoreManager.getKeyManagers(), keystoreManager.getTrustManagers(), SecureRandom.getInstanceStrong());

            serverSocketFactory = sslContext.getServerSocketFactory();

            thread.start();

            assert spinUpLatch.await(5, TimeUnit.SECONDS);
        });
    }

    public void stop() {
        Log.i(TAG, "stopping fake TV receiver");
        dead = true;
    }

    private void loop() {
        catchAll(() -> {
            try (ServerSocket serverSock = serverSocketFactory.createServerSocket(0)) {
                port = serverSock.getLocalPort();

                Log.d(TAG, "listening on port " + serverSock.getLocalPort());
                spinUpLatch.countDown();

                while (!dead) {
                    SSLSocket socket = (SSLSocket) serverSock.accept();

                    totalConnects++;

                    AtomicReference<FakeTvConnection> aConnection = new AtomicReference<>();
                    FakeTvConnection connection = new FakeTvConnection(socket, () -> {
                        totalDisconnects++;
                        connections.remove(aConnection.get());
                    });
                    aConnection.set(connection);
                    connections.add(connection);

                    connection.start();
                }
            }
        });
    }

    public List<FakeTvConnection> getConnections() {
        return connections;
    }

    public int getTotalConnects() {
        return totalConnects;
    }

    public int getTotalDisconnects() {
        return totalDisconnects;
    }

    public int getPort() {
        return port;
    }
}
