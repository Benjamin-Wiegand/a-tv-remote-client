package io.benwiegand.atvremote.phone.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.util.Log;

import androidx.annotation.StringRes;

import io.benwiegand.atvremote.phone.R;
import io.benwiegand.atvremote.phone.ui.RemoteActivity;

public class ConnectionNotificationManager implements ConnectionService.Callback {
    private static final String TAG = ConnectionNotificationManager.class.getSimpleName();
    private static final String BACKGROUND_CONNECTION_NOTIFICATION_CHANNEL = "connection";

    private final Object lock = new Object();

    private final Service context;

    private String deviceName = null;
    private @StringRes int statusText = R.string.connection_status_connection_lost;
    private boolean showing = false;

    public ConnectionNotificationManager(Service context) {
        this.context = context;
    }

    private void createNotificationChannel() {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = nm.getNotificationChannel(BACKGROUND_CONNECTION_NOTIFICATION_CHANNEL);
        if (channel == null)
            nm.createNotificationChannel(new NotificationChannel(BACKGROUND_CONNECTION_NOTIFICATION_CHANNEL, context.getString(R.string.background_connection_notification_channel_name), NotificationManager.IMPORTANCE_LOW));
    }

    private void updateNotificationLocked() {
        if (!showing) return;

        Intent intent = new Intent(context, RemoteActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(context, BACKGROUND_CONNECTION_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.tv)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentTitle(context.getString(statusText))
                .setSubText(deviceName)
                .build();

        try {
            context.startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } catch (Throwable t) {
            Log.e(TAG, "failed to start foreground context", t);
        }
    }

    private void updateNotification() {
        synchronized (lock) {
            updateNotificationLocked();
        }
    }

    public void startForegroundNotification() {
        createNotificationChannel();
        synchronized (lock) {
            showing = true;
            updateNotificationLocked();
        }
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
        updateNotification();
    }

    @Override
    public void onServiceInit() {}

    @Override
    public void onServiceInitError(Throwable t, boolean possiblyKeystoreInit) {}

    public void onConnecting() {
        statusText = R.string.connection_status_connecting;
        updateNotification();
    }

    @Override
    public void onSocketConnected() {
        statusText = R.string.connection_status_hand_shaking;
        updateNotification();
    }

    @Override
    public void onConnected(TVReceiverConnection connection) {
        statusText = R.string.connection_status_ready;
        updateNotification();
    }

    @Override
    public void onConnectError(Throwable t) {
        statusText = R.string.connection_status_connection_lost;
        updateNotification();
    }

    @Override
    public void onDisconnected(Throwable t) {
        statusText = R.string.connection_status_connection_lost;
        updateNotification();
    }
}
