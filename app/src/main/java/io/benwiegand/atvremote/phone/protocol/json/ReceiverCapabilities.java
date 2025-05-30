package io.benwiegand.atvremote.phone.protocol.json;

import android.util.Log;

import java.util.HashSet;

public record ReceiverCapabilities(HashSet<String> supportedFeatures, HashSet<String> extraButtons) {
    private static final String TAG = ReceiverCapabilities.class.getSimpleName();

    public boolean hasFeature(String feature) {
        return supportedFeatures().contains(feature);
    }

    public boolean hasButton(String extraButton) {
        return extraButtons().contains(extraButton);
    }

    public static ReceiverCapabilities getDefault() {
        // this ideally shouldn't happen
        Log.w(TAG, "default capabilities requested");
        assert false;

        HashSet<String> features = new HashSet<>();

        // just assume all generic functions are supported
        features.add(SUPPORTED_FEATURE_APP_SWITCHER);
        features.add(SUPPORTED_FEATURE_QUICK_SETTINGS);
        features.add(SUPPORTED_FEATURE_MEDIA_CONTROLS);
        features.add(SUPPORTED_FEATURE_MEDIA_SESSIONS);
        features.add(SUPPORTED_FEATURE_MOUSE);

        return new ReceiverCapabilities(features, new HashSet<>());
    }

    // non-TV builds
    public static final String SUPPORTED_FEATURE_APP_SWITCHER = "APP_SWITCHER";
    public static final String SUPPORTED_FEATURE_QUICK_SETTINGS = "QUICK_SETTINGS";

    // advanced inputs
    public static final String SUPPORTED_FEATURE_MEDIA_CONTROLS = "MEDIA_CONTROLS";
    public static final String SUPPORTED_FEATURE_MEDIA_SESSIONS = "MEDIA_SESSIONS";
    public static final String SUPPORTED_FEATURE_MOUSE = "MOUSE";

    // google tv
    public static final String EXTRA_BUTTON_GTV_DASHBOARD = "DASHBOARD_BUTTON";

    // lineage os
    public static final String EXTRA_BUTTON_LINEAGE_SYSTEM_OPTIONS = "LINEAGE_SYSTEM_OPTIONS_BUTTON";
}
