package io.benwiegand.atvremote.phone.ui;

import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.security.cert.Certificate;
import java.text.MessageFormat;
import java.time.Instant;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.auth.ssl.CorruptedKeystoreException;
import io.benwiegand.atvremote.phone.auth.ssl.KeyUtil;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.protocol.PairingData;
import io.benwiegand.atvremote.phone.protocol.PairingManager;
import io.benwiegand.atvremote.phone.util.ByteUtil;
import io.benwiegand.atvremote.phone.util.ErrorUtil;
import io.benwiegand.atvremote.phone.util.UiUtil;

public class PairingActivity extends ConnectingActivity {
    private static final String TAG = PairingActivity.class.getSimpleName();

    // global error actions
    private final UiUtil.ButtonPreset RETRY_PAIRING_ACTION = new UiUtil.ButtonPreset(R.string.button_retry, v -> connect());
    private final UiUtil.ButtonPreset CANCEL_PAIRING_ACTION = new UiUtil.ButtonPreset(R.string.button_cancel, v -> finish());

    // ui
    private View layoutView = null;
    private boolean ignoreDisconnection = false;

    // pairing secrets
    private Certificate certificate = null;
    private byte[] fingerprint = null;
    private String pairingCode = null;

    // connection
    private TVReceiverConnection connection = null;

    // misc
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PairingManager pairingManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pairing);

        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

    }

    private void connect() {
        showLoadingScreen(R.string.title_pairing_connecting, MessageFormat.format(getString(R.string.description_pairing_connecting), deviceName));
        binder.connect(deviceName, remoteHostname, remotePort, true);
    }

    @Override
    public void onServiceInit() {
        pairingManager = binder.getPairingManager();
        connect();
    }

    @Override
    public void onSocketConnected() {
        showLoadingScreen(R.string.title_pairing_hand_shaking, MessageFormat.format(getString(R.string.description_pairing_hand_shaking), deviceName));
    }

    @Override
    public void onConnected(TVReceiverConnection connection) {
        try {
            certificate = connection.getCertificate();
            if (certificate == null) throw new IOException("TV did not send an SSL certificate");
            fingerprint = KeyUtil.calculateCertificateFingerprint(certificate);
            this.connection = connection;
        } catch (CorruptedKeystoreException e) {
            certificate = null;
            fingerprint = null;
            binder.disconnect();
            showError(new ErrorUtil.ErrorSpec(
                    R.string.connect_failure, R.string.connect_failure_desc_bad_certificate, new IOException("TV sent a bad SSL certificate", e),
                    RETRY_PAIRING_ACTION, CANCEL_PAIRING_ACTION, null));
            return;
        } catch (Throwable t) {
            certificate = null;
            fingerprint = null;
            binder.disconnect();
            showError(new ErrorUtil.ErrorSpec(
                    R.string.connect_failure, R.string.connect_failure_desc_unexpected_error, t,
                    RETRY_PAIRING_ACTION, CANCEL_PAIRING_ACTION, null));
            return;
        }

        showPairingCodeScreen();
    }

    @Override
    public void onConnectError(Throwable t) {
        showError(connectExceptionErrorMessage(t, RETRY_PAIRING_ACTION, CANCEL_PAIRING_ACTION));
    }

    @Override
    public void onDisconnected(Throwable t) {
        if (ignoreDisconnection) return;
        showError(new ErrorUtil.ErrorSpec(
                R.string.title_pairing_error, R.string.description_pairing_error_connection_lost, t,
                RETRY_PAIRING_ACTION, CANCEL_PAIRING_ACTION, null));
    }

    private void sendPairingCode() {
        showLoadingScreen(R.string.title_pairing_authenticating, MessageFormat.format(getString(R.string.description_pairing_authenticating), deviceName));
        connection.sendPairingCode(pairingCode)
                .doOnError(t -> {
                    Log.e(TAG, "failed to get token", t);
                    binder.disconnect();
                    showError(new ErrorUtil.ErrorSpec(
                            R.string.title_pairing_error, t,
                            RETRY_PAIRING_ACTION, CANCEL_PAIRING_ACTION, null));
                })
                .doOnResult(token -> {
                    Log.d(TAG, "received token");
                    ignoreDisconnection = true;
                    try {
                        binder.disconnect();

                        Log.v(TAG, "committing pairing data");
                        boolean committed = pairingManager.addNewDevice(certificate, new PairingData(token, fingerprint, deviceName, remoteHostname, Instant.now().getEpochSecond()));
                        if (!committed) throw new RuntimeException("failed to commit pairing data");

                        Log.v(TAG, "refreshing certificates");
                        binder.refreshCertificates();

                        Log.i(TAG, "pairing successful");
                        launchRemote();

                        ignoreDisconnection = false;
                    } catch (Throwable t) {
                        // todo: might not be localized
                        showError(new ErrorUtil.ErrorSpec(
                                R.string.title_pairing_error, t,
                                RETRY_PAIRING_ACTION, CANCEL_PAIRING_ACTION, null));
                    }
                })
                .callMeWhenDone();
    }

    private void launchRemote() {
        runOnUiThread(() -> {
            Intent intent = new Intent(this, RemoteActivity.class)
                    .putExtra(EXTRA_DEVICE_NAME, deviceName)
                    .putExtra(EXTRA_HOSTNAME, remoteHostname)
                    .putExtra(EXTRA_PORT_NUMBER, remotePort);
            startActivity(intent);
            finish();
        });
    }

    private void showLoadingScreen(@StringRes int title, String description) {
        runOnUiThread(() -> {
            View layout = switchLayout(R.layout.layout_pairing_loading);

            TextView titleText = layout.findViewById(R.id.title_text);
            titleText.setText(title);

            TextView descriptionText = layout.findViewById(R.id.description_text);
            descriptionText.setText(description);

            layout.findViewById(R.id.cancel_button)
                    .setOnClickListener(v -> finish());
        });
    }

    private void showPairingCodeScreen() {
        runOnUiThread(() -> {
            View layout = switchLayout(R.layout.layout_pairing_code);
            EditText pairingCodeText = layout.findViewById(R.id.pairing_code_text);
            Button nextButton = layout.findViewById(R.id.next_button);

            layout.findViewById(R.id.cancel_button)
                    .setOnClickListener(v -> finish());

            pairingCodeText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == IME_ACTION_DONE) return nextButton.performClick();
                return false;
            });

            nextButton.setOnClickListener(v -> {
                pairingCode = pairingCodeText.getText().toString();
                if (pairingCode.length() != 6) {
                    pairingCodeText.setError(getString(R.string.error_pairing_code_length));
                    return;
                }

                showFingerprintScreen();
            });

        });
    }

    private void showFingerprintScreen() {
        runOnUiThread(() -> {
            View layout = switchLayout(R.layout.layout_pairing_fingerprint);

            TextView elevatedText = layout.findViewById(R.id.fingerprint_elevated_text);
            elevatedText.setText("70 D0"); // todo

            TextView fullText = layout.findViewById(R.id.fingerprint_full_text);
            fullText.setText(ByteUtil.hexOf(fingerprint, " ", false));

            Button matchButton = layout.findViewById(R.id.match_button);
            matchButton.setOnClickListener(v -> sendPairingCode());

            Button noMatchButton = layout.findViewById(R.id.no_match_button);
            noMatchButton.setOnClickListener(v -> {
                binder.disconnect();
                showError(new ErrorUtil.ErrorSpec(
                        R.string.title_pairing_error, R.string.description_pairing_error_fingerprint_differs, null,
                        RETRY_PAIRING_ACTION, CANCEL_PAIRING_ACTION, null));
            });

            // don't immediately enable buttons to prevent user from skipping through this part.
            // they probably will anyway, but I tried.
            handler.postDelayed(() -> {
                matchButton.setEnabled(true);
                noMatchButton.setEnabled(true);
            }, 5000);

            layout.findViewById(R.id.back_button)
                    .setOnClickListener(v -> showPairingCodeScreen());
        });
    }

    @Override
    protected void showError(ErrorUtil.ErrorSpec error) {
        // the disconnection is likely a result of the current error
        ignoreDisconnection = true;
        runOnUiThread(() -> {
            View layout = switchLayout(R.layout.layout_error);
            ErrorUtil.inflateErrorScreen(layout, error);
        });
    }

    private View switchLayout(@LayoutRes int layout) {
        // discount fragments. I know fragments exist, but I'm lazy and this feels easier
        ViewGroup root = findViewById(R.id.root);
        View oldLayout = layoutView;
        layoutView = getLayoutInflater().inflate(layout, root, false);
        root.addView(layoutView);

        if (oldLayout != null) oldLayout.animate()
                .setInterpolator(UiUtil.EASE_IN)
                .translationX(-root.getWidth())
                .withEndAction(() -> root.removeView(oldLayout))
                .start();

        layoutView.setTranslationX(root.getWidth());
        layoutView.animate()
                .setInterpolator(UiUtil.EASE_OUT)
                .translationX(0)
                .start();

        ignoreDisconnection = layout == R.layout.layout_error;

        return layoutView;
    }
}