package io.benwiegand.atvremote.phone.ui;

import static io.benwiegand.atvremote.phone.network.discovery.ServiceExplorer.FAILURE_THE_DOCUMENTATION_LIES;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.MDNS_SERVICE_TYPE;

import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.net.InetAddress;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.network.discovery.ServiceDiscoveryCallback;
import io.benwiegand.atvremote.phone.network.discovery.ServiceDiscoveryException;
import io.benwiegand.atvremote.phone.network.discovery.ServiceExplorer;
import io.benwiegand.atvremote.phone.util.ErrorUtil;
import io.benwiegand.atvremote.phone.util.UiUtil;

public class TVDiscoveryActivity extends DynamicColorsCompatActivity implements ServiceDiscoveryCallback {
    private static final String TAG = TVDiscoveryActivity.class.getSimpleName();

    private static final float ENTRY_DISABLED_ALPHA = 0.5f;
    private static final float ENTRY_STALE_ALPHA = 0.5f;
    private static final float ENTRY_ENABLED_ALPHA = 1;

    private final Map<String, View> discoveredServiceViewMap = new HashMap<>();
    private final List<View> staleServices = new ArrayList<>();
    private ServiceExplorer serviceExplorer = null;

    private View errorView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_frame);

        FrameLayout frame = findViewById(R.id.frame);
        View mainView = getLayoutInflater().inflate(R.layout.activity_tvdiscovery, frame, true);

        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(frame, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mainView.findViewById(R.id.manual_connection_button)
                .setOnClickListener(v -> launchManualConnection());

    }

    private void launchManualConnection() {
        Intent intent = new Intent(this, ManualConnectionActivity.class);
        startActivity(intent);
    }

    private void hideError() {
        runOnUiThread(() -> {
            if (errorView == null) return;
            FrameLayout frame = findViewById(R.id.frame);
            frame.removeView(errorView);
            errorView = null;
        });
    }

    private void showError(ErrorUtil.ErrorSpec error) {
        runOnUiThread(() -> {
            hideError();

            FrameLayout frame = findViewById(R.id.frame);
            errorView = getLayoutInflater().inflate(R.layout.layout_error, frame, false);
            ErrorUtil.inflateErrorScreen(errorView, error, this::hideError);
            frame.addView(errorView);
        });
    }

    private void removeStaleEntries() {
        runOnUiThread(() -> {
            LinearLayout resultList = findViewById(R.id.discovery_result_list);
            staleServices.forEach(resultList::removeView);
        });
    }

    private void setEntryViewResolving(View receiverEntry) {
        TextView uriText = receiverEntry.findViewById(R.id.uri);
        View resolvedIndicator = receiverEntry.findViewById(R.id.resolved_indicator);
        View resolvingIndicator = receiverEntry.findViewById(R.id.resolving_indicator);
        View retryButton = receiverEntry.findViewById(R.id.retry_resolve_button);

        uriText.setText(R.string.discovery_result_entry_resolving);
        resolvedIndicator.setVisibility(View.GONE);
        resolvingIndicator.setVisibility(View.VISIBLE);
        receiverEntry.setEnabled(false);
        receiverEntry.setAlpha(ENTRY_DISABLED_ALPHA);
        retryButton.setVisibility(View.GONE);
    }

    private void setEntryViewResolved(View receiverEntry, String deviceName, String hostname, int port) {
        TextView uriText = receiverEntry.findViewById(R.id.uri);
        View resolvedIndicator = receiverEntry.findViewById(R.id.resolved_indicator);
        View resolvingIndicator = receiverEntry.findViewById(R.id.resolving_indicator);
        View retryButton = receiverEntry.findViewById(R.id.retry_resolve_button);

        uriText.setText(MessageFormat.format(
                getString(R.string.discovery_result_entry_uri_format),
                hostname, String.valueOf(port) /* port numbers shouldn't have commas */));

        receiverEntry.setOnClickListener(v -> {
            Log.d(TAG, "Starting remote activity");

            Intent intent = new Intent(this, RemoteActivity.class);
            intent.putExtra(RemoteActivity.EXTRA_DEVICE_NAME, deviceName);
            intent.putExtra(RemoteActivity.EXTRA_HOSTNAME, hostname);
            intent.putExtra(RemoteActivity.EXTRA_PORT_NUMBER, port);

            startActivity(intent);
        });

        resolvedIndicator.setVisibility(View.VISIBLE);
        resolvingIndicator.setVisibility(View.GONE);
        receiverEntry.setEnabled(true);
        receiverEntry.setAlpha(ENTRY_ENABLED_ALPHA);
        retryButton.setVisibility(View.GONE);
    }

    private void setEntryViewResolveFailed(View receiverEntry, NsdServiceInfo serviceInfo, Throwable resolveError) {
        TextView uriText = receiverEntry.findViewById(R.id.uri);
        View resolvedIndicator = receiverEntry.findViewById(R.id.resolved_indicator);
        View resolvingIndicator = receiverEntry.findViewById(R.id.resolving_indicator);
        View retryButton = receiverEntry.findViewById(R.id.retry_resolve_button);

        String message;
        if (resolveError instanceof ErrorMessageException e) {
            message = e.getLocalizedMessage(this);
        } else {
            message = ErrorUtil.getExceptionLine(this, resolveError);
        }

        uriText.setText(MessageFormat.format(getString(R.string.discovery_failed_resolution), message));

        retryButton.setOnClickListener(v -> serviceExplorer.retryResolve(serviceInfo));

        resolvedIndicator.setVisibility(View.VISIBLE);
        resolvingIndicator.setVisibility(View.GONE);
        receiverEntry.setEnabled(false);
        receiverEntry.setAlpha(ENTRY_ENABLED_ALPHA);
        retryButton.setVisibility(View.VISIBLE);
    }

    private void inflatePartialEntry(String key, String deviceName) {
        runOnUiThread(() -> {
            LinearLayout resultList = findViewById(R.id.discovery_result_list);
            View receiverEntry = getLayoutInflater().inflate(R.layout.layout_discovered_receiver_entry, resultList, false);

            TextView hostnameText = receiverEntry.findViewById(R.id.device_name);
            hostnameText.setText(deviceName);

            setEntryViewResolving(receiverEntry);

            View oldEntry = discoveredServiceViewMap.put(key, receiverEntry);
            if (oldEntry != null)
                resultList.removeView(oldEntry);

            resultList.addView(receiverEntry);
        });
    }

    private void updateOrInflateEntry(String key, NsdServiceInfo serviceInfo, Throwable resolveError) {
        InetAddress host = serviceInfo.getHost();
        String hostname = host == null ? null : host.getHostAddress();
        String deviceName = serviceInfo.getServiceName();
        int port = serviceInfo.getPort();

        runOnUiThread(() -> {
            View receiverEntry = discoveredServiceViewMap.get(key);
            if (receiverEntry == null) {
                inflatePartialEntry(key, deviceName);
                receiverEntry = discoveredServiceViewMap.get(key);
                assert receiverEntry != null;
            }

            TextView hostnameText = receiverEntry.findViewById(R.id.device_name);
            hostnameText.setText(deviceName);

            if (resolveError != null) {
                setEntryViewResolveFailed(receiverEntry, serviceInfo, resolveError);
            } else if (host == null) {
                setEntryViewResolving(receiverEntry);
            } else {
                setEntryViewResolved(receiverEntry, deviceName, hostname, port);
            }

        });
    }

    private void updateOrInflateEntry(String key, NsdServiceInfo serviceInfo) {
        updateOrInflateEntry(key, serviceInfo, null);
    }

    @Override
    public void serviceDiscoveredPreResolution(String key, NsdServiceInfo partialServiceInfo) {
        updateOrInflateEntry(key, partialServiceInfo);
        removeStaleEntries();
    }

    @Override
    public void serviceDiscovered(String key, NsdServiceInfo serviceInfo) {
        updateOrInflateEntry(key, serviceInfo);
        removeStaleEntries();
    }

    @Override
    public void resolveFailed(String key, NsdServiceInfo partialServiceInfo, ServiceDiscoveryException e) {
        updateOrInflateEntry(key, partialServiceInfo, e);
        removeStaleEntries();
    }

    @Override
    public void serviceLost(String key) {
        runOnUiThread(() -> {
            View view = discoveredServiceViewMap.remove(key);
            if (view == null) return;

            LinearLayout resultList = findViewById(R.id.discovery_result_list);
            resultList.removeView(view);
        });
    }

    @Override
    public void discoveryStarted() {
        runOnUiThread(() -> {
            findViewById(R.id.service_discovery_indicator)
                    .setVisibility(View.VISIBLE);
            findViewById(R.id.service_discovery_notice)
                    .setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void discoveryStopped() {
        serviceExplorer = null;
        runOnUiThread(() -> findViewById(R.id.service_discovery_indicator)
                .setVisibility(View.INVISIBLE));
    }

    @Override
    public void discoveryFailure(ServiceDiscoveryException e, boolean whileStopping) {
        if (whileStopping && e.getErrorCode() == FAILURE_THE_DOCUMENTATION_LIES & e.getCause() instanceof IllegalArgumentException) {
            // it's complaining that discovery was already stopped
            return;
        }

        runOnUiThread(() -> {
            findViewById(R.id.service_discovery_indicator)
                    .setVisibility(View.INVISIBLE);

            showError(new ErrorUtil.ErrorSpec(R.string.discovery_failed_init, e,
                    new UiUtil.ButtonPreset(R.string.button_retry, v -> startDiscovery()),
                    new UiUtil.ButtonPreset(R.string.button_cancel, v -> finish()),
                    new UiUtil.ButtonPreset(R.string.button_manual_connection, v -> launchManualConnection())
            ));
        });
    }

    private void stopDiscovery() {
        if (serviceExplorer == null) return;
        serviceExplorer.stopDiscovery();
        serviceExplorer = null;
    }

    private void startDiscovery() {
        stopDiscovery(); // ensure stopped

        // gray out existing entries as they are "stale"
        // why? because I want to throw my phone into a wall when I hit back and everything in the
        // discovery page is gone and I have to wait for it to scan again.
        for (View view : discoveredServiceViewMap.values())
            view.setAlpha(ENTRY_STALE_ALPHA);

        removeStaleEntries(); // remove the _old_ stale entries
        staleServices.addAll(discoveredServiceViewMap.values());
        discoveredServiceViewMap.clear();

        NsdManager nsdManager = getSystemService(NsdManager.class);
        serviceExplorer = new ServiceExplorer(nsdManager, this);
        serviceExplorer.startDiscovery(MDNS_SERVICE_TYPE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        stopDiscovery();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        startDiscovery();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }
}