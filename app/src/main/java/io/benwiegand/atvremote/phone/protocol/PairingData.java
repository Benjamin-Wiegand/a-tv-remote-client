package io.benwiegand.atvremote.phone.protocol;

import android.content.SharedPreferences;

import java.time.Instant;

import io.benwiegand.atvremote.phone.util.ByteUtil;

public record PairingData(String token, String fingerprint, String friendlyName, String lastConnectedIpAddress, long lastConnectedTimestamp) {

    public PairingData(String token, byte[] fingerprint, String friendlyName, String lastConnectedIpAddress, long lastConnectedTimestamp) {
        this(token, ByteUtil.hexOf(fingerprint), friendlyName, lastConnectedIpAddress, lastConnectedTimestamp);
    }

    public Instant lastConnectedInstant() {
        if (lastConnectedTimestamp() < 0) return null;
        return Instant.ofEpochSecond(lastConnectedTimestamp());
    }

    // for shared preferences
    public static final String KEY_TOKEN = "token";
    public static final String KEY_FINGERPRINT = "fingerprint";
    public static final String KEY_FRIENDLY_NAME = "name";
    public static final String KEY_LAST_CONNECTED_IP_ADDRESS = "addr";
    public static final String KEY_LAST_CONNECTED_TIMESTAMP = "last_connected";

    public static PairingData readFromPreferences(SharedPreferences sp) {
        // token and fingerprint are required
        String token = sp.getString(KEY_TOKEN, null);
        String fingerprint = sp.getString(KEY_FINGERPRINT, null);
        if (token == null || fingerprint == null) return null;

        // there are better ways of doing this. reflection is one of them. last time I tried
        // reflection in an AOSP build it broke. this can always be replaced with a better solution.
        // if you are reading this and have a better solution, please contribute it I would really appreciate it.
        return new PairingData(
                token,
                fingerprint,
                sp.getString(KEY_FRIENDLY_NAME, null),
                sp.getString(KEY_LAST_CONNECTED_IP_ADDRESS, null),
                sp.getLong(KEY_LAST_CONNECTED_TIMESTAMP, -1)
        );
    }

    public boolean writeToPreferences(SharedPreferences.Editor spe) {
        return spe.clear()
                .putString(KEY_TOKEN, token())
                .putString(KEY_FINGERPRINT, fingerprint())
                .putString(KEY_FRIENDLY_NAME, friendlyName())
                .putString(KEY_LAST_CONNECTED_IP_ADDRESS, lastConnectedIpAddress())
                .putLong(KEY_LAST_CONNECTED_TIMESTAMP, lastConnectedTimestamp())
                .commit();
    }
}
