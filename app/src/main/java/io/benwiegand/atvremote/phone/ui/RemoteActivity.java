package io.benwiegand.atvremote.phone.ui;

import static android.view.inputmethod.EditorInfo.IME_ACTION_SEND;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
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

import com.google.android.material.color.DynamicColors;
import com.google.android.material.navigation.NavigationBarView;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.control.InputHandler;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.protocol.KeyEventType;
import io.benwiegand.atvremote.phone.protocol.RequiresPairingException;
import io.benwiegand.atvremote.phone.protocol.json.MediaMetaEvent;
import io.benwiegand.atvremote.phone.protocol.json.MediaPositionEvent;
import io.benwiegand.atvremote.phone.protocol.json.MediaStateEvent;
import io.benwiegand.atvremote.phone.protocol.json.ReceiverCapabilities;
import io.benwiegand.atvremote.phone.state.MediaSessionTracker;
import io.benwiegand.atvremote.phone.ui.view.RemoteButton;
import io.benwiegand.atvremote.phone.ui.view.RemoteImageButton;
import io.benwiegand.atvremote.phone.ui.view.TrackpadSurface;
import io.benwiegand.atvremote.phone.util.ErrorUtil;
import io.benwiegand.atvremote.phone.util.UiUtil;

public class RemoteActivity extends ConnectingActivity {
    private static final String TAG = RemoteActivity.class.getSimpleName();

    private static final long CONNECT_RETRY_DELAY = 1500;

    private static final int SEEK_BAR_MAX = 10000; // most displays are not 10k pixels wide, so this should be more than precise enough

    private static final VibrationEffect ATTENTION_VIBRATION_EFFECT = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK);

    private static final int BUTTON_REPEAT_DELAY = 420;
    private static final int VOLUME_BUTTON_REPEAT_INTERVAL = 100;
    private static final int DPAD_BUTTON_REPEAT_INTERVAL = 50;
    private static final int SKIP_BUTTON_REPEAT_INTERVAL = 150;
    private static final int TRACK_BUTTON_REPEAT_INTERVAL = 150;
    private static final int KEYBOARD_EXTRA_BUTTON_REPEAT_INTERVAL = 50;

    private static final String KEY_STATE_SELECTED_LAYOUT = "selected_layout";
    private static final int DEFAULT_LAYOUT = R.id.dpad_selector_button;

    // used to remove stuff to make the ui fit on small screens
    private static final float DPAD_PADDING_HEIGHT_DP = 24;
    private static final float MEDIA_SUMMARY_MIN_HEIGHT_DP = 48;
    private static final float MEDIA_CONTROLS_MIN_HEIGHT_DP = 64;

    // if this can't be met, use portrait layout.
    private static final float LAYOUT_REMOTE_STANDARD_LANDSCAPE_MIN_WIDTH_DP = 192 /* dpad */ + 192 /* controls section */;

    // if this can't be met, remove ui elements.
    private static final float LAYOUT_REMOTE_STANDARD_PORTRAIT_MIN_HEIGHT_DP = 192 /* dpad */
            + 64 /* navbar */
            + DPAD_PADDING_HEIGHT_DP
            + MEDIA_SUMMARY_MIN_HEIGHT_DP
            + MEDIA_CONTROLS_MIN_HEIGHT_DP;

    private record LayoutOrientationSelector(@LayoutRes int portraitLayout, @LayoutRes int landscapeLayout) {
        LayoutOrientationSelector(@LayoutRes int layout) {
            this(layout, layout);
        }
    }
    private static final Map<Integer, LayoutOrientationSelector> LAYOUT_MAP = Map.of(
            R.id.dpad_selector_button,      new LayoutOrientationSelector(R.layout.layout_remote_standard, R.layout.layout_remote_standard_landscape),
            R.id.trackpad_selector_button,  new LayoutOrientationSelector(R.layout.layout_remote_mouse, R.layout.layout_remote_mouse_landscape),
            R.id.media_selector_button,     new LayoutOrientationSelector(R.layout.layout_remote_media),
            R.id.keyboard_selector_button,  new LayoutOrientationSelector(R.layout.layout_remote_keyboard)
    );

    // connection
    private InputHandler inputHandler = null;
    private ReceiverCapabilities capabilities = null;
    private MediaSessionTracker mediaSessionTracker = null;

    // ui
    private View errorView = null;
    private Toast toast = null;
    private Vibrator vibrator;
    @IdRes private int selectedLayout = DEFAULT_LAYOUT;

    // events
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        // applying themes using "@color/material_dynamic_*" will cause a crash if api level doesn't support it
        setTheme(DynamicColors.isDynamicColorAvailable() ?
                R.style.Theme_ATVRemote_Remote_DynamicColors :
                R.style.Theme_ATVRemote_Remote);
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        // use frame to allow an error screen to be inflated if needed
        setContentView(R.layout.layout_frame);
        FrameLayout frame = findViewById(R.id.frame);
        View remoteView = getLayoutInflater().inflate(R.layout.activity_remote, frame, true);

        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.frame), (v, insets) -> {
            Insets avoidZone = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());

            // the remote
            View bottomNavigationView = v.findViewById(R.id.control_method_selector);
            int remoteFrameBottomPadding = avoidZone.bottom - bottomNavigationView.getHeight();
            if (remoteFrameBottomPadding < 0) remoteFrameBottomPadding = 0;

            v.findViewById(R.id.action_bar).setPadding(avoidZone.left, avoidZone.top, avoidZone.right, 0);
            v.findViewById(R.id.remote_frame).setPadding(avoidZone.left, 0, avoidZone.right, remoteFrameBottomPadding);

            // error screen
            if (errorView != null)
                errorView.setPadding(avoidZone.left, avoidZone.top, avoidZone.right, avoidZone.bottom);

            return insets;
        });

        // ui setup
        TextView tvNameText = remoteView.findViewById(R.id.connected_tv_name_text);
        tvNameText.setText(deviceName);

        vibrator = getSystemService(Vibrator.class);

        // restore state
        if (savedInstanceState != null) {
            selectedLayout = savedInstanceState.getInt(KEY_STATE_SELECTED_LAYOUT, DEFAULT_LAYOUT);
        }

        // ensure navbar matches selected value
        NavigationBarView controlMethodSelector = remoteView.findViewById(R.id.control_method_selector);
        controlMethodSelector.setSelectedItemId(selectedLayout);

        setupFixedButtons();

        // layout is different depending on aspect ratio
        // the aspect ratio is taken from the view showing said layout, not the device orientation
        // this can't be done in onCreate because the view hasn't drawn
        remoteView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Log.v(TAG, "global layout");
                remoteView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                ViewCompat.requestApplyInsets(findViewById(R.id.frame));
                refreshRemoteLayout();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_STATE_SELECTED_LAYOUT, selectedLayout);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mediaSessionTracker != null)
            mediaSessionTracker.destroy();
    }

    private void scheduleReconnect() {
        handler.postDelayed(this::connect, CONNECT_RETRY_DELAY);
    }

    private void connect() {
        setConnectionStatus(R.string.connection_status_connecting, true, false);
        binder.connect(deviceName, remoteHostname, remotePort, false);
    }

    @Override
    public void onServiceInit() {
        connect();
    }

    private void hideError() {
        runOnUiThread(() -> {
            if (errorView == null) return;
            FrameLayout frame = findViewById(R.id.frame);
            frame.removeView(errorView);
            errorView = null;
        });
    }

    @Override
    protected void showError(ErrorUtil.ErrorSpec error) {
        runOnUiThread(() -> {
            hideError();

            FrameLayout frame = findViewById(R.id.frame);
            errorView = getLayoutInflater().inflate(R.layout.layout_error, frame, false);
            ErrorUtil.inflateErrorScreen(errorView, error, this::hideError);
            frame.addView(errorView);
            ViewCompat.requestApplyInsets(frame);
        });
    }

    @Override
    public void onSocketConnected() {
        setConnectionStatus(R.string.connection_status_hand_shaking, true, false);
    }

    @Override
    public void onConnected(TVReceiverConnection connection) {
        inputHandler = connection.getInputForwarder();
        capabilities = connection.getCapabilities();

        if (mediaSessionTracker != null)
            mediaSessionTracker.destroy();
        mediaSessionTracker = new MediaSessionTracker(connection);
        mediaSessionTracker.init();

        refreshRemoteLayout(); // new capability set and mediaSessionTracker

        setConnectionStatus(R.string.connection_status_ready, false, true);
    }

    @Override
    public void onConnectError(Throwable t) {
        if (t instanceof IOException) {
            toast(t);
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
        setConnectionStatus(R.string.connection_status_connection_lost, false, false);

        if (isFinishing() || isDestroyed()) return;
        if (t != null) toast(t);
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

    private void toast(Throwable t) {
        toast(ErrorUtil.getExceptionLine(this, t));
    }

    private void setConnectionStatus(@StringRes int text, boolean connecting, boolean allowInput) {
        runOnUiThread(() -> {
            TextView connectionStatusText = findViewById(R.id.connection_status_text);
            connectionStatusText.setText(text);

            findViewById(R.id.connecting_indicator)
                    .setVisibility(connecting ? View.VISIBLE : View.GONE);

            findViewById(R.id.disabled_overlay)
                    .setVisibility(allowInput ? View.GONE : View.VISIBLE);
        });
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

    private Consumer<KeyEventType> wrapToHandleButtonResult(BiFunction<InputHandler, KeyEventType, Sec<Void>> sender) {
        return event -> {
            if (inputHandler == null) return;
            sender.apply(inputHandler, event)
                    .doOnError(this::handleActionError)
                    .callMeWhenDone();
        };
    }

    private Runnable wrapToHandleButtonResult(Function<InputHandler, Sec<Void>> sender) {
        return () -> wrapToHandleButtonResult((ih, event) -> sender.apply(ih)).accept(KeyEventType.CLICK);
    }

    private void setupClickableButton(RemoteButton button, Function<InputHandler, Sec<Void>> clickSender, Function<InputHandler, Sec<Void>> longClickSender) {
        if (button == null) return;
        if (longClickSender == null) {
            button.setClickKeyEvent(wrapToHandleButtonResult(clickSender));
        } else {
            button.setClickKeyEvent(
                    wrapToHandleButtonResult(clickSender),
                    wrapToHandleButtonResult(longClickSender));
        }
    }

    private void setupClickableButton(@IdRes int buttonId, Function<InputHandler, Sec<Void>> clickSender, Function<InputHandler, Sec<Void>> longClickSender) {
        setupClickableButton(findViewById(buttonId), clickSender, longClickSender);
    }

    private void setupDownUpButton(RemoteButton button, BiFunction<InputHandler, KeyEventType, Sec<Void>> sender) {
        if (button == null) return;
        button.setDownUpKeyEvent(wrapToHandleButtonResult(sender));
    }

    private void setupDownUpButton(@IdRes int id, BiFunction<InputHandler, KeyEventType, Sec<Void>> sender) {
        setupDownUpButton(findViewById(id), sender);
    }

    private void setupExtraButton(RemoteButton button, String buttonString) {
        setupClickableButton(button, ih -> ih.pressExtraButton(buttonString), null);
    }

    private void setupRepeatableDownUpButton(RemoteButton button, BiFunction<InputHandler, KeyEventType, Sec<Void>> sender, int repeatInterval) {
        if (button == null) return;
        button.setDownUpKeyEvent(wrapToHandleButtonResult(sender), BUTTON_REPEAT_DELAY, repeatInterval);
    }

    private void setupRepeatableDownUpButton(@IdRes int buttonId, BiFunction<InputHandler, KeyEventType, Sec<Void>> sender, int repeatInterval) {
        setupRepeatableDownUpButton(findViewById(buttonId), sender, repeatInterval);
    }

    private void setupRepeatableUpDownButtonWithResult(@IdRes int buttonId, BiFunction<InputHandler, KeyEventType, Sec<Boolean>> sender, int repeatInterval) {
        RemoteButton button = findViewById(buttonId);
        setupRepeatableDownUpButton(button, (ih, e) -> sender.apply(ih, e)
                .map(r -> {
                    if (!r) vibrator.vibrate(ATTENTION_VIBRATION_EFFECT);
                    return null;
                }), repeatInterval);
    }

    private void setupTrackpad() {
        // trackpad
        TrackpadSurface trackpadSurface = findViewById(R.id.trackpad_surface);
        if (trackpadSurface == null) return;
        trackpadSurface.setMouseTranslationConsumer((x, y) -> {
            if (inputHandler == null) return null;
            return inputHandler.cursorMove(x, y);
        });

        setupDownUpButton(R.id.trackpad_click_button, InputHandler::leftClick);
    }

    private void setupVolumeAdjustButton() {
        View volumeAdjustButton = findViewById(R.id.volume_adjust_button);
        if (volumeAdjustButton == null) return;

        volumeAdjustButton.setOnClickListener(v -> {
            View view = getLayoutInflater().inflate(R.layout.layout_remote_dialog_volume_adjustment, null, false);

            setupRepeatableDownUpButton(view.findViewById(R.id.volume_up_button), InputHandler::volumeUp, VOLUME_BUTTON_REPEAT_INTERVAL);
            setupRepeatableDownUpButton(view.findViewById(R.id.volume_down_button), InputHandler::volumeDown, VOLUME_BUTTON_REPEAT_INTERVAL);
            setupDownUpButton(view.findViewById(R.id.mute_button), InputHandler::toggleMute);

            new AlertDialog.Builder(this)
                    .setTitle(R.string.title_volume_dialog)
                    .setView(view)
                    .setPositiveButton(R.string.button_close, null)
                    .show();
        });
    }

    private void setupKeyboard() {
        EditText autoTypeText = findViewById(R.id.auto_type_text);

        ImageButton submitButton = findViewById(R.id.auto_type_submit_button);
        if (autoTypeText != null && submitButton != null) {

            autoTypeText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == IME_ACTION_SEND) return submitButton.performClick();
                return false;
            });

            submitButton.setOnClickListener(v -> {
                if (inputHandler == null) return;
                autoTypeText.setEnabled(false);
                submitButton.setEnabled(false);
                inputHandler.commitText(String.valueOf(autoTypeText.getText()), 1)
                        .doOnResult(r -> {
                            runOnUiThread(() -> {
                                autoTypeText.setEnabled(true);
                                submitButton.setEnabled(true);
                            });
                            if (r) {
                                runOnUiThread(() -> autoTypeText.setText(""));
                            } else {
                                vibrator.vibrate(ATTENTION_VIBRATION_EFFECT);
                                toast("failed to commit text");
                            }
                        })
                        .doOnError(t -> {
                            runOnUiThread(() -> {
                                autoTypeText.setEnabled(true);
                                submitButton.setEnabled(true);
                            });
                            handleActionError(t);
                        })
                        .callMeWhenDone();
            });
        }

        setupRepeatableUpDownButtonWithResult(R.id.keyboard_arrow_up, (ih, e) -> ih.sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP, e), KEYBOARD_EXTRA_BUTTON_REPEAT_INTERVAL);
        setupRepeatableUpDownButtonWithResult(R.id.keyboard_arrow_down, (ih, e) -> ih.sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, e), KEYBOARD_EXTRA_BUTTON_REPEAT_INTERVAL);
        setupRepeatableUpDownButtonWithResult(R.id.keyboard_arrow_left, (ih, e) -> ih.sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, e), KEYBOARD_EXTRA_BUTTON_REPEAT_INTERVAL);
        setupRepeatableUpDownButtonWithResult(R.id.keyboard_arrow_right, (ih, e) -> ih.sendKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, e), KEYBOARD_EXTRA_BUTTON_REPEAT_INTERVAL);
        setupRepeatableUpDownButtonWithResult(R.id.keyboard_home_key, (ih, e) -> ih.sendKeyEvent(KeyEvent.KEYCODE_MOVE_HOME, e), KEYBOARD_EXTRA_BUTTON_REPEAT_INTERVAL);
        setupRepeatableUpDownButtonWithResult(R.id.keyboard_end_key, (ih, e) -> ih.sendKeyEvent(KeyEvent.KEYCODE_MOVE_END, e), KEYBOARD_EXTRA_BUTTON_REPEAT_INTERVAL);
        setupRepeatableUpDownButtonWithResult(R.id.keyboard_delete_key, (ih, e) -> ih.sendKeyEvent(KeyEvent.KEYCODE_FORWARD_DEL, e), KEYBOARD_EXTRA_BUTTON_REPEAT_INTERVAL);
        setupRepeatableUpDownButtonWithResult(R.id.keyboard_backspace_key, (ih, e) -> ih.sendKeyEvent(KeyEvent.KEYCODE_DEL, e), KEYBOARD_EXTRA_BUTTON_REPEAT_INTERVAL);
    }

    private void setupRemoteButtons() {
        if (capabilities == null) return;

        // dpad
        setupRepeatableDownUpButton(R.id.up_button, InputHandler::dpadUp, DPAD_BUTTON_REPEAT_INTERVAL);
        setupRepeatableDownUpButton(R.id.down_button, InputHandler::dpadDown, DPAD_BUTTON_REPEAT_INTERVAL);
        setupRepeatableDownUpButton(R.id.left_button, InputHandler::dpadLeft, DPAD_BUTTON_REPEAT_INTERVAL);
        setupRepeatableDownUpButton(R.id.right_button, InputHandler::dpadRight, DPAD_BUTTON_REPEAT_INTERVAL);
        setupDownUpButton(R.id.select_button, InputHandler::dpadSelect);

        // nav bar
        setupDownUpButton(R.id.back_button, InputHandler::navBack);
        setupDownUpButton(R.id.recent_button, InputHandler::navRecent);

        // home button (hold for dashboard on gtv)
        // todo: the receiver should handle this on control methods that don't support up/down events
        if (capabilities.hasButton(ReceiverCapabilities.EXTRA_BUTTON_GTV_DASHBOARD)) {
            setupClickableButton(R.id.home_button, ih -> ih.navHome(KeyEventType.CLICK), i -> i.pressExtraButton(ReceiverCapabilities.EXTRA_BUTTON_GTV_DASHBOARD));
        } else {
            setupDownUpButton(R.id.home_button, InputHandler::navHome);
        }

        // menu button (notifications/dashboard)
        RemoteImageButton menuButton = findViewById(R.id.menu_button);
        if (menuButton != null) {
            if (capabilities.hasButton(ReceiverCapabilities.EXTRA_BUTTON_GTV_DASHBOARD)) {
                setupExtraButton(menuButton, ReceiverCapabilities.EXTRA_BUTTON_GTV_DASHBOARD);
                menuButton.setImageResource(R.drawable.gtv_dashboard);
            } else if (capabilities.hasButton(ReceiverCapabilities.EXTRA_BUTTON_LINEAGE_SYSTEM_OPTIONS)) {
                setupExtraButton(menuButton, ReceiverCapabilities.EXTRA_BUTTON_LINEAGE_SYSTEM_OPTIONS);
                menuButton.setImageResource(R.drawable.notifications);
            } else {
                setupDownUpButton(menuButton, InputHandler::navNotifications);
                menuButton.setImageResource(R.drawable.notifications);
            }
        }

//        setupBasicButton(R.id.quick_settings_button, InputHandler::navQuickSettings);

        // volume
        setupVolumeAdjustButton();

        // media
        setupRepeatableDownUpButton(R.id.skip_backward_button, InputHandler::skipBackward, SKIP_BUTTON_REPEAT_INTERVAL);
        setupRepeatableDownUpButton(R.id.prev_track_button, InputHandler::prevTrack, TRACK_BUTTON_REPEAT_INTERVAL);
        setupDownUpButton(R.id.pause_button, InputHandler::playPause);
        setupRepeatableDownUpButton(R.id.next_track_button, InputHandler::nextTrack, TRACK_BUTTON_REPEAT_INTERVAL);
        setupRepeatableDownUpButton(R.id.skip_forward_button, InputHandler::skipForward, SKIP_BUTTON_REPEAT_INTERVAL);

        // trackpad
        setupTrackpad();

        // keyboard
        setupKeyboard();
    }

    private class PrimaryMediaSessionCallback implements MediaSessionTracker.MediaSessionCallback {
        private long duration = 0;

        private void setNullableText(TextView textView, String text) {
            if (textView != null) {
                if (text == null) {
                    textView.setVisibility(View.INVISIBLE);
                } else {
                    textView.setVisibility(View.VISIBLE);
                    textView.setText(text);
                }
            }
        }

        private int calculateSeekProgress(long elapsed) {
            return (int) (elapsed * SEEK_BAR_MAX / duration);
        }

        @Override
        public void onMetadataUpdated(MediaMetaEvent metaEvent) {
            TextView mediaTitle = findViewById(R.id.media_title_text);
            TextView mediaSubtitle = findViewById(R.id.media_subtitle_text);
            TextView appLabel = findViewById(R.id.media_app_label_text);

            TextView endTimeText = findViewById(R.id.media_end_text);
            SeekBar seekBar = findViewById(R.id.media_seek_bar);

            if (metaEvent == null) {
                if (mediaTitle != null) mediaTitle.setVisibility(View.INVISIBLE);
                if (mediaSubtitle != null) mediaSubtitle.setVisibility(View.INVISIBLE);
                if (appLabel != null) appLabel.setVisibility(View.INVISIBLE);
                if (endTimeText != null) endTimeText.setVisibility(View.INVISIBLE);
                if (seekBar != null) seekBar.setVisibility(View.INVISIBLE);
            } else {

                setNullableText(mediaTitle, metaEvent.title());
                setNullableText(mediaSubtitle, metaEvent.subtitle());
                setNullableText(appLabel, metaEvent.sourceName());

                if (metaEvent.length() == null || metaEvent.length() < 1) {
                    // likely unknown, live stream, or loading
                    if(endTimeText != null) endTimeText.setText(R.string.media_display_timestamp_end_placeholder);
                    duration = -1;
                } else {
                    // known timestamp
                    if (endTimeText != null) endTimeText.setText(MessageFormat.format(
                            getString(R.string.media_display_timestamp_end_format),
                            UiUtil.formatMediaTimestampMS(RemoteActivity.this, metaEvent.length())
                    ));
                    duration = metaEvent.length();
                }
            }
        }

        @Override
        public void onStateUpdated(MediaStateEvent stateEvent) {
            RemoteImageButton pausePlayButton = findViewById(R.id.pause_button);
            if (pausePlayButton == null) return;

            if (stateEvent == null) {
                // default to pause
                pausePlayButton.setImageResource(R.drawable.playpause);
            } else {
                if (stateEvent.paused()) pausePlayButton.setImageResource(R.drawable.play);
                else if (stateEvent.playing()) pausePlayButton.setImageResource(R.drawable.pause);
            }
        }

        @Override
        public void onPositionUpdated(MediaPositionEvent positionEvent) {
            TextView elapsedTimeText = findViewById(R.id.media_elapsed_text);
            TextView endTimeText = findViewById(R.id.media_end_text);
            SeekBar seekBar = findViewById(R.id.media_seek_bar);

            if (positionEvent == null) {
                if (endTimeText != null) endTimeText.setVisibility(View.INVISIBLE);
                if (elapsedTimeText != null) elapsedTimeText.setVisibility(View.INVISIBLE);
                if (seekBar != null) seekBar.setVisibility(View.INVISIBLE);
            } else {
                if (seekBar != null) {
                    if (positionEvent.position() != null && duration > 0) {
                        seekBar.setProgress(calculateSeekProgress(positionEvent.position()));
                        if (positionEvent.bufferedPosition() != null)
                            seekBar.setSecondaryProgress(calculateSeekProgress(positionEvent.bufferedPosition()));

                        seekBar.setVisibility(View.VISIBLE);
                    } else {
                        seekBar.setVisibility(View.INVISIBLE);
                    }
                }

                if (elapsedTimeText != null) {
                    // show timestamps if position known
                    if (positionEvent.position() == null) {
                        elapsedTimeText.setVisibility(View.INVISIBLE);
                        if (endTimeText != null) endTimeText.setVisibility(View.INVISIBLE);
                    } else {
                        elapsedTimeText.setText(UiUtil.formatMediaTimestampMS(RemoteActivity.this, positionEvent.position()));
                        elapsedTimeText.setVisibility(View.VISIBLE);
                        if (endTimeText != null) endTimeText.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        @Override
        public void onDestroyed() {
            onMetadataUpdated(null);
            onStateUpdated(null);
            onPositionUpdated(null);
        }
    }

    private void refreshRemoteLayout() {
        runOnUiThread(() -> {
            FrameLayout remoteFrame = findViewById(R.id.remote_frame);
            remoteFrame.removeAllViews();

            // layout
            LayoutOrientationSelector selector = LAYOUT_MAP.get(selectedLayout);
            if (selector == null) throw new IllegalStateException("selected layout does not exist in layout map!");

            // orientation
            boolean portrait = remoteFrame.getHeight() > remoteFrame.getWidth();
            int layout = portrait ? selector.portraitLayout() : selector.landscapeLayout();

            // dpad landscape mode needs a minimum width
            if (layout == R.layout.layout_remote_standard_landscape) {
                float minWidth = UiUtil.dpToPx(this, LAYOUT_REMOTE_STANDARD_LANDSCAPE_MIN_WIDTH_DP);
                if (remoteFrame.getWidth() < minWidth) {
                    layout = selector.portraitLayout();
                }
            }

            // inflate
            getLayoutInflater().inflate(layout, remoteFrame, true);
            setupRemoteButtons();

            // remove things if it doesn't fit
            // would be nice to do this dynamically at some point
            if (layout == R.layout.layout_remote_standard) {
                float height = remoteFrame.getHeight();
                float minHeight = UiUtil.dpToPx(this, LAYOUT_REMOTE_STANDARD_PORTRAIT_MIN_HEIGHT_DP);

                if (height < minHeight) {
                    // remove dpad padding
                    View dpad = findViewById(R.id.dpad);
                    dpad.setPadding(dpad.getPaddingLeft(), 0, dpad.getPaddingRight(), 0);
                    minHeight -= UiUtil.dpToPx(this, DPAD_PADDING_HEIGHT_DP);
                }
                if (height < minHeight) {
                    // remove media summary
                    findViewById(R.id.media_summary).setVisibility(View.GONE);
                    minHeight -= UiUtil.dpToPx(this, MEDIA_SUMMARY_MIN_HEIGHT_DP);
                }
                if (height < minHeight) {
                    // remove media section entirely
                    findViewById(R.id.media_section).setVisibility(View.GONE);
                    minHeight -= UiUtil.dpToPx(this, MEDIA_CONTROLS_MIN_HEIGHT_DP);
                }
                if (height < minHeight) {
                    Log.w(TAG, "could not meet an acceptable UI for the screen size");
                }

            }

            // media
            if (mediaSessionTracker != null) {
                mediaSessionTracker.setPrimarySessionCallback(new PrimaryMediaSessionCallback());
                if (selectedLayout == R.id.media_selector_button) {
                    // media layout has a seek bar
                    mediaSessionTracker.trackPosition()
                            .doOnError(t -> {
                                Log.e(TAG, "failed to start tracking media", t);
                                toast(t);
                            })
                            .callMeWhenDone();
                } else {
                    mediaSessionTracker.stopTrackingPosition();
                }
            }

        });
    }

    private void setupFixedButtons() {
        View disconnectButton = findViewById(R.id.disconnect_button);
        disconnectButton.setOnClickListener(v -> finish());

        NavigationBarView controlMethodSelector = findViewById(R.id.control_method_selector);
        controlMethodSelector.setOnItemSelectedListener(item -> {
            @IdRes int layout = item.getItemId();
            assert (LAYOUT_MAP.containsKey(layout));
            selectedLayout = layout;
            refreshRemoteLayout();
            return true;
        });

    }

}