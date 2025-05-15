package io.benwiegand.atvremote.phone.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;

import com.google.android.material.navigation.NavigationBarView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.control.InputHandler;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.network.TVReceiverConnectionCallback;
import io.benwiegand.atvremote.phone.protocol.RequiresPairingException;
import io.benwiegand.atvremote.phone.ui.view.RemoteButton;
import io.benwiegand.atvremote.phone.ui.view.TrackpadButton;
import io.benwiegand.atvremote.phone.ui.view.TrackpadSurface;
import io.benwiegand.atvremote.phone.util.ErrorUtil;

public class RemoteActivity extends ConnectingActivity implements TVReceiverConnectionCallback {
    private static final String TAG = RemoteActivity.class.getSimpleName();

    private static final VibrationEffect CLICK_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
    private static final VibrationEffect LONG_CLICK_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);

    private static final int BUTTON_REPEAT_DELAY = 420;

    // connection
    private InputHandler inputHandler = null;

    // ui
    private final List<View> tvControlButtons = new ArrayList<>(/* todo: define size */);
    private AlertDialog errorDialog = null;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);

        // ui setup
        TextView tvNameText = findViewById(R.id.connected_tv_name_text);
        tvNameText.setText(deviceName);

        vibrator = getSystemService(Vibrator.class);
        setupButtons();
    }

    protected void onReady() {
        hideError();

        // connect to tv
        scheduleConnect(0L);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideError();
    }

    private void hideError() {
        runOnUiThread(() -> {
            if (errorDialog == null || !errorDialog.isShowing()) return;
            errorDialog.cancel();
        });
    }

    @Override
    protected void showError(ErrorUtil.ErrorSpec error) {
        runOnUiThread(() -> {
            hideError();

            View view = getLayoutInflater().inflate(R.layout.layout_error, null, false);
            ErrorUtil.inflateErrorScreen(view, error);
            errorDialog = new AlertDialog.Builder(this, R.style.Theme_ATVRemote)
                    .setView(view)
                    .setCancelable(false)
                    .show();
        });
    }

    @Override
    protected TVReceiverConnection connectToTV() throws RequiresPairingException, IOException {
        runOnUiThread(() -> {
            setConnectionStatus(R.string.connection_status_connecting, true);
            setControlsEnabled(false);
        });

        TVReceiverConnection newConnection = connectionService.connectToTV(remoteHostname, remotePort, this);
        inputHandler = newConnection.getInputForwarder();
        return newConnection;
    }

    private void setControlsEnabled(boolean enabled) {
        runOnUiThread(() ->
                tvControlButtons.forEach(b -> b.setEnabled(enabled)));
    }

    private void setConnectionStatus(@StringRes int text, boolean connecting) {
        TextView connectionStatusText = findViewById(R.id.connection_status_text);
        connectionStatusText.setText(text);

        findViewById(R.id.connecting_indicator)
                .setVisibility(connecting ? View.VISIBLE : View.INVISIBLE);
    }

    private void switchToLayout(@LayoutRes int layout) {
        FrameLayout remoteFrame = findViewById(R.id.remote_frame);
        remoteFrame.removeAllViews();
        tvControlButtons.clear();

        getLayoutInflater().inflate(layout, remoteFrame, true);
        setupRemoteButtons();
    }

    private View setupBasicButton(@IdRes int buttonId, Function<InputHandler, Sec<Void>> action) {
        View button = findViewById(buttonId);
        if (button == null) return null;
        tvControlButtons.add(button);
        button.setOnClickListener(v -> {
            if (inputHandler == null) return;
            action.apply(inputHandler)
                    .doOnError(t -> Log.d(TAG, "button action failed", t))
                    .callMeWhenDone();
            vibrator.vibrate(CLICK_VIBRATION_EFFECT);
        });
        return button;
    }

    private void setupBasicButton(@IdRes int buttonId, Function<InputHandler, Sec<Void>> action, Function<InputHandler, Sec<Void>> longPressAction) {
        View button = setupBasicButton(buttonId, action);
        if (button == null) return;
        button.setOnLongClickListener(v -> {
            if (inputHandler == null) return false;
            longPressAction.apply(inputHandler)
                    .doOnError(t -> Log.d(TAG, "button action failed", t))
                    .callMeWhenDone();
            vibrator.vibrate(LONG_CLICK_VIBRATION_EFFECT);
            return true;
        });

    }

    private void setupRepeatableButton(@IdRes int buttonId, Function<InputHandler, Sec<Void>> action, int repeatInterval) {
        RemoteButton button = (RemoteButton) setupBasicButton(buttonId, action);
        if (button == null) return;
        button.setRepeat(BUTTON_REPEAT_DELAY, repeatInterval);
    }

    private void setupTrackpad() {
        // trackpad
        TrackpadSurface trackpadSurface = findViewById(R.id.trackpad_surface);
        if (trackpadSurface == null) return;
        trackpadSurface.setMouseTranslationConsumer((x, y) -> {
            if (inputHandler == null) return null;
            return inputHandler.cursorMove(x, y);
        });

        TrackpadButton trackpadLeftClickButton = findViewById(R.id.trackpad_click_button);
        trackpadLeftClickButton.setOnPress(() -> {
            if (inputHandler == null) return;
            inputHandler.cursorDown();
            vibrator.vibrate(CLICK_VIBRATION_EFFECT);
        });
        trackpadLeftClickButton.setOnRelease(() -> {
            if (inputHandler == null) return;
            inputHandler.cursorUp();
            vibrator.vibrate(LONG_CLICK_VIBRATION_EFFECT);
        });
    }

    private void setupRemoteButtons() {
        // dpad
        setupRepeatableButton(R.id.up_button, InputHandler::dpadUp, 150);
        setupRepeatableButton(R.id.down_button, InputHandler::dpadDown, 150);
        setupRepeatableButton(R.id.left_button, InputHandler::dpadLeft, 150);
        setupRepeatableButton(R.id.right_button, InputHandler::dpadRight, 150);
        setupBasicButton(R.id.select_button, InputHandler::dpadSelect, InputHandler::dpadLongPress);

        // nav bar
        setupBasicButton(R.id.back_button, InputHandler::navBack);
        setupBasicButton(R.id.home_button, InputHandler::navHome);
        setupBasicButton(R.id.recent_button, InputHandler::navRecent);

        setupBasicButton(R.id.notifications_button, InputHandler::navNotifications);
        setupBasicButton(R.id.quick_settings_button, InputHandler::navQuickSettings);

        // volume
        setupRepeatableButton(R.id.volume_up_button, InputHandler::volumeUp, 100);
        setupRepeatableButton(R.id.volume_down_button, InputHandler::volumeDown, 100);
        setupBasicButton(R.id.mute_button, InputHandler::mute);

        setupRepeatableButton(R.id.skip_backward_button, InputHandler::skipBackward, 690);
        setupBasicButton(R.id.prev_track_button, InputHandler::prevTrack);
        setupBasicButton(R.id.pause_button, InputHandler::pause);
        setupBasicButton(R.id.next_track_button, InputHandler::nextTrack);
        setupRepeatableButton(R.id.skip_forward_button, InputHandler::skipForward, 690);

        // trackpad
        setupTrackpad();
    }

    private void setupFixedButtons() {
        View disconnectButton = findViewById(R.id.disconnect_button);
        disconnectButton.setOnClickListener(v -> finish());

        NavigationBarView controlMethodSelector = findViewById(R.id.control_method_selector);
        controlMethodSelector.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.dpad_selector_button)
                switchToLayout(R.layout.layout_remote_standard);
            else if (item.getItemId() == R.id.trackpad_selector_button)
                switchToLayout(R.layout.layout_remote_mouse);
            else return false;
            return true;
        });

    }

    private void setupButtons() {
        setupRemoteButtons();
        setupFixedButtons();
    }

    @Override
    public void onSocketConnected() {
        runOnUiThread(() -> {
            setConnectionStatus(R.string.connection_status_hand_shaking, true);
            setControlsEnabled(false);
        });
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            setControlsEnabled(true);
            setConnectionStatus(R.string.connection_status_ready, false);
        });
    }

    @Override
    public void onDisconnected() {
        scheduleConnect(0L);
        runOnUiThread(() -> {
            setControlsEnabled(false);
            setConnectionStatus(R.string.connection_status_connection_lost, false);
        });
    }
}