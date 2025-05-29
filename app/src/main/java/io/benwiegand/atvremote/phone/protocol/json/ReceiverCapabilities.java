package io.benwiegand.atvremote.phone.protocol.json;

import java.util.HashSet;

public record ReceiverCapabilities(HashSet<String> supportedFeatures) {

    // non-TV builds
    public static final String SUPPORTED_FEATURE_APP_SWITCHER = "APP_SWITCHER";
    public static final String SUPPORTED_FEATURE_QUICK_SETTINGS = "QUICK_SETTINGS";

    // google tv
    public static final String SUPPORTED_FEATURE_DASHBOARD_BUTTON = "DASHBOARD_BUTTON";

    // advanced inputs
    public static final String SUPPORTED_FEATURE_MEDIA_CONTROLS = "MEDIA_CONTROLS";
    public static final String SUPPORTED_FEATURE_MEDIA_SESSIONS = "MEDIA_SESSIONS";
}
