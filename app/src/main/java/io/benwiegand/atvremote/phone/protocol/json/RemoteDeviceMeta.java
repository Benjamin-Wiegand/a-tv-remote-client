package io.benwiegand.atvremote.phone.protocol.json;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.protocol.DeviceType;

public record RemoteDeviceMeta(
        int type,
        String friendlyName
) {
    private static final String TAG = RemoteDeviceMeta.class.getSimpleName();

    public static RemoteDeviceMeta getDeviceMeta(Context context) {
        return new RemoteDeviceMeta(DeviceType.PHONE.toInt(), findDeviceName(context));
    }

    private static String findDeviceName(Context context) {
        String hostname = Settings.Global.getString(context.getContentResolver(), "device_name");
        if (hostname != null) return hostname;

        Log.d(TAG, "no device_name, falling back to app name");
        return context.getString(R.string.app_name);
    }

}
