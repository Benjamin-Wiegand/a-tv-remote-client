package io.benwiegand.atvremote.phone.network.discovery;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

public class ServiceExplorer implements NsdManager.DiscoveryListener {
    private static final String TAG = ServiceExplorer.class.getSimpleName();

    /**
     * despite the documentation claiming start failures are sent to onStartDiscoveryFailed(),
     * sometimes (most of the time) it does not do that and instead throws in startServiceDiscovery().
     * the same goes for onStopDiscoveryFailed() with stopServiceDiscovery()
     */
    public static final int FAILURE_THE_DOCUMENTATION_LIES = -0xDEADBEEF;

    private final NsdManager nsdManager;

    private final ServiceDiscoveryCallback discoveryCallback;

    public ServiceExplorer(NsdManager nsdManager, ServiceDiscoveryCallback discoveryCallback) {
        this.nsdManager = nsdManager;
        this.discoveryCallback = discoveryCallback;
    }

    public void startDiscovery(String serviceType) {
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, this);
        } catch (Throwable t) {
            onStartDiscoveryFailed(serviceType, FAILURE_THE_DOCUMENTATION_LIES, t);
        }
    }

    public void stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(this);
        } catch (Throwable t) {
            onStopDiscoveryFailed(null, FAILURE_THE_DOCUMENTATION_LIES, t);
        }
    }

    public void retryResolve(NsdServiceInfo serviceInfo) {
        discoveryCallback.serviceDiscoveredPreResolution(serviceInfo.getServiceName(), serviceInfo);
        tryResolve(serviceInfo, 1);
    }

    @Override
    public void onDiscoveryStarted(String serviceType) {
        Log.i(TAG, "discovery started for service type: " + serviceType);
        discoveryCallback.discoveryStarted();
    }

    @Override
    public void onStartDiscoveryFailed(String serviceType, int errorCode) {
        onStartDiscoveryFailed(serviceType, errorCode, null);
    }

    public void onStartDiscoveryFailed(String serviceType, int errorCode, Throwable t) {
        ServiceDiscoveryException e = createExceptionForErrorCode(errorCode, t);
        Log.e(TAG, "discovery failed for service type: " + serviceType, e);
        discoveryCallback.discoveryFailure(e, false);
    }

    @Override
    public void onServiceFound(NsdServiceInfo serviceInfo) {
        Log.v(TAG, "found service: " + serviceInfo);
        discoveryCallback.serviceDiscoveredPreResolution(serviceInfo.getServiceName(), serviceInfo);
        tryResolve(serviceInfo, 3);
    }

    private void tryResolve(NsdServiceInfo serviceInfo, int maxTries) {
        nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.v(TAG, "resolved: " + serviceInfo);
                discoveryCallback.serviceDiscovered(serviceInfo.getServiceName(), serviceInfo);
            }

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                ServiceDiscoveryException e = createExceptionForErrorCode(errorCode, null);
                Log.w(TAG, "resolve failed for: " + serviceInfo, e);

                if (maxTries > 0) {
                    tryResolve(serviceInfo, maxTries - 1);
                } else {
                    Log.e(TAG, "giving up resolving: " + serviceInfo);
                    discoveryCallback.resolveFailed(serviceInfo.getServiceName(), serviceInfo, e);
                }
            }
        });
    }

    @Override
    public void onServiceLost(NsdServiceInfo serviceInfo) {
        Log.v(TAG, "service lost: " + serviceInfo);
        discoveryCallback.serviceLost(serviceInfo.getServiceName());
    }

    @Override
    public void onDiscoveryStopped(String serviceType) {
        Log.i(TAG, "service discovery stopped for service type: " + serviceType);
        discoveryCallback.discoveryStopped();
    }

    @Override
    public void onStopDiscoveryFailed(String serviceType, int errorCode) {
        onStopDiscoveryFailed(serviceType, errorCode, null);
    }

    public void onStopDiscoveryFailed(String serviceType, int errorCode, Throwable t) {
        ServiceDiscoveryException e = createExceptionForErrorCode(errorCode, t);
        Log.e(TAG, "stop discovery failed for service type: " + serviceType, e);
        discoveryCallback.discoveryFailure(e, true);
        // the documentation example has a call to stopServiceDiscovery() here, but that just seems to cause a crash
    }

    private ServiceDiscoveryException createExceptionForErrorCode(int errorCode, Throwable t) {
        return t == null ?
                new ServiceDiscoveryException(errorCode):
                new ServiceDiscoveryException(errorCode, t);
    }


}
