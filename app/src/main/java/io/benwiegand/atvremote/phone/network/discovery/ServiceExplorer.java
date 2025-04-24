package io.benwiegand.atvremote.phone.network.discovery;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

public class ServiceExplorer implements NsdManager.DiscoveryListener {
    private static final String TAG = ServiceExplorer.class.getSimpleName();

    private final NsdManager nsdManager;

    private final ServiceDiscoveryCallback discoveryCallback;

    public ServiceExplorer(NsdManager nsdManager, ServiceDiscoveryCallback discoveryCallback) {
        this.nsdManager = nsdManager;
        this.discoveryCallback = discoveryCallback;
    }

    public void startDiscovery(String serviceType) {
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, this);
    }

    public void stopDiscovery() {
        nsdManager.stopServiceDiscovery(this);
    }

    @Override
    public void onDiscoveryStarted(String serviceType) {
        Log.i(TAG, "discovery started for service type: " + serviceType);
        discoveryCallback.discoveryStarted();
    }

    @Override
    public void onStartDiscoveryFailed(String serviceType, int errorCode) {
        ServiceDiscoveryException e = createExceptionForErrorCode(errorCode);
        Log.e(TAG, "discovery failed for service type: " + serviceType, e);
        discoveryCallback.discoveryFailure(e);
    }

    @Override
    public void onServiceFound(NsdServiceInfo serviceInfo) {
        Log.v(TAG, "found service: " + serviceInfo);
        tryResolve(serviceInfo, 3);
    }

    private void tryResolve(NsdServiceInfo serviceInfo, int maxTries) {
        nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.v(TAG, "resolved: " + serviceInfo);
                discoveryCallback.serviceDiscovered(serviceInfo.getServiceName(), serviceInfo);;
            }

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                ServiceDiscoveryException e = createExceptionForErrorCode(errorCode);
                Log.w(TAG, "resolve failed for: " + serviceInfo, e);

                if (maxTries > 0)
                    tryResolve(serviceInfo, maxTries - 1);
                else
                    Log.e(TAG, "giving up resolving: " + serviceInfo);
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
        ServiceDiscoveryException e = createExceptionForErrorCode(errorCode);
        Log.e(TAG, "stop discovery failed for service type: " + serviceType, e);
        nsdManager.stopServiceDiscovery(this);
        discoveryCallback.discoveryFailure(e);
    }

    private ServiceDiscoveryException createExceptionForErrorCode(int errorCode) {
        return new ServiceDiscoveryException(mapErrorCodeDebugString(errorCode));
    }

    private String mapErrorCodeDebugString(int errorCode) {
        return switch (errorCode) {
            case NsdManager.FAILURE_INTERNAL_ERROR -> "FAILURE_INTERNAL_ERROR";
            case NsdManager.FAILURE_ALREADY_ACTIVE -> "FAILURE_ALREADY_ACTIVE";
            case NsdManager.FAILURE_MAX_LIMIT -> "FAILURE_MAX_LIMIT";
            case NsdManager.FAILURE_OPERATION_NOT_RUNNING -> "FAILURE_OPERATION_NOT_RUNNING";
            case NsdManager.FAILURE_BAD_PARAMETERS -> "FAILURE_BAD_PARAMETERS";
            default -> "(UNKNOWN_ERROR_CODE " + errorCode + ")";
        };
    }

}
