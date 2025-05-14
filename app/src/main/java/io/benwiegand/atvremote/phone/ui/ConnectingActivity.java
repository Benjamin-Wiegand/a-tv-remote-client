package io.benwiegand.atvremote.phone.ui;

import static io.benwiegand.atvremote.phone.network.SocketUtil.tryClose;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.util.Timer;
import java.util.TimerTask;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.auth.ssl.CorruptedKeystoreException;
import io.benwiegand.atvremote.phone.network.ConnectionService;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.protocol.RequiresPairingException;
import io.benwiegand.atvremote.phone.util.UiUtil;

public abstract class ConnectingActivity extends AppCompatActivity {
    private static final String TAG = ConnectingActivity.class.getSimpleName();

    private static final long CONNECT_RETRY_DELAY = 1500;

    // intent extras
    public static final String EXTRA_DEVICE_NAME = "name";
    public static final String EXTRA_HOSTNAME = "addr";
    public static final String EXTRA_PORT_NUMBER = "port";

    // threads
    private Timer timer = null;
    private final Handler handler = new Handler(Looper.getMainLooper());

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
        // run this in a handler so onCreate() finishes for child
        handler.post(this::initConnectionService);
    }

    protected abstract void onReady();

    private void initConnectionService() {
        // error actions
        UiUtil.ButtonPreset retryButton = new UiUtil.ButtonPreset(R.string.button_retry, v -> initConnectionService());
        UiUtil.ButtonPreset cancelButton = new UiUtil.ButtonPreset(R.string.button_cancel, v -> finish());
        UiUtil.ButtonPreset deleteKeystoreAndRetry = new UiUtil.ButtonPreset(R.string.button_keystore_delete_and_retry, v -> new AlertDialog.Builder(this)
                .setTitle(R.string.title_confirm)
                .setMessage(R.string.description_confirm_delete_keystore)
                .setPositiveButton(R.string.button_confirm_delete, (d, w) -> {
                    connectionService.getKeystoreManager().deleteKeystore();
                    initConnectionService();
                })
                .setNeutralButton(R.string.button_cancel, null)
                .show());

        connectionService = new ConnectionService(this);
        try {
            connectionService.initializeSSL();
            onReady();
        } catch (IOException e) {
            Log.e(TAG, "failed to load keystore", e);
            showError(R.string.init_failure, R.string.init_failure_desc_general, e,
                    retryButton, cancelButton, deleteKeystoreAndRetry);
        } catch (KeyManagementException | CorruptedKeystoreException e) {
            Log.e(TAG, "keystore is corrupted", e);
            showError(R.string.init_failure, R.string.init_failure_desc_corrupted_keystore, e,
                    deleteKeystoreAndRetry, cancelButton, retryButton);
        } catch (UnsupportedOperationException e) {
            Log.e(TAG, "device unsupported?", e);
            showError(R.string.init_failure, R.string.init_failure_desc_unsupported, e,
                    retryButton, cancelButton, null);
        } catch (RuntimeException e) {
            Log.e(TAG, "unexpected error", e);
            showError(R.string.init_failure, R.string.init_failure_desc_unexpected_error, e,
                    retryButton, cancelButton, null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        timer.cancel();
        if (connection != null) tryClose(connection);
    }

    protected abstract void showError(
            @StringRes int title, Throwable t,
            UiUtil.ButtonPreset positiveAction,
            UiUtil.ButtonPreset neutralAction,
            UiUtil.ButtonPreset negativeAction);

    protected abstract void showError(
            @StringRes int title, @StringRes int description, Throwable t,
            UiUtil.ButtonPreset positiveAction,
            UiUtil.ButtonPreset neutralAction,
            UiUtil.ButtonPreset negativeAction);

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
            showError(R.string.negotiation_failure, R.string.negotiation_failure_desc_unexpected_error, e,
                    new UiUtil.ButtonPreset(R.string.button_retry, v -> initConnectionService()),
                    new UiUtil.ButtonPreset(R.string.button_cancel, v -> finish()),
                    null);
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
