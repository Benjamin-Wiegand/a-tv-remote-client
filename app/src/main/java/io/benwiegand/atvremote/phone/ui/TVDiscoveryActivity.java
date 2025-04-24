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

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.network.discovery.ServiceDiscoveryCallback;
import io.benwiegand.atvremote.phone.network.discovery.ServiceDiscoveryException;
import io.benwiegand.atvremote.phone.network.discovery.ServiceExplorer;

public class TVDiscoveryActivity extends AppCompatActivity implements ServiceDiscoveryCallback {
    private static final String TAG = TVDiscoveryActivity.class.getSimpleName();

    private final Map<String, View> discoveredServiceViewMap = new HashMap<>();
    private ServiceExplorer serviceExplorer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tvdiscovery);

        findViewById(R.id.manual_connection_button)
                .setOnClickListener(v -> {
                    // todo: using placeholder DebugActivity for now
                    Intent intent = new Intent(this, DebugActivity.class);
                    startActivity(intent);
                });

        NsdManager nsdManager = getSystemService(NsdManager.class);
        serviceExplorer = new ServiceExplorer(nsdManager, this);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void serviceDiscovered(String key, NsdServiceInfo serviceInfo) {
        String hostname = serviceInfo.getHost().getHostAddress();
        String deviceName = serviceInfo.getServiceName();

        runOnUiThread(() -> {
            LinearLayout resultList = findViewById(R.id.discovery_result_list);
            View receiverEntry = getLayoutInflater().inflate(R.layout.layout_discovered_receiver_entry, resultList, false);

            TextView hostnameText = receiverEntry.findViewById(R.id.device_name);
            hostnameText.setText(deviceName);

            TextView uriText = receiverEntry.findViewById(R.id.uri);
            uriText.setText("tcp://" + serviceInfo.getHost().getHostAddress() + ":" + serviceInfo.getPort());

            receiverEntry.setOnClickListener(v -> {
                Log.d(TAG, "Starting remote activity");

                Intent intent = new Intent(this, RemoteActivity.class);
                intent.putExtra(RemoteActivity.EXTRA_DEVICE_NAME, deviceName);
                intent.putExtra(RemoteActivity.EXTRA_HOSTNAME, hostname);
                intent.putExtra(RemoteActivity.EXTRA_PORT_NUMBER, serviceInfo.getPort());

                startActivity(intent);
            });

            View oldEntry = discoveredServiceViewMap.put(key, receiverEntry);
            if (oldEntry != null)
                resultList.removeView(oldEntry);

            resultList.addView(receiverEntry);
        });
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

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        serviceExplorer.stopDiscovery();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        // todo: make a new service explorer here (there are edge cases that break state)
        serviceExplorer.startDiscovery(MDNS_SERVICE_TYPE);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }
}