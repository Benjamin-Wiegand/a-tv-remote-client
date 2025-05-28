package io.benwiegand.atvremote.phone.ui;

import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.MDNS_SERVICE_TYPE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.network.discovery.ServiceDiscoveryCallback;
import io.benwiegand.atvremote.phone.network.discovery.ServiceDiscoveryException;
import io.benwiegand.atvremote.phone.network.discovery.ServiceExplorer;

public class TVDiscoveryActivity extends DynamicColorsCompatActivity implements ServiceDiscoveryCallback {
    private static final String TAG = TVDiscoveryActivity.class.getSimpleName();

    private static final float ENTRY_DISABLED_ALPHA = 0.5f;
    private static final float ENTRY_STALE_ALPHA = 0.5f;
    private static final float ENTRY_ENABLED_ALPHA = 1;

    private final Map<String, View> discoveredServiceViewMap = new HashMap<>();
    private final List<View> staleServices = new ArrayList<>();
    private ServiceExplorer serviceExplorer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tvdiscovery);

        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.manual_connection_button)
                .setOnClickListener(v -> {
                    Intent intent = new Intent(this, ManualConnectionActivity.class);
                    startActivity(intent);
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

        uriText.setText("resolving..."); //todo
        resolvedIndicator.setVisibility(View.GONE);
        resolvingIndicator.setVisibility(View.VISIBLE);
        receiverEntry.setEnabled(false);
        receiverEntry.setAlpha(ENTRY_DISABLED_ALPHA);
    }

    private void setEntryViewResolved(View receiverEntry, String deviceName, String hostname, int port) {
        TextView uriText = receiverEntry.findViewById(R.id.uri);
        View resolvedIndicator = receiverEntry.findViewById(R.id.resolved_indicator);
        View resolvingIndicator = receiverEntry.findViewById(R.id.resolving_indicator);

        uriText.setText("tcp://" + hostname + ":" + port);
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

    private void updateOrInflateEntry(String key, NsdServiceInfo serviceInfo) {
        InetAddress host = serviceInfo.getHost();
        String hostname = host == null ? null : host.getHostAddress();
        String deviceName = serviceInfo.getServiceName();

        runOnUiThread(() -> {
            View receiverEntry = discoveredServiceViewMap.get(key);
            if (receiverEntry == null) {
                inflatePartialEntry(key, deviceName);
                receiverEntry = discoveredServiceViewMap.get(key);
                assert receiverEntry != null;
            }

            TextView hostnameText = receiverEntry.findViewById(R.id.device_name);
            hostnameText.setText(deviceName);

            if (host == null) {
                setEntryViewResolving(receiverEntry);
            } else {
                setEntryViewResolved(receiverEntry, deviceName, hostname, serviceInfo.getPort());
            }

        });
    }

    @Override
    public void serviceDiscoveredPreResolution(String key, NsdServiceInfo partialServiceInfo) {
        String deviceName = partialServiceInfo.getServiceName();
        inflatePartialEntry(key, deviceName);
        removeStaleEntries();
    }

    @Override
    public void serviceDiscovered(String key, NsdServiceInfo serviceInfo) {
        updateOrInflateEntry(key, serviceInfo);
        removeStaleEntries();
    }

    @Override
    public void resolveFailed(String key, NsdServiceInfo partialServiceInfo, ServiceDiscoveryException e) {
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
        runOnUiThread(() -> {
            findViewById(R.id.service_discovery_indicator)
                    .setVisibility(View.INVISIBLE);
        });
    }

    @Override
    public void discoveryFailure(ServiceDiscoveryException e) {
        runOnUiThread(() -> {
            findViewById(R.id.service_discovery_indicator)
                    .setVisibility(View.INVISIBLE);

            // todo: error message
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