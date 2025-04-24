package io.benwiegand.atvremote.phone.ui;

import static io.benwiegand.atvremote.phone.network.SocketUtil.tryClose;
import static io.benwiegand.atvremote.phone.util.ErrorUtil.generateErrorDescription;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.text.MessageFormat;
import java.util.Timer;
import java.util.TimerTask;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.auth.ssl.CorruptedKeystoreException;
import io.benwiegand.atvremote.phone.network.ConnectionService;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.protocol.RequiresPairingException;

public abstract class ConnectingActivity extends AppCompatActivity {
    private static final String TAG = ConnectingActivity.class.getSimpleName();

    private static final long CONNECT_RETRY_DELAY = 1500;

    // intent extras
    public static final String EXTRA_DEVICE_NAME = "name";
    public static final String EXTRA_HOSTNAME = "addr";
    public static final String EXTRA_PORT_NUMBER = "port";

    // threads
    private Timer timer = null;

    // connection
    protected ConnectionService connectionService;
    protected TVReceiverConnection connection = null;
    protected String deviceName;
    protected String remoteHostname;
    protected int remotePort;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // parse extras
        deviceName = getIntent().getStringExtra(EXTRA_DEVICE_NAME);
        remoteHostname = getIntent().getStringExtra(EXTRA_HOSTNAME);
        remotePort = getIntent().getIntExtra(EXTRA_PORT_NUMBER, -1);

        if (remoteHostname == null || remotePort < 0 || remotePort > 65535) {
            Log.e(TAG, "PairingActivity not launched with required intent extras");
            setResult(-1);
            finish();
            return;
        }
        if (deviceName == null) deviceName = remoteHostname;

        // start connection timer
        timer = new Timer();

        // connection
        connectionService = new ConnectionService(this);
        try {
            connectionService.initializeSSL();

        } catch (IOException e) {
            Log.e(TAG, "failed to load keystore", e);
            showError(R.string.init_failure, MessageFormat.format(getString(R.string.init_failure_desc_general), generateErrorDescription(e)));
            // todo: also allow delete/retry, the keystore might be corrupted
        } catch (KeyManagementException | CorruptedKeystoreException e) {
            Log.e(TAG, "keystore is corrupted", e);
            // todo: delete/retry
            showError(R.string.init_failure, MessageFormat.format(getString(R.string.init_failure_desc_corrupted_keystore), generateErrorDescription(e)));
        } catch (UnsupportedOperationException e) {
            Log.e(TAG, "device unsupported?", e);
            showError(R.string.init_failure, MessageFormat.format(getString(R.string.init_failure_desc_unsupported), generateErrorDescription(e)));
        } catch (RuntimeException e) {
            Log.e(TAG, "unexpected error", e);
            showError(R.string.init_failure, MessageFormat.format(getString(R.string.init_failure_desc_unexpected_error), generateErrorDescription(e)));
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        timer.cancel();
        if (connection != null) tryClose(connection);
    }

    protected abstract void showError(@StringRes int title, String description);

    protected void scheduleConnect(long delay) {
        try {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    initConnection();
                }
            }, delay);
        } catch (IllegalStateException e) {
            Log.w(TAG, "task not scheduled, timer is dead");
        }
    }

    private void initConnection() {
        if (connection != null) tryClose(connection);

        try {
            connection = connectToTV();
        } catch (UnknownHostException e) {
            // todo: somehow inform the user without popup
            Log.e(TAG, "Unknown host", e);
            scheduleConnect(CONNECT_RETRY_DELAY);
        } catch (IOException e) {
            Log.e(TAG, "got IOException while connecting to TV", e);
            scheduleConnect(CONNECT_RETRY_DELAY);
        } catch (InterruptedException e) {
            Log.d(TAG, "interrupted");
            finish();   // assume termination
        } catch (RuntimeException e) {
            Log.e(TAG, "unexpected error", e);
            showError(R.string.negotiation_failure, MessageFormat.format(getString(R.string.negotiation_failure_desc_unexpected_error), generateErrorDescription(e)));
        } catch (RequiresPairingException e) {
            Log.i(TAG, "not paired, starting pairing: " + e.getMessage());
            Intent intent = new Intent(this, PairingActivity.class)
                    .putExtra(EXTRA_DEVICE_NAME, deviceName)
                    .putExtra(EXTRA_HOSTNAME, remoteHostname)
                    .putExtra(EXTRA_PORT_NUMBER, remotePort);
            startActivity(intent);
            finish();
        }
    }

    protected abstract TVReceiverConnection connectToTV() throws RequiresPairingException, IOException, InterruptedException;

}
