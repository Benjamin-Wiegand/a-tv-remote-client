package io.benwiegand.atvremote.phone.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.navigation.NavigationBarView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.control.InputHandler;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.protocol.RequiresPairingException;
import io.benwiegand.atvremote.phone.ui.view.RemoteButton;
import io.benwiegand.atvremote.phone.ui.view.TrackpadButton;
import io.benwiegand.atvremote.phone.ui.view.TrackpadSurface;
import io.benwiegand.atvremote.phone.util.ErrorUtil;
import io.benwiegand.atvremote.phone.util.UiUtil;

public class RemoteActivity extends ConnectingActivity {
    private static final String TAG = RemoteActivity.class.getSimpleName();

    private static final long CONNECT_RETRY_DELAY = 1500;

    private static final VibrationEffect CLICK_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
    private static final VibrationEffect LONG_CLICK_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);
    private static final VibrationEffect ATTENTION_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK);

    private static final int BUTTON_REPEAT_DELAY = 420;

    private static final String KEY_STATE_SELECTED_LAYOUT = "selected_layout";
    private static final int DEFAULT_LAYOUT = R.id.dpad_selector_button;

    private record LayoutOrientationSelector(@LayoutRes int portraitLayout, @LayoutRes int landscapeLayout) {
        LayoutOrientationSelector(@LayoutRes int layout) {
            this(layout, layout);
        }
    }
    private static final Map<Integer, LayoutOrientationSelector> LAYOUT_MAP = Map.of(
            R.id.dpad_selector_button,      new LayoutOrientationSelector(R.layout.layout_remote_standard, R.layout.layout_remote_standard_landscape),
            R.id.trackpad_selector_button,  new LayoutOrientationSelector(R.layout.layout_remote_mouse, R.layout.layout_remote_mouse_landscape),
            R.id.media_selector_button,     new LayoutOrientationSelector(R.layout.layout_remote_media)
    );

    // connection
    private InputHandler inputHandler = null;

    // ui
    private final List<View> tvControlButtons = new ArrayList<>(/* todo: define size */);
    private AlertDialog errorDialog = null;
    private Toast toast = null;
    private Vibrator vibrator;
    @IdRes private int selectedLayout = DEFAULT_LAYOUT;
    private boolean controlsEnabled = false;

    // events
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        setTheme(R.style.Theme_ATVRemote_Remote);
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        setContentView(R.layout.activity_remote);

        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(statusBars.left, 0, statusBars.right, 0);
            v.findViewById(R.id.action_bar).setPadding(0, statusBars.top, 0, 0);

            return insets;
        });


        // ui setup
        TextView tvNameText = findViewById(R.id.connected_tv_name_text);
        tvNameText.setText(deviceName);

        vibrator = getSystemService(Vibrator.class);

        // restore state
        if (savedInstanceState != null) {
            selectedLayout = savedInstanceState.getInt(KEY_STATE_SELECTED_LAYOUT, DEFAULT_LAYOUT);
        }

        // ensure navbar matches selected value
        NavigationBarView controlMethodSelector = findViewById(R.id.control_method_selector);
        controlMethodSelector.setSelectedItemId(selectedLayout);

        setupFixedButtons();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.d(TAG, "focus changed");
        // layout is different depending on aspect ratio
        // the aspect ratio is taken from the view showing said layout, not the device orientation
        // this can't be done in onCreate because the view hasn't drawn
        setupLayout();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_STATE_SELECTED_LAYOUT, selectedLayout);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideError();
    }

    private void scheduleReconnect() {
        handler.postDelayed(this::connect, CONNECT_RETRY_DELAY);
    }

    private void connect() {
        runOnUiThread(() -> {
            setConnectionStatus(R.string.connection_status_connecting, true);
            setControlsEnabled(false);
        });

        binder.connect(deviceName, remoteHostname, remotePort, false);
    }

    @Override
    public void onServiceInit() {
        connect();
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

            errorDialog = ErrorUtil.inflateErrorScreenAsDialog(this, error);
            errorDialog.show();
        });
    }

    @Override
    public void onSocketConnected() {
        runOnUiThread(() -> {
            setConnectionStatus(R.string.connection_status_hand_shaking, true);
            setControlsEnabled(false);
        });
    }

    @Override
    public void onConnected(TVReceiverConnection connection) {
        inputHandler = connection.getInputForwarder();

        runOnUiThread(() -> {
            setControlsEnabled(true);
            setConnectionStatus(R.string.connection_status_ready, false);
        });
    }

    @Override
    public void onConnectError(Throwable t) {
        if (t instanceof IOException) {
            toast(ErrorUtil.getExceptionLine(this, t));
            scheduleReconnect();
        } else if (t instanceof RequiresPairingException) {
            Log.i(TAG, "not paired, starting pairing: " + t.getMessage());
            Intent intent = new Intent(this, PairingActivity.class)
                    .putExtra(EXTRA_DEVICE_NAME, deviceName)
                    .putExtra(EXTRA_HOSTNAME, remoteHostname)
                    .putExtra(EXTRA_PORT_NUMBER, remotePort);
            startActivity(intent);
            finish();
        } else {
            ErrorUtil.ErrorSpec error = connectExceptionErrorMessage(t,
                    new UiUtil.ButtonPreset(R.string.button_retry, v -> connect()),
                    new UiUtil.ButtonPreset(R.string.button_cancel, v -> finish()));
            showError(error);
        }
    }

    @Override
    public void onDisconnected(Throwable t) {
        runOnUiThread(() -> {
            setControlsEnabled(false);
            setConnectionStatus(R.string.connection_status_connection_lost, false);
        });

        if (isFinishing() || isDestroyed()) return;
        if (t != null) toast(ErrorUtil.getExceptionLine(this, t));
        connect();
    }


    // ui stuff

    private void toast(String message) {
        // todo: replace this with a "soft" error that doesn't have the limitations of toasts
        runOnUiThread(() -> {
            if (toast != null) toast.cancel();
            toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
            toast.show();
        });
    }

    private void updateControlsEnabled() {
        runOnUiThread(() ->
                tvControlButtons.forEach(b -> b.setEnabled(controlsEnabled)));
    }

    private void setControlsEnabled(boolean enabled) {
        controlsEnabled = enabled;
        updateControlsEnabled();
    }

    private void setConnectionStatus(@StringRes int text, boolean connecting) {
        TextView connectionStatusText = findViewById(R.id.connection_status_text);
        connectionStatusText.setText(text);

        findViewById(R.id.connecting_indicator)
                .setVisibility(connecting ? View.VISIBLE : View.GONE);
    }

    private void switchToLayout(@LayoutRes int layout) {
        FrameLayout remoteFrame = findViewById(R.id.remote_frame);
        remoteFrame.removeAllViews();
        tvControlButtons.clear();

        getLayoutInflater().inflate(layout, remoteFrame, true);
    }

    private void handleActionError(Throwable t) {
        Log.d(TAG, "action failed", t);
        if (t instanceof ErrorMessageException e) {
            vibrator.vibrate(ATTENTION_VIBRATION_EFFECT);
            showError(new ErrorUtil.ErrorSpec(
                    R.string.action_failure, e,
                    new UiUtil.ButtonPreset(R.string.button_ok, null),
                    null, null));
        }

    }

    private void setupBasicButton(View button, Function<InputHandler, Sec<Void>> action, Function<InputHandler, Sec<Void>> longPressAction) {
        if (button == null) return;
        tvControlButtons.add(button);

        button.setOnClickListener(v -> {
            if (inputHandler == null) return;
            action.apply(inputHandler)
                    .doOnError(this::handleActionError)
                    .callMeWhenDone();
            vibrator.vibrate(CLICK_VIBRATION_EFFECT);
        });

        if (longPressAction == null) return;

        button.setOnLongClickListener(v -> {
            if (inputHandler == null) return false;
            longPressAction.apply(inputHandler)
                    .doOnError(this::handleActionError)
                    .callMeWhenDone();
            vibrator.vibrate(LONG_CLICK_VIBRATION_EFFECT);
            return true;
        });
    }

    private void setupBasicButton(@IdRes int buttonId, Function<InputHandler, Sec<Void>> action, Function<InputHandler, Sec<Void>> longPressAction) {
        setupBasicButton(findViewById(buttonId), action, longPressAction);
    }

    private void setupBasicButton(View button, Function<InputHandler, Sec<Void>> action) {
        setupBasicButton(button, action, null);
    }

    private void setupBasicButton(@IdRes int buttonId, Function<InputHandler, Sec<Void>> action) {
        setupBasicButton(buttonId, action, null);
    }

    private void setupRepeatableButton(RemoteButton button, Function<InputHandler, Sec<Void>> action, int repeatInterval) {
        if (button == null) return;
        setupBasicButton(button, action);
        button.setRepeat(BUTTON_REPEAT_DELAY, repeatInterval);
    }

    private void setupRepeatableButton(@IdRes int buttonId, Function<InputHandler, Sec<Void>> action, int repeatInterval) {
        setupRepeatableButton(findViewById(buttonId), action, repeatInterval);
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

    private void setupVolumeAdjustButton() {
        View volumeAdjustButton = findViewById(R.id.volume_adjust_button);
        if (volumeAdjustButton == null) return;
        tvControlButtons.add(volumeAdjustButton);

        volumeAdjustButton.setOnClickListener(v -> {
            View view = getLayoutInflater().inflate(R.layout.layout_remote_dialog_volume_adjustment, null, false);

            setupRepeatableButton(view.findViewById(R.id.volume_up_button), InputHandler::volumeUp, 100);
            setupRepeatableButton(view.findViewById(R.id.volume_down_button), InputHandler::volumeDown, 100);
            setupBasicButton(view.findViewById(R.id.mute_button), InputHandler::mute);

            new AlertDialog.Builder(this)
                    .setTitle(R.string.title_volume_dialog)
                    .setView(view)
                    .setPositiveButton(R.string.button_close, null)
                    .show();
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

//        setupBasicButton(R.id.notifications_button, InputHandler::navNotifications);
//        setupBasicButton(R.id.quick_settings_button, InputHandler::navQuickSettings);

        // volume
        setupVolumeAdjustButton();

        // media
        setupRepeatableButton(R.id.skip_backward_button, InputHandler::skipBackward, 690);
        setupBasicButton(R.id.prev_track_button, InputHandler::prevTrack);
        setupBasicButton(R.id.pause_button, InputHandler::pause);
        setupBasicButton(R.id.next_track_button, InputHandler::nextTrack);
        setupRepeatableButton(R.id.skip_forward_button, InputHandler::skipForward, 690);

        // trackpad
        setupTrackpad();

        updateControlsEnabled();
    }

    private void setLayout(@IdRes int selector) {
        if (!LAYOUT_MAP.containsKey(selector)) throw new IllegalArgumentException("Layout selector has no mapping defined!");
        selectedLayout = selector;
        setupLayout();
    }

    private void setupLayout() {
        LayoutOrientationSelector selector = LAYOUT_MAP.get(selectedLayout);
        if (selector == null) throw new IllegalStateException("selected layout does not exist in layout map!");

        View view = findViewById(R.id.remote_frame);
        boolean portrait = view.getHeight() > view.getWidth();
        int layout = portrait ? selector.portraitLayout() : selector.landscapeLayout();

        switchToLayout(layout);
        setupRemoteButtons();
    }

    private void setupFixedButtons() {
        View disconnectButton = findViewById(R.id.disconnect_button);
        disconnectButton.setOnClickListener(v -> finish());

        NavigationBarView controlMethodSelector = findViewById(R.id.control_method_selector);
        controlMethodSelector.setOnItemSelectedListener(item -> {
            try {
                setLayout(item.getItemId());
            } catch (IllegalArgumentException e) {
                Log.wtf(TAG, "failed to set layout", e);
                return false;
            }
            return true;
        });

    }

}