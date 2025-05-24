package io.benwiegand.atvremote.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static io.benwiegand.atvremote.phone.helper.TestUtil.blockAndFlatten;
import static io.benwiegand.atvremote.phone.helper.TestUtil.busyWait;
import static io.benwiegand.atvremote.phone.helper.TestUtil.clearAppData;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.core.util.Supplier;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.async.SecAdapter;
import io.benwiegand.atvremote.phone.auth.ssl.CorruptedKeystoreException;
import io.benwiegand.atvremote.phone.auth.ssl.KeystoreManager;
import io.benwiegand.atvremote.phone.dummytv.FakeKeystoreManager;
import io.benwiegand.atvremote.phone.dummytv.FakeTVServer;
import io.benwiegand.atvremote.phone.dummytv.FakeTvConnection;
import io.benwiegand.atvremote.phone.helper.ConnectionCounter;
import io.benwiegand.atvremote.phone.network.ConnectionService;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.protocol.PairingData;
import io.benwiegand.atvremote.phone.protocol.PairingManager;
import io.benwiegand.atvremote.phone.ui.ConnectingActivity;
import io.benwiegand.atvremote.phone.ui.ManualConnectionActivity;
import io.benwiegand.atvremote.phone.ui.PairingActivity;
import io.benwiegand.atvremote.phone.ui.RemoteActivity;
import io.benwiegand.atvremote.phone.ui.TVDiscoveryActivity;

@RunWith(AndroidJUnit4.class)
public class UiTest {
    private static final String TAG = UiTest.class.getSimpleName();

    private final FakeTVServer server = new FakeTVServer();
    private final ConnectionCounter connectionCounter = new ConnectionCounter(server, 5000);

    private <T extends Activity> T launchActivity(Instrumentation in, Class<T> activityClass) {
        Intent intent = new Intent(in.getTargetContext(), activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return activityClass.cast(in.startActivitySync(intent));
    }

    private RemoteActivity launchRemote(Instrumentation in, int port) {
        Intent intent = new Intent(in.getTargetContext(), RemoteActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(ConnectingActivity.EXTRA_DEVICE_NAME, "deez nuts");
        intent.putExtra(ConnectingActivity.EXTRA_HOSTNAME, "localhost");
        intent.putExtra(ConnectingActivity.EXTRA_PORT_NUMBER, port);

        return (RemoteActivity) in.startActivitySync(intent);
    }

    private PairingActivity launchPairingActivity(Instrumentation in, int port) {
        Intent intent = new Intent(in.getTargetContext(), PairingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(ConnectingActivity.EXTRA_DEVICE_NAME, "deez nuts");
        intent.putExtra(ConnectingActivity.EXTRA_HOSTNAME, "localhost");
        intent.putExtra(ConnectingActivity.EXTRA_PORT_NUMBER, port);

        return (PairingActivity) in.startActivitySync(intent);
    }

    // todo: light mode breaks this because the activity gets relaunched, invalidating the one returned by this.
    private <T extends Activity> Sec<T> listenForActivity(Instrumentation in, Class<T> activityClass, long timeout) {
        Instrumentation.ActivityMonitor activityMonitor = in.addMonitor(activityClass.getName(), null, false);

        // there is only currently threadless sec, so add an external thread for now
        SecAdapter.SecWithAdapter<T> secWithAdapter = SecAdapter.createThreadless();
        new Thread(() -> {
            try {
                Activity activity = activityMonitor.waitForActivityWithTimeout(timeout);
                in.removeMonitor(activityMonitor);

                T result = null;
                if (activity != null) {
                    result = activityClass.cast(activity);
                }

                secWithAdapter.secAdapter().provideResult(result);
            } catch (Throwable t) {
                secWithAdapter.secAdapter().throwError(t);
            }
        }).start();

        return secWithAdapter.sec();
    }

    private boolean waitForUiElement(Activity a, @IdRes int id, long timeout) {
        busyWait(() -> a.findViewById(id) != null, 100, timeout);
        return a.findViewById(id) != null;
    }

    private String getTextContent(Activity a, @IdRes int id) throws InterruptedException {
        return runOnUiThreadForResult(a, () -> {
            TextView tv = a.findViewById(id);
            if (tv == null) return null;
            return String.valueOf(tv.getText());
        });
    }

    private void assertStringResourceMatch(Activity a, @IdRes int id, @StringRes int expected) throws InterruptedException {
        assertEquals("expecting text to match",
                a.getString(expected), getTextContent(a, id));
    }

    private void assertStringResourceMatch(Activity a, @IdRes int id, @StringRes int expected, long timeoutMs) throws InterruptedException {
        busyWait(() -> {
            try {
                assertStringResourceMatch(a, id, expected);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }, 100, timeoutMs);

        assertStringResourceMatch(a, id, expected);
    }

    private void enterText(Activity a, @IdRes int target, String text) throws InterruptedException {
        runOnUiThreadSync(a, () -> {
            EditText pairingCodeText = a.findViewById(target);
            pairingCodeText.setText(text);
        });
    }

    private void enterPairingCode(PairingActivity a, int code) throws InterruptedException {
        enterText(a, R.id.pairing_code_text, String.valueOf(code));
    }

    private TVReceiverConnection getClientConnectionFromActivity(PairingActivity a) throws IllegalAccessException, NoSuchFieldException {
        // use reflection for this
        Field conectionField = PairingActivity.class.getDeclaredField("connection");
        conectionField.setAccessible(true);
        TVReceiverConnection connection = (TVReceiverConnection) conectionField.get(a);
        assert connection != null;
        return connection;
    }

    private TVReceiverConnection getClientConnectionFromActivity(RemoteActivity a) throws IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException {
        // use even more reflection for this
        Field binderField = ConnectingActivity.class.getDeclaredField("binder");
        binderField.setAccessible(true);
        ConnectionService.ConnectionServiceBinder binder = (ConnectionService.ConnectionServiceBinder) binderField.get(a);
        assert binder != null;

        Method getConnectionMethod = ConnectionService.ConnectionServiceBinder.class.getDeclaredMethod("getConnection");
        getConnectionMethod.setAccessible(true);
        TVReceiverConnection connection = (TVReceiverConnection) getConnectionMethod.invoke(binder);
        assert connection != null;

        return connection;
    }

    private void runOnUiThreadSync(Activity a, Runnable runnable) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        a.runOnUiThread(() -> {
            runnable.run();
            latch.countDown();
        });
        assert latch.await(5, TimeUnit.SECONDS);
    }

    private <T> T runOnUiThreadForResult(Activity a, Supplier<T> supplier) throws InterruptedException {
        AtomicReference<T> result = new AtomicReference<>(null);
        runOnUiThreadSync(a, () -> result.set(supplier.get()));
        return result.get();
    }

    private boolean clickButton(Activity a, @IdRes int buttonId) throws InterruptedException {
        View button = a.findViewById(buttonId);
        if (button == null) return false;

        busyWait(button::hasOnClickListeners, 100, 1000);
        return runOnUiThreadForResult(a, button::performClick);
    }

    private void assertConnected(RemoteActivity remoteActivity) throws InterruptedException {
        assertStringResourceMatch(remoteActivity, R.id.connection_status_text, R.string.connection_status_ready, 5000);
    }

    private Activity[] toRemoteFromLauncherStack(Instrumentation in) throws Throwable {
        // launch the launcher activity (discovery activity)
        TVDiscoveryActivity discoveryActivity = launchActivity(in, TVDiscoveryActivity.class);

        // go to the manual connection screen
        Sec<ManualConnectionActivity> manualConnectionActivitySec = listenForActivity(in, ManualConnectionActivity.class, 3000);
        clickButton(discoveryActivity, R.id.manual_connection_button);
        ManualConnectionActivity manualConnectionActivity = blockAndFlatten(manualConnectionActivitySec, 3000);
        assertNotNull("expecting ManualConnectionActivity to open", manualConnectionActivity);

        // fill out the details
        enterText(manualConnectionActivity, R.id.hostname_text, "127.0.0.1");
        enterText(manualConnectionActivity, R.id.port_text, String.valueOf(server.getPort()));

        // connect
        Sec<RemoteActivity> remoteActivitySec = listenForActivity(in, RemoteActivity.class, 3000);
        clickButton(manualConnectionActivity, R.id.connect_button);
        RemoteActivity remoteActivity = blockAndFlatten(remoteActivitySec, 3000);
        assertNotNull("expecting RemoteActivity to open", remoteActivity);

        return new Activity[]{remoteActivity, manualConnectionActivity, discoveryActivity};
    }

    private RemoteActivity toRemoteFromLauncher(Instrumentation in) throws Throwable {
        Activity[] stack = toRemoteFromLauncherStack(in);
        return (RemoteActivity) stack[0];
    }

    private RemoteActivity doPairing(Instrumentation in, PairingActivity pairingActivity) throws Throwable {
        assertTrue("expecting pairing screen",
                waitForUiElement(pairingActivity, R.id.pairing_code_text, 5000));
        TVReceiverConnection connection = getClientConnectionFromActivity(pairingActivity);
        connectionCounter.expectConnection();

        // enter the pairing code
        enterPairingCode(pairingActivity, FakeTvConnection.TEST_CODE);

        assertTrue( "expecting to click next button",
                clickButton(pairingActivity, R.id.next_button));

        assertTrue("expecting fingerprint verification screen",
                waitForUiElement(pairingActivity, R.id.match_button, 5000));

        Sec<RemoteActivity> remoteActivitySec = listenForActivity(in, RemoteActivity.class, 3000);

        assertTrue( "expecting to click fingerprint match button",
                clickButton(pairingActivity, R.id.match_button));

        // should be disconnected
        busyWait(connection::isDead, 100, 5000);
        assertTrue("connection should be dead after pairing completes", connection.isDead());

        // remote activity should be open
        RemoteActivity remoteActivity = blockAndFlatten(remoteActivitySec, 3000);
        assertNotNull("expecting RemoteActivity to open", remoteActivity);

        // it should successfully connect (and not go to pairing again)
        assertConnected(remoteActivity);

        connectionCounter.expect(1, 1);

        return remoteActivity;
    }

    /**
     * skips pairing process and installs pairing data directly
     */
    private void installTestPairingData(Instrumentation in) throws IOException, CorruptedKeystoreException {
        Log.i(TAG, "Installing test pairing data");
        Context context = in.getTargetContext();

        KeystoreManager km = new KeystoreManager(context);
        km.loadKeystore();

        PairingManager pm = new PairingManager(context, km);

        assert pm.addNewDevice(
                FakeKeystoreManager.getTestCert(),
                new PairingData(
                        FakeTvConnection.TEST_TOKEN,
                        FakeKeystoreManager.TEST_CERTIFICATE_FINGERPRINT,
                        "test device", "127.0.0.1",
                        Instant.now().getEpochSecond()));

    }

    /**
     * tests pairing activity
     * <ul>
     *     <li>lost connection</li>
     *     <li>wrong pairing code</li>
     *     <li>fingerprint miss-match</li>
     *     <li>correct pairing code</li>
     *     <li>smooth transition to remote activity</li>
     * </ul>
     */
    @Test
    public void pairing_Test() throws Throwable {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        clearAppData(in.getTargetContext());
        assert !ActivityManager.isUserAMonkey(); // mandatory check

        // these delays account for animations, without these it can grab the old buttons before they disappear
        // yes I know using fragments will fix this, I'll switch to them eventually
        final long animationDelay = PairingActivity.SCREEN_TRANSITION_DURATION;

        server.start();

        // navigate to remote to trigger pairing
        Sec<PairingActivity> pairingActivitySec = listenForActivity(in, PairingActivity.class, 5000);
        toRemoteFromLauncher(in);
        PairingActivity a = blockAndFlatten(pairingActivitySec, 5000);

        assertNotNull("expecting PairingActivity to open", a);

        // test disconnection

        assertTrue("expecting pairing screen",
                waitForUiElement(a, R.id.pairing_code_text, 5000));
        TVReceiverConnection connection = getClientConnectionFromActivity(a);
        connectionCounter.expect(2, 1);

        // kill connection from server
        FakeTvConnection serverConnection = server.getConnections().get(0);
        serverConnection.stop(5000);

        // wait for it to disconnect on client
        busyWait(connection::isDead, 100, 5000);
        assertTrue("connection should be dead after killing it", connection.isDead());
        connectionCounter.expectDisconnection();

        // error screen should show
        assertTrue("expecting error screen",
                waitForUiElement(a, R.id.stack_trace_dropdown, 5000));
        Thread.sleep(animationDelay);
        assertStringResourceMatch(a, R.id.description_text, R.string.description_pairing_error_connection_lost);

        // retry
        assertTrue("expecting to click retry button",
                clickButton(a, R.id.positive_button));

        Thread.sleep(animationDelay);


        // test wrong code

        assertTrue("expecting pairing screen",
                waitForUiElement(a, R.id.pairing_code_text, 5000));
        connection = getClientConnectionFromActivity(a);
        connectionCounter.expectConnection();

        // enter the wrong code
        enterPairingCode(a, FakeTvConnection.TEST_INCORRECT_CODE);

        assertTrue( "expecting to click next button",
                clickButton(a, R.id.next_button));

        assertTrue("expecting fingerprint verification screen",
                waitForUiElement(a, R.id.match_button, 5000));

        assertTrue( "expecting to click fingerprint match button",
                clickButton(a, R.id.match_button));

        // error should show
        assertTrue("expecting error screen",
                waitForUiElement(a, R.id.stack_trace_dropdown, 5000));
        Thread.sleep(animationDelay);
        assertStringResourceMatch(a, R.id.description_text, R.string.protocol_error_pairing_code_invalid);

        // should be disconnected
        busyWait(connection::isDead, 100, 5000);
        assertTrue("connection should be dead after entering wrong code", connection.isDead());
        connectionCounter.expectDisconnection();

        // retry
        assertTrue( "expecting to click retry button",
                clickButton(a, R.id.positive_button));

        Thread.sleep(animationDelay);


        // test no match

        assertTrue("expecting pairing screen",
                waitForUiElement(a, R.id.pairing_code_text, 5000));
        connection = getClientConnectionFromActivity(a);
        connectionCounter.expectConnection();

        // enter any code (doesn't matter)
        enterPairingCode(a, FakeTvConnection.TEST_CODE);

        assertTrue( "expecting to click next button",
                clickButton(a, R.id.next_button));

        assertTrue("expecting fingerprint verification screen",
                waitForUiElement(a, R.id.match_button, 5000));

        // hit the no match button
        assertTrue( "expecting to click fingerprint no match button",
                clickButton(a, R.id.no_match_button));

        // error should show
        assertTrue("expecting error screen",
                waitForUiElement(a, R.id.stack_trace_dropdown, 5000));
        Thread.sleep(animationDelay);
        assertStringResourceMatch(a, R.id.description_text, R.string.description_pairing_error_fingerprint_differs);

        // should be disconnected
        busyWait(connection::isDead, 100, 5000);
        assertTrue("connection should be dead after hitting no match button", connection.isDead());
        connectionCounter.expectDisconnection();

        // retry
        assertTrue( "expecting to click retry button",
                clickButton(a, R.id.positive_button));

        Thread.sleep(animationDelay);


        // test correct code

        doPairing(in, a);


        server.stop();
    }

    /**
     * simply sits idle on the pairing screen for a few seconds.
     * I've had a few bugs with pings that this would catch.
     */
    @Test
    public void pairingIdle_test() throws Throwable {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        clearAppData(in.getTargetContext());
        server.start();

        // navigate to remote to trigger pairing
        Sec<PairingActivity> pairingActivitySec = listenForActivity(in, PairingActivity.class, 5000);
        toRemoteFromLauncher(in);
        PairingActivity a = blockAndFlatten(pairingActivitySec, 5000);

        assertTrue("expecting pairing screen",
                waitForUiElement(a, R.id.pairing_code_text, 5000));
        TVReceiverConnection connection = getClientConnectionFromActivity(a);
        connectionCounter.expect(2, 1);

        // wait for twice the keepalive interval
        Thread.sleep(TVReceiverConnection.KEEPALIVE_INTERVAL * 2);

        // nothing should have exploded
        assertFalse("expecting connection to stay alive after idling",
                connection.isDead());
        assertFalse("expecting no error showing after idling",
                waitForUiElement(a, R.id.stack_trace_dropdown, 100));
        connectionCounter.assertConnections();

        server.stop();
    }

    /**
     * sanity check to verify that installTestPairingData() works
     */
    @Test
    public void testPairingData_Test() throws InterruptedException, IOException, CorruptedKeystoreException {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        clearAppData(in.getTargetContext());
        server.start();
        installTestPairingData(in);

        RemoteActivity remoteActivity = launchRemote(in, server.getPort());

        assertConnected(remoteActivity);
        connectionCounter.expectConnection();

        server.stop();
    }

    /**
     * tests connecting to a paired device via the manual connection button
     */
    @Test
    public void manualConnection_Test() throws Throwable {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        clearAppData(in.getTargetContext());
        server.start();
        installTestPairingData(in);

        RemoteActivity remoteActivity = toRemoteFromLauncher(in);
        assertConnected(remoteActivity);
        connectionCounter.expectConnection();

        server.stop();
    }

    /**
     * tests pairing, closing the app, and reopening and reconnecting
     */
    @Test
    public void pairingPersistence_Test() throws Throwable {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        clearAppData(in.getTargetContext());
        server.start();

        // navigate to remote to trigger pairing
        Sec<PairingActivity> pairingActivitySec = listenForActivity(in, PairingActivity.class, 5000);
        Activity[] stack = toRemoteFromLauncherStack(in);
        PairingActivity pairingActivity = blockAndFlatten(pairingActivitySec, 5000);
        connectionCounter.expect(1, 1);

        assertTrue("expecting pairing screen",
                waitForUiElement(pairingActivity, R.id.pairing_code_text, 5000));

        // pair
        RemoteActivity remoteActivity = doPairing(in, pairingActivity);

        // disconnect
        TVReceiverConnection connection = getClientConnectionFromActivity(remoteActivity);
        clickButton(remoteActivity, R.id.disconnect_button);

        busyWait(connection::isDead, 100, 5000);
        assertTrue("connection should be dead after hitting the disconnect button", connection.isDead());
        connectionCounter.expectDisconnection();

        // close all the remaining activities
        for (int i = 1; i < stack.length; i++) {
            stack[i].finish();
        }


        // launch remote again to ensure pairing data retention
        remoteActivity = toRemoteFromLauncher(in);
        assertConnected(remoteActivity);
        connectionCounter.expectConnection();

        server.stop();
    }

    /**
     * test connecting for remote/pairing, then pressing the disconnect button
     */
    @Test
    public void remoteDisconnection_Test() throws Throwable {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        clearAppData(in.getTargetContext());
        server.start();


        // navigate to remote to trigger pairing
        Sec<PairingActivity> pairingActivitySec = listenForActivity(in, PairingActivity.class, 5000);
        Activity[] stack = toRemoteFromLauncherStack(in);
        PairingActivity pairingActivity = blockAndFlatten(pairingActivitySec, 5000);

        assertTrue("expecting pairing screen",
                waitForUiElement(pairingActivity, R.id.pairing_code_text, 5000));
        connectionCounter.expect(2, 1);

        // disconnect
        TVReceiverConnection connection = getClientConnectionFromActivity(pairingActivity);
        clickButton(pairingActivity, R.id.cancel_button);

        busyWait(connection::isDead, 100, 5000);
        assertTrue("connection should be dead after hitting the disconnect button", connection.isDead());
        connectionCounter.expectDisconnection();

        // close all the remaining activities
        for (int i = 1; i < stack.length; i++) {
            stack[i].finish();
        }


        // now test RemoteActivity
        installTestPairingData(in);


        // open remote
        RemoteActivity remoteActivity = toRemoteFromLauncher(in);
        assertConnected(remoteActivity);
        connectionCounter.expectConnection();

        // disconnect
        connection = getClientConnectionFromActivity(remoteActivity);
        clickButton(remoteActivity, R.id.disconnect_button);

        busyWait(connection::isDead, 100, 5000);
        assertTrue("connection should be dead after hitting the disconnect button", connection.isDead());
        connectionCounter.expectDisconnection();


        server.stop();
    }



}
