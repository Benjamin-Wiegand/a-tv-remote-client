package io.benwiegand.atvremote.phone.network;

import android.util.Log;

import java.io.Closeable;
import java.net.Socket;

public class SocketUtil {
    private static final String TAG = SocketUtil.class.getSimpleName();

    public static void tryClose(Socket socket) {
        if (socket.isClosed()) return;
        tryClose((Closeable) socket);
    }

    public static void tryClose(TVReceiverConnection connection) {
        if (connection.isDead()) return;
        tryClose((Closeable) connection);
    }

    public static void tryClose(EventJuggler eventJuggler) {
        if (eventJuggler.isDead()) return;
        tryClose((Closeable) eventJuggler);
    }

    public static void tryClose(TCPReader reader) {
        if (reader.isDead()) return;
        tryClose((Closeable) reader);
    }

    public static void tryClose(TCPWriter writer) {
        tryClose((Closeable) writer);
    }

    public static void tryClose(Closeable closeable) {
        String name = closeable.getClass().getSimpleName();
        try {
            closeable.close();
            Log.d(TAG, name + " closed: " + closeable);
        } catch (Throwable t) {
            Log.w(TAG, "failed to close " + name + ": " + closeable, t);
        }
    }

}
