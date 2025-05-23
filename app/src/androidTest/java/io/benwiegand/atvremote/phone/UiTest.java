package io.benwiegand.atvremote.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static io.benwiegand.atvremote.phone.helper.TestUtil.busyWait;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.IdRes;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

import io.benwiegand.atvremote.phone.dummytv.FakeTVServer;
import io.benwiegand.atvremote.phone.dummytv.FakeTvConnection;
import io.benwiegand.atvremote.phone.helper.ConnectionCounter;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.ui.ConnectingActivity;
import io.benwiegand.atvremote.phone.ui.PairingActivity;
import io.benwiegand.atvremote.phone.ui.RemoteActivity;

@RunWith(AndroidJUnit4.class)
public class UiTest {

    private final FakeTVServer server = new FakeTVServer();
    private final ConnectionCounter connectionCounter = new ConnectionCounter(server, 5000);

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

    private boolean waitForUiElement(Activity a, @IdRes int id, long timeout) {
        busyWait(() -> a.findViewById(id) != null, 100, timeout);
        a.runOnUiThread(() -> {});
        return a.findViewById(id) != null;
    }

    private void enterPairingCode(PairingActivity a, int code) {
        a.runOnUiThread(() -> {
            EditText pairingCodeText = a.findViewById(R.id.pairing_code_text);
            pairingCodeText.setText(String.valueOf(code));
        });
    }

    private TVReceiverConnection getClientConnectionFromActivity(PairingActivity a) throws IllegalAccessException, NoSuchFieldException {
        // use reflection for this
        Field conectionField = PairingActivity.class.getDeclaredField("connection");
        conectionField.setAccessible(true);
        TVReceiverConnection connection = (TVReceiverConnection) conectionField.get(a);
        assert connection != null;
        return connection;
    }

    private boolean clickButton(Activity a, @IdRes int buttonId) {
        View button = a.findViewById(buttonId);
        if (button == null) return false;

        busyWait(button::hasOnClickListeners, 100, 1000);
        a.runOnUiThread(button::performClick);
        return true;
    }

    @Test
    public void pairing_Test() throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        assert !ActivityManager.isUserAMonkey(); // mandatory check

        server.start();


        PairingActivity a = launchPairingActivity(in, server.getPort());



        // test disconnection

        assertTrue("expecting pairing screen",
                waitForUiElement(a, R.id.pairing_code_text, 5000));
        TVReceiverConnection connection = getClientConnectionFromActivity(a);
        connectionCounter.expectConnection();

        // kill connection from server
        FakeTvConnection serverConnection = server.getConnections().getFirst();
        serverConnection.stop(5000);

        // wait for it to die on client
        busyWait(connection::isDead, 100, 5000);
        assertTrue("connection should be dead after killing it", connection.isDead());

        assertTrue("expecting error screen",
                waitForUiElement(a, R.id.stack_trace_dropdown, 5000));
        connectionCounter.expectDisconnection();

        // todo: verify error text

        assertTrue("expected to click retry button",
                clickButton(a, R.id.positive_button));

        Thread.sleep(300);



        // test wrong code

        assertTrue("expecting pairing screen",
                waitForUiElement(a, R.id.pairing_code_text, 5000));
        connection = getClientConnectionFromActivity(a);
        connectionCounter.expectConnection();

        enterPairingCode(a, FakeTvConnection.TEST_INCORRECT_CODE);

        assertTrue( "expected to click next button",
                clickButton(a, R.id.next_button));

        assertTrue("expecting fingerprint verification screen",
                waitForUiElement(a, R.id.match_button, 5000));

        assertTrue( "expected to click fingerprint match button",
                clickButton(a, R.id.match_button));

        assertTrue("expecting error screen",
                waitForUiElement(a, R.id.stack_trace_dropdown, 5000));

        // todo: error text

        busyWait(connection::isDead, 100, 5000);
        assertTrue("connection should be dead after entering wrong code", connection.isDead());
        connectionCounter.expectDisconnection();

        assertTrue( "expected to click retry button",
                clickButton(a, R.id.positive_button));

        Thread.sleep(300);



        // test no match

        assertTrue("expecting pairing screen",
                waitForUiElement(a, R.id.pairing_code_text, 5000));
        connection = getClientConnectionFromActivity(a);
        connectionCounter.expectConnection();

        enterPairingCode(a, FakeTvConnection.TEST_CODE);

        assertTrue( "expected to click next button",
                clickButton(a, R.id.next_button));

        assertTrue("expecting fingerprint verification screen",
                waitForUiElement(a, R.id.match_button, 5000));

        assertTrue( "expected to click fingerprint no match button",
                clickButton(a, R.id.no_match_button));

        assertTrue("expecting error screen",
                waitForUiElement(a, R.id.stack_trace_dropdown, 5000));

        // todo: error text

        busyWait(connection::isDead, 100, 5000);
        assertTrue("connection should be dead after entering wrong code", connection.isDead());
        connectionCounter.expectDisconnection();

        assertTrue( "expected to click retry button",
                clickButton(a, R.id.positive_button));

        Thread.sleep(300);



        // test correct code

        assertTrue("expecting pairing screen",
                waitForUiElement(a, R.id.pairing_code_text, 5000));
        connection = getClientConnectionFromActivity(a);
        connectionCounter.expectConnection();

        enterPairingCode(a, FakeTvConnection.TEST_CODE);

        assertTrue( "expected to click next button",
                clickButton(a, R.id.next_button));

        assertTrue("expecting fingerprint verification screen",
                waitForUiElement(a, R.id.match_button, 5000));

        assertTrue( "expected to click fingerprint match button",
                clickButton(a, R.id.match_button));

        busyWait(connection::isDead, 100, 5000);
        assertTrue("connection should be dead after pairing completes", connection.isDead());
        connectionCounter.expectDisconnection();



        Thread.sleep(2000);


        server.stop();

    }

    /**
     * simply sits idle on the pairing screen for a few seconds.
     * I've had a few bugs with pings that this would catch.
     */
    @Test
    public void pairingIdle_test() throws NoSuchFieldException, IllegalAccessException, InterruptedException {
        Instrumentation in = InstrumentationRegistry.getInstrumentation();
        server.start();

        // open pairing activity
        PairingActivity a = launchPairingActivity(in, server.getPort());
        assertTrue("expecting pairing screen",
                waitForUiElement(a, R.id.pairing_code_text, 5000));
        TVReceiverConnection connection = getClientConnectionFromActivity(a);
        connectionCounter.expectConnection();

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


}
