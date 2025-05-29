package io.benwiegand.atvremote.phone.protocol;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.benwiegand.atvremote.phone.auth.ssl.CorruptedKeystoreException;
import io.benwiegand.atvremote.phone.auth.ssl.KeystoreManager;

public class PairingManager {
    private static final String TAG = PairingManager.class.getSimpleName();

    private static final String KEY_PAIRED_DEVICES = "tvs";
    private static final String KEY_PREFIX_PAIRING_DATA = "pairing_data_";

    private final Context context;
    private final KeystoreManager keystoreManager;
    private final Map<String, String> fingerprintMap = new HashMap<>();

    public PairingManager(Context context, KeystoreManager keystoreManager) {
        this.context = context;
        this.keystoreManager = keystoreManager;

        loadPairedDevices();
    }

    private void loadPairedDevices() {
        synchronized (fingerprintMap) {
            Log.d(TAG, "loading ssl fingerprint map");
            fingerprintMap.clear();

            SharedPreferences sp = context.getSharedPreferences(KEY_PAIRED_DEVICES, Context.MODE_PRIVATE);
            for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
                String deviceId = entry.getKey();

                if (entry.getValue() instanceof String fingerprint)
                    fingerprintMap.put(fingerprint, deviceId);
                else
                    Log.wtf(TAG, "non-string value in paired devices table for key: " + deviceId);
            }
        }
    }

    public boolean addNewDevice(Certificate certificate, PairingData data) throws IOException, CorruptedKeystoreException {
        synchronized (fingerprintMap) {
            String deviceId = UUID.randomUUID().toString();

            boolean committed = context.getSharedPreferences(KEY_PAIRED_DEVICES, Context.MODE_PRIVATE)
                    .edit()
                    .putString(deviceId, data.fingerprint())
                    .commit();

            if (!committed) {
                Log.wtf(TAG, "failed to write token to preference map");
                return false;
            }

            fingerprintMap.put(data.fingerprint(), deviceId);

            keystoreManager.addCertificate(deviceId, certificate);
            keystoreManager.saveKeystore();

            return writePairingData(deviceId, data);
        }

    }

    private boolean writePairingData(String deviceId, PairingData data) {
        return data.writeToPreferences(sharedPreferencesForDevice(deviceId).edit());
    }

    public PairingData fetchPairingData(String fingerprint) {
        String deviceId;
        synchronized (fingerprintMap) {
            deviceId = fingerprintMap.get(fingerprint);
            if (deviceId == null) return null;
        }

        PairingData data = PairingData.readFromPreferences(sharedPreferencesForDevice(deviceId));
        if (data != null && !fingerprint.equals(data.fingerprint())) {
            // this would best be solved with re-pairing
            Log.w(TAG, "fingerprint miss-match for entry: " + deviceId);
            return null;
        }
        return data;
    }

    private SharedPreferences sharedPreferencesForDevice(String deviceId) {
        String key = KEY_PREFIX_PAIRING_DATA + deviceId;
        Log.d(TAG, "loading " + key);
        return context.getSharedPreferences(key, Context.MODE_PRIVATE);
    }

    public boolean updatePairingData(PairingData pairingData) {
        String deviceId;
        synchronized (fingerprintMap) {
            deviceId = fingerprintMap.get(pairingData.fingerprint());
            if (deviceId == null) return false;
        }

        return writePairingData(deviceId, pairingData);
    }

}
