package io.benwiegand.atvremote.phone.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.security.KeyManagementException;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.auth.ssl.CorruptedKeystoreException;
import io.benwiegand.atvremote.phone.auth.ssl.KeystoreManager;
import io.benwiegand.atvremote.phone.network.ConnectionService;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.util.ErrorUtil;
import io.benwiegand.atvremote.phone.util.UiUtil;

public abstract class ConnectingActivity extends DynamicColorsCompatActivity implements ConnectionService.Callback {
    private static final String TAG = ConnectingActivity.class.getSimpleName();

    // intent extras
    public static final String EXTRA_DEVICE_NAME = "name";
    public static final String EXTRA_HOSTNAME = "addr";
    public static final String EXTRA_PORT_NUMBER = "port";

    // connection
    private Intent serviceIntent;
    private final ConnectionServiceConnection connectionServiceConnection = new ConnectionServiceConnection();
    protected ConnectionService.ConnectionServiceBinder binder = null;
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

        serviceIntent = new Intent(this, ConnectionService.class);
        startService(serviceIntent);
        boolean bindResult = bindService(serviceIntent, connectionServiceConnection, BIND_IMPORTANT | BIND_AUTO_CREATE);
        assert bindResult;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binder != null)
            binder.foreground();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (binder != null)
            binder.background();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        if (binder != null)
            binder.unregister(this, !isChangingConfigurations());

        if (!isChangingConfigurations())
            stopService(serviceIntent);

        unbindService(connectionServiceConnection);
    }

    private class ConnectionServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "connection service connected");
            binder = (ConnectionService.ConnectionServiceBinder) service;
            binder.register(ConnectingActivity.this);
            binder.init();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e(TAG, "connection service died");
            if (isFinishing() || isDestroyed()) return; // and we have (likely) killed it

            onServiceDeath(false);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            ServiceConnection.super.onBindingDied(name);
            Log.e(TAG, "connection service binding was murdered");
            onServiceDeath(true);
        }
    }

    protected abstract void showError(ErrorUtil.ErrorSpec error);

    @Override
    public abstract void onServiceInit();

    @Override
    public void onServiceInitError(Throwable t, boolean possiblyKeystoreInit) {
        UiUtil.ButtonPreset retryButton = new UiUtil.ButtonPreset(R.string.button_retry, v -> binder.init());
        UiUtil.ButtonPreset cancelButton = new UiUtil.ButtonPreset(R.string.button_cancel, v -> finish());
        UiUtil.ButtonPreset deleteKeystoreAndRetry = new UiUtil.ButtonPreset(R.string.button_keystore_delete_and_retry, v -> new AlertDialog.Builder(this)
                .setTitle(R.string.title_confirm)
                .setMessage(R.string.description_confirm_delete_keystore)
                .setPositiveButton(R.string.button_confirm_delete, (d, w) -> {
                    KeystoreManager.deleteKeystore(getApplicationContext());
                    binder.init();
                })
                .setNeutralButton(R.string.button_cancel, null)
                .show());

        if (t instanceof IOException) {
            Log.e(TAG, "failed to load keystore", t);
            showError(new ErrorUtil.ErrorSpec(
                    R.string.init_failure, R.string.init_failure_desc_general, t,
                    retryButton, cancelButton, deleteKeystoreAndRetry));
        } else if (t instanceof KeyManagementException || t instanceof CorruptedKeystoreException) {
            Log.e(TAG, "keystore is corrupted", t);
            showError(new ErrorUtil.ErrorSpec(
                    R.string.init_failure, R.string.init_failure_desc_corrupted_keystore, t,
                    deleteKeystoreAndRetry, cancelButton, retryButton));
        } else if (t instanceof UnsupportedOperationException) {
            Log.e(TAG, "device unsupported?", t);
            showError(new ErrorUtil.ErrorSpec(
                    R.string.init_failure, R.string.init_failure_desc_unsupported, t,
                    retryButton, cancelButton, null));
        } else if (t instanceof ErrorMessageException) {
            showError(new ErrorUtil.ErrorSpec(
                    R.string.init_failure, t,
                    retryButton, cancelButton, null));
        } else {
            Log.e(TAG, "unexpected error", t);
            showError(new ErrorUtil.ErrorSpec(
                    R.string.init_failure, R.string.init_failure_desc_unexpected_error, t,
                    retryButton, cancelButton, null));
        }
    }

    @Override
    public abstract void onSocketConnected();

    @Override
    public abstract void onConnected(TVReceiverConnection connection);

    @Override
    public abstract void onConnectError(Throwable t);

    protected ErrorUtil.ErrorSpec connectExceptionErrorMessage(Throwable t, UiUtil.ButtonPreset retryButton, UiUtil.ButtonPreset cancelButton) {
        if (t instanceof ErrorMessageException) {
            return new ErrorUtil.ErrorSpec(
                    R.string.connect_failure, t,
                    retryButton, cancelButton, null);
        } else {
            return new ErrorUtil.ErrorSpec(
                    R.string.connect_failure, R.string.connect_failure_desc_unexpected_error, t,
                    retryButton, cancelButton, null);
        }
    }

    @Override
    public abstract void onDisconnected(Throwable t);

    /**
     * called when the service dies (can be overridden by children)
     * @param murder if killed intentionally or via external factors
     */
    public void onServiceDeath(boolean murder) {
        if (murder) {   // silently exit
            finish();
            return;
        }

        // todo: try to gather an exception for this if possible
        showError(new ErrorUtil.ErrorSpec(
                R.string.title_application_error, R.string.description_application_error_connection_service_stopped, null,
                new UiUtil.ButtonPreset(R.string.button_close, v -> finish()),
                null, null
        ));
    }

}
