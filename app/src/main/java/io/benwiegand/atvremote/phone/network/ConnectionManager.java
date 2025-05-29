package io.benwiegand.atvremote.phone.network;

import static io.benwiegand.atvremote.phone.auth.ssl.KeyUtil.calculateCertificateFingerprint;
import static io.benwiegand.atvremote.phone.auth.ssl.KeyUtil.getSecureRandom;
import static io.benwiegand.atvremote.phone.network.SocketUtil.tryClose;
import static io.benwiegand.atvremote.phone.util.ByteUtil.hexOf;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.time.Instant;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import io.benwiegand.atvremote.phone.auth.ssl.CorruptedKeystoreException;
import io.benwiegand.atvremote.phone.auth.ssl.KeyUtil;
import io.benwiegand.atvremote.phone.auth.ssl.KeystoreManager;
import io.benwiegand.atvremote.phone.protocol.PairingData;
import io.benwiegand.atvremote.phone.protocol.PairingManager;
import io.benwiegand.atvremote.phone.protocol.RequiresPairingException;
import io.benwiegand.atvremote.phone.stuff.LGTMTrustManager;

public class ConnectionManager {
    private static final String TAG = ConnectionManager.class.getSimpleName();

    private final KeystoreManager keystoreManager;
    private final SSLContext sslContext;
    private final SSLContext pairingSslContext;
    private SocketFactory socketFactory = null;
    private SocketFactory pairingSocketFactory = null;
    private final PairingManager pairingManager;

    public ConnectionManager(Context context) {
        keystoreManager = new KeystoreManager(context);
        pairingManager = new PairingManager(context, keystoreManager);
        try {
            sslContext = SSLContext.getInstance("TLS");
            pairingSslContext = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            // this is unlikely. if this ever happens it's probably a bug?
            Log.wtf(TAG, "unable to instantiate an SSLContext because JDK lacks TLS", e);
            throw new UnsupportedOperationException("your device lacks TLS support?!", e);
        }

    }

    public void initializeSSL() throws IOException, CorruptedKeystoreException, KeyManagementException {
        keystoreManager.loadKeystore();

        refreshCertificates();

        // use an insecure context for pairing because the certificate isn't trusted yet
        pairingSslContext.init(
                keystoreManager.getKeyManagers(),
                new TrustManager[] {new LGTMTrustManager()},
                getSecureRandom()
        );

        pairingSocketFactory = pairingSslContext.getSocketFactory();
    }

    public void refreshCertificates() throws CorruptedKeystoreException, KeyManagementException {
        sslContext.init(
                keystoreManager.getKeyManagers(),
                keystoreManager.getTrustManagers(),
                getSecureRandom()
        );

        socketFactory = sslContext.getSocketFactory();
    }

    private SSLSocket openSocket(String hostname, int port, boolean pairing) throws IOException {
        SocketFactory factory = pairing ? pairingSocketFactory : socketFactory;
        if (factory == null) throw new IllegalStateException("must call initializeSSL() first");

        Log.d(TAG, "connecting to tv at address: " + hostname + ":" + port);
        SSLSocket socket = (SSLSocket) factory.createSocket(hostname, port);
        try {
            socket.setTrafficClass(0x10 /* lowdelay */);
            socket.startHandshake();
            Log.d(TAG, "CipherSuite: " + socket.getSession().getCipherSuite());
            Log.d(TAG, "Protocol: " + socket.getSession().getProtocol());
            Log.d(TAG, "PeerHost: " + socket.getSession().getPeerHost());
            Log.d(TAG, "LocalPrincipal: " + socket.getSession().getLocalPrincipal());

            return socket;
        } catch (Throwable t) {
            tryClose(socket);
            throw t;
        }
    }

    public TVReceiverConnection connectToTV(Context context, String hostname, int port, TVReceiverConnectionCallback callback) throws IOException, RequiresPairingException {
        SSLSocket socket;
        try {
            socket = openSocket(hostname, port, false);
        } catch (SSLException e) {
            throw new RequiresPairingException(e);
        }

        Certificate cert = KeyUtil.getRemoteCertificate(socket);
        if (cert == null) throw new IOException("TV didn't send an SSL certificate");

        String token;
        try {
            String fingerprint = hexOf(calculateCertificateFingerprint(cert));
            Log.d(TAG, "certificate fingerprint: " + fingerprint);

            PairingData pairingData = pairingManager.fetchPairingData(fingerprint);
            if (pairingData == null) throw new RequiresPairingException("certificate unknown");
            token = pairingData.token();

            // update additional pairing info in a new thread because it takes forever
            new Thread(() -> {
                PairingData updatedPairingData = pairingData.updateLastConnection(hostname, Instant.now().getEpochSecond());
                boolean committed = pairingManager.updatePairingData(updatedPairingData);  // not the end of the world if this fails
                if (!committed) Log.w(TAG, "failed to commit updated pairing data");
            }).start();

        } catch (CorruptedKeystoreException e) {
            throw new RuntimeException("TV sent bad cert", e);
        }

        TVReceiverConnection connection = new TVReceiverConnection(context, socket, callback, token);
        connection.init();
        return connection;
    }

    public TVReceiverConnection startPairingToTV(Context context, String hostname, int port, TVReceiverConnectionCallback callback) throws IOException {
        SSLSocket socket = openSocket(hostname, port, true);
        TVReceiverConnection connection = new TVReceiverConnection(context, socket, callback);
        try {
            connection.init();
        } catch (RequiresPairingException e) {
            Log.wtf(TAG, "got RequiresPairingException while connecting for pairing!", e);
            throw new AssertionError("Got RequiresPairingException during pairing. This shouldn't happen, if it does it's a bug", e);
        }
        return connection;
    }

    public PairingManager getPairingManager() {
        return pairingManager;
    }
}
