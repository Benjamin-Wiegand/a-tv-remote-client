package io.benwiegand.atvremote.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static io.benwiegand.atvremote.phone.helper.TestUtil.block;
import static io.benwiegand.atvremote.phone.helper.TestUtil.busyWait;
import static io.benwiegand.atvremote.phone.helper.TestUtil.catchAll;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.auth.ssl.KeyUtil;
import io.benwiegand.atvremote.phone.control.InputHandler;
import io.benwiegand.atvremote.phone.dummytv.FakeKeystoreManager;
import io.benwiegand.atvremote.phone.dummytv.FakeTVServer;
import io.benwiegand.atvremote.phone.dummytv.FakeTvConnection;
import io.benwiegand.atvremote.phone.helper.ConnectionCounter;
import io.benwiegand.atvremote.phone.helper.FlatConnectionServiceCallback;
import io.benwiegand.atvremote.phone.network.ConnectionService;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.protocol.PairingData;
import io.benwiegand.atvremote.phone.protocol.PairingManager;
import io.benwiegand.atvremote.phone.ui.ErrorMessageException;
import io.benwiegand.atvremote.phone.util.ByteUtil;

@RunWith(AndroidJUnit4.class)
public class ConnectionTest {
    private static final String TAG = ConnectionTest.class.getSimpleName();

    FakeTVServer server = new FakeTVServer();
    FlatConnectionServiceCallback callback = new FlatConnectionServiceCallback();
    ConnectionCounter connectionCounter = new ConnectionCounter(server, 5000);

    ConnectionService.ConnectionServiceBinder binder = null;
    CountDownLatch bindLatch = new CountDownLatch(1);

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG, "service connected");
            binder = (ConnectionService.ConnectionServiceBinder) service;
            bindLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(TAG, "service disconnected");
        }
    };


    private void startService(Context context) {
        Log.d(TAG, "starting service");
        Intent intent = new Intent(context, ConnectionService.class);
        context.startService(intent);
        assert context.bindService(intent, conn, Context.BIND_AUTO_CREATE);

        catchAll(() -> bindLatch.await(5, TimeUnit.SECONDS));
        assertNotNull("ConnectionService bind", binder);
    }

    public void stopService(Context context) {
        Log.d(TAG, "stopping service");
        Intent intent = new Intent(context, ConnectionService.class);
        context.unbindService(conn);
        context.stopService(intent);
    }

    public void doServiceInit() {
        binder.init();
        callback.waitForNextCall(5, TimeUnit.SECONDS);
        callback.assertCallTo("onServiceInit");
        callback.assertNoMoreCalls("expecting only onServiceInit was called");
    }

    public void doFullServiceInit(Context context) {
        startService(context);
        binder.register(callback);
        doServiceInit();
    }

    public void doFullServiceTeardown(Context context) {
        binder.unregister(callback, true);
        stopService(context);
    }

    public TVReceiverConnection doConnection(FakeTVServer server, boolean expectLazy, boolean forPairing) {
        binder.connect("fake TV", "localhost", server.getPort(), forPairing);

        if (!expectLazy) {
            callback.waitForNextCall(20, TimeUnit.SECONDS);
            callback.assertCallTo("onSocketConnected");
        }

        callback.waitForNextCall(10, TimeUnit.SECONDS);
        Object[] args = callback.assertCallTo("onConnected");

        TVReceiverConnection connection = (TVReceiverConnection) args[0];
        assertFalse("expecting connection alive", connection.isDead());

        callback.assertNoMoreCalls("expecting only onConnected");

        return connection;
    }

    /**
     * basic sanity check. ensures the test can bind to the ConnectionService
     */
    @Test
    public void serviceInit_Test() {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        Context context = in.getTargetContext();

        startService(context);

        binder.register(callback);

        assertFalse("expecting service not initialized before initializing it", binder.isInitialized());
        doServiceInit();
        assertTrue("expecting service initialized", binder.isInitialized());

        PairingManager test = binder.getPairingManager();
        doServiceInit();
        assertTrue("expecting service still initialized", binder.isInitialized());
        assertSame("expecting the same PairingManager as the service doesn't redo initialization", test, binder.getPairingManager());

        binder.unregister(callback, true);

        stopService(context);
    }

    /**
     * ensures connecting works, repeated calls to connect() return the same initial connection,
     * and calling connect() after disconnect() does actually return a new connection.
     */
    @Test
    public void connect_Test() {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        Context context = in.getTargetContext();

        server.start();

        doFullServiceInit(context);


        // normal connection
        TVReceiverConnection connection = doConnection(server, false, true);
        connectionCounter.expectConnection();

        // second connection should not reconnect
        TVReceiverConnection secondConnection = doConnection(server, true,  true);
        assertSame("expecting second connection request to the same TV to return the initial connection", connection, secondConnection);
        connectionCounter.assertConnections();

        // even after calling init() again, it should not reconnect
        doServiceInit();
        TVReceiverConnection thirdConnection = doConnection(server, true, true);
        assertSame("expecting third connection request to the same TV after calling init a second time to also return the initial connection", connection, thirdConnection);
        connectionCounter.assertConnections();

        // disconnect
        binder.disconnect();
        callback.waitForNextCall(6, TimeUnit.SECONDS);
        Object[] args = callback.assertCallTo("onDisconnected");
        assertNull("expecting no throwable in onDisconnected() callback for call to binder.disconnect()", args[0]);
        callback.assertNoMoreCalls("expecting no calls other than onDisconnected");
        connectionCounter.expectDisconnection();

        // _now_ reconnecting should increment the total connections
        TVReceiverConnection fourthConnection = doConnection(server, false, true);
        assertNotSame("expecting connection request after disconnect to return a new connection", connection, fourthConnection);
        connectionCounter.expectConnection();


        doFullServiceTeardown(context);

        server.stop();
    }

    /**
     * tests onConnectError() callback by having the server reject the socket before the ssl handshake
     */
    @Test
    public void badDestination_Test() {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        Context context = in.getTargetContext();

        server.start();
        server.setReject(true);

        doFullServiceInit(context);


        // connect
        binder.connect("fake TV", "localhost", server.getPort(), true);
        callback.waitForNextCall(5, TimeUnit.SECONDS);
        Object[] args = callback.assertCallTo("onConnectError");
        callback.assertNoMoreCalls("expecting no other calls after onConnectError");
        assertNotNull("throwable in onConnectError should never be null", args[0]);
        assertTrue("expecting throwable of type IOException in onConnectError", args[0] instanceof IOException);


        doFullServiceTeardown(context);

        server.stop();
    }

    /**
     * test automatic disconnections for unregister() and stopService()
     */
    @Test
    public void autoDisconnect_Test() {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        Context context = in.getTargetContext();

        server.start();

        doFullServiceInit(context);

        // connect
        TVReceiverConnection connection = doConnection(server, false, true);
        connectionCounter.expectConnection();

        // disconnect via unregister
        binder.unregister(callback, true);
        busyWait(connection::isDead, 100, 6000); // no way to receive the callback
        assertTrue("expecting disconnect after calling unregister() with disconnect = true", connection.isDead());
        callback.assertNoMoreCalls("expecting no calls because that should be impossible (no callback registered)");
        connectionCounter.expectDisconnection();

        // re-register
        binder.register(callback);

        // connect
        TVReceiverConnection secondConnection = doConnection(server, false, true);
        assertNotSame("expecting connection request after disconnect to return a new connection", connection, secondConnection); // sanity
        connectionCounter.expectConnection();

        // disconnect via killing service
        stopService(context);
        busyWait(secondConnection::isDead, 100, 6000);
        assertTrue("expecting disconnect after calling unregister() with disconnect = true", secondConnection.isDead());
        callback.waitForNextCall(5, TimeUnit.SECONDS);
        Object[] args = callback.assertCallTo("onDisconnected");
        assertNull("expecting no throwable in onDisconnected() callback for call to binder.disconnect()", args[0]);
        connectionCounter.expectDisconnection();

        // restart for teardown
        startService(context);

        doFullServiceTeardown(context);

        server.stop();
    }

    /**
     * tests critical pairing functionality:
     * <ul>
     *     <li>getting an error message on bad pairing code</li>
     *     <li>certificate matches the one on the server</li>
     *     <li>certificate fingerprint calculation</li>
     *     <li>saving certificate</li>
     *     <li>saving pairing data</li>
     *     <li>connecting to saved device with certificate and pairing data</li>
     * </ul>
     */
    @Test
    public void pairing_Test() {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        Context context = in.getTargetContext();

        server.start();

        doFullServiceInit(context);


        {
            Log.i(TAG, "connecting for pairing with wrong code");

            // connect for pairing
            TVReceiverConnection connection = doConnection(server, false, true);
            connectionCounter.expectConnection();

            // try the wrong code
            Sec<String> tokenSec = connection.sendPairingCode(String.valueOf(FakeTvConnection.TEST_INCORRECT_CODE));
            assert block(tokenSec, 5, TimeUnit.SECONDS);
            assertFalse("expecting unsuccessful pairing", tokenSec.isSuccessful());
            assertTrue("expecting an error extending ErrorMessageException", tokenSec.getError() instanceof ErrorMessageException);

            // ensure disconnected
            callback.waitForNextCall(6, TimeUnit.SECONDS);
            callback.assertCallTo("onDisconnected");    // throwable here is undefined behavior (it doesn't matter)
            callback.assertNoMoreCalls("expecting no calls other than onDisconnected");
            connectionCounter.expectDisconnection();
        }

        {
            Log.i(TAG, "connecting for pairing (for real this time)");

            // connect for pairing again
            TVReceiverConnection connection = doConnection(server, false, true);
            connectionCounter.expectConnection();

            // verify certificate
            Certificate certificate = catchAll(connection::getCertificate);
            assertEquals("expecting certificate to match the test certificate",
                    FakeKeystoreManager.getTestCert(), certificate);

            // verify fingerprint
            byte[] fingerprint = catchAll(() -> KeyUtil.calculateCertificateFingerprint(certificate));
            assertEquals("expecting test certificate fingerprint to match precalculated value",
                    FakeKeystoreManager.TEST_CERTIFICATE_FINGERPRINT, ByteUtil.hexOf(fingerprint));

            // try to pair for real this time
            Sec<String> tokenSec = connection.sendPairingCode(String.valueOf(FakeTvConnection.TEST_CODE));
            assert block(tokenSec, 5, TimeUnit.SECONDS);
            assertTrue("expecting successful pairing", tokenSec.isSuccessful());

            // check the received token
            String token = tokenSec.getResult();
            assertEquals("expecting the test token", FakeTvConnection.TEST_TOKEN, token);

            // register to pairing manager
            PairingData pairingData = new PairingData(token, fingerprint, "not a real tv", "127.0.0.1", Instant.now().getEpochSecond());
            boolean committed = catchAll(() -> binder.getPairingManager().addNewDevice(certificate, pairingData));
            assertTrue("expected pairing data commit to be successful", committed);

            binder.refreshCertificates();
            assertTrue("expecting service to refresh certificates without falling back to re-init", binder.isInitialized());

            // TV no longer disconnects automatically
            binder.disconnect();

            // ensure disconnected
            callback.waitForNextCall(6, TimeUnit.SECONDS);
            callback.assertCallTo("onDisconnected");    // throwable here is undefined behavior (it doesn't matter)
            callback.assertNoMoreCalls("expecting no calls other than onDisconnected");
            connectionCounter.expectDisconnection();
        }

        {
            Log.i(TAG, "connecting normally now that fake TV is paired");

            // connect for remote
            TVReceiverConnection connection = doConnection(server, false, false);
            connectionCounter.expectConnection();

            InputHandler forwarder = connection.getInputForwarder();

            // just look for a success response for now
            Sec<Void> testOp = forwarder.dpadUp();
            block(testOp, 2, TimeUnit.SECONDS);
            assertTrue("expected test operation to work", testOp.isSuccessful());

            // callback should be quiet
            callback.assertNoMoreCalls("expected no calls while nothing relevant is happening");
        }


        doFullServiceTeardown(context);
        connectionCounter.expectDisconnection();

        server.stop();
    }

}
