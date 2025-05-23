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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.dummytv.FakeTVServer;
import io.benwiegand.atvremote.phone.dummytv.FakeTvConnection;
import io.benwiegand.atvremote.phone.helper.FlatConnectionServiceCallback;
import io.benwiegand.atvremote.phone.network.ConnectionService;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.protocol.PairingManager;
import io.benwiegand.atvremote.phone.ui.ErrorMessageException;

@RunWith(AndroidJUnit4.class)
public class ConnectionTest {
    private static final String TAG = ConnectionTest.class.getSimpleName();

    FakeTVServer server = new FakeTVServer();
    FlatConnectionServiceCallback callback = new FlatConnectionServiceCallback();

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

    public void assertConnections(FakeTVServer server, int connects, int disconnects) {
        server.waitForCounters(connects, disconnects, 5000);
        assertEquals("expecting " + connects + " total connections to fake TV receiver", connects, server.getTotalConnects());
        assertEquals("expecting " + disconnects + " total disconnections from fake TV receiver", disconnects, server.getTotalDisconnects());
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
        assertConnections(server, 1, 0);

        // second connection should not reconnect
        TVReceiverConnection secondConnection = doConnection(server, true,  true);
        assertSame("expecting second connection request to the same TV to return the initial connection", connection, secondConnection);
        assertConnections(server, 1, 0);

        // even after calling init() again, it should not reconnect
        doServiceInit();
        TVReceiverConnection thirdConnection = doConnection(server, true, true);
        assertSame("expecting third connection request to the same TV after calling init a second time to also return the initial connection", connection, thirdConnection);
        assertConnections(server, 1, 0);

        // disconnect
        binder.disconnect();
        callback.waitForNextCall(6, TimeUnit.SECONDS);
        Object[] args = callback.assertCallTo("onDisconnected");
        assertNull("expecting no throwable in onDisconnected() callback for call to binder.disconnect()", args[0]);
        callback.assertNoMoreCalls("expecting no calls other than onDisconnected");
        assertConnections(server, 1, 1);

        // _now_ reconnecting should increment the total connections
        TVReceiverConnection fourthConnection = doConnection(server, false, true);
        assertNotSame("expecting connection request after disconnect to return a new connection", connection, fourthConnection);
        assertConnections(server, 2, 1);


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
        assertConnections(server, 1, 0);

        // disconnect via unregister
        binder.unregister(callback, true);
        busyWait(connection::isDead, 100, 6000); // no way to receive the callback
        assertTrue("expecting disconnect after calling unregister() with disconnect = true", connection.isDead());
        callback.assertNoMoreCalls("expecting no calls because that should be impossible (no callback registered)");
        assertConnections(server, 1, 1);

        // re-register
        binder.register(callback);

        // connect
        TVReceiverConnection secondConnection = doConnection(server, false, true);
        assertNotSame("expecting connection request after disconnect to return a new connection", connection, secondConnection); // sanity
        assertConnections(server, 2, 1);

        // disconnect via killing service
        stopService(context);
        busyWait(secondConnection::isDead, 100, 6000);
        assertTrue("expecting disconnect after calling unregister() with disconnect = true", secondConnection.isDead());
        Object[] args = callback.assertCallTo("onDisconnected");
        assertNull("expecting no throwable in onDisconnected() callback for call to binder.disconnect()", args[0]);
        assertConnections(server, 2, 2);

        // restart for teardown
        startService(context);

        doFullServiceTeardown(context);

        server.stop();
    }

    /**
     * tests general pairing flow (todo: finish this)
     */
    @Test
    public void pairing_Test() {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        Context context = in.getTargetContext();

        server.start();

        doFullServiceInit(context);


        // connect for pairing
        TVReceiverConnection connection = doConnection(server, false, true);
        assertConnections(server, 1, 0);

        // try the wrong code
        Sec<String> tokenSec = connection.sendPairingCode(String.valueOf(696969));
        assert block(tokenSec, 5, TimeUnit.SECONDS);
        assertFalse("expecting unsuccessful pairing", tokenSec.isSuccessful());
        assertTrue("expecting an error extending ErrorMessageException", tokenSec.getError() instanceof ErrorMessageException);

        // ensure disconnected
        callback.waitForNextCall(6, TimeUnit.SECONDS);
        callback.assertCallTo("onDisconnected");    // throwable here is undefined behavior (it doesn't matter)
        callback.assertNoMoreCalls("expecting no calls other than onDisconnected");
        assertConnections(server, 1, 1);


        // connect for pairing again
        connection = doConnection(server, false, true);
        assertConnections(server, 2, 1);

        // try to pair for real this time
        tokenSec = connection.sendPairingCode(String.valueOf(FakeTvConnection.TEST_CODE));
        assert block(tokenSec, 5, TimeUnit.SECONDS);
        assertTrue("expecting successful pairing", tokenSec.isSuccessful());
        String token = tokenSec.getResult();

        assertEquals("expecting the test token", FakeTvConnection.TEST_TOKEN, token);


        doFullServiceTeardown(context);
        assertConnections(server, 2, 2);

        server.stop();
    }

}
