package io.benwiegand.atvremote.phone.network.discovery;

import android.net.nsd.NsdServiceInfo;

public interface ServiceDiscoveryCallback {
    void serviceDiscovered(String key, NsdServiceInfo serviceInfo);
    void resolveFailed(NsdServiceInfo partialServiceInfo, ServiceDiscoveryException e);
    void serviceLost(String key);

    void discoveryStarted();
    void discoveryStopped();
    void discoveryFailure(ServiceDiscoveryException e);
}
