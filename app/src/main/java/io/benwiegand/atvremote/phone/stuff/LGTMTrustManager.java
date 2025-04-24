package io.benwiegand.atvremote.phone.stuff;

import android.annotation.SuppressLint;
import android.util.Log;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.X509TrustManager;

// this trust manager always accepts.
// it's to be used during pairing so that the pairing can actually happen.
// otherwise, the connection won't open because the cert isn't trusted.

// in other words, an intentionally insecure trust manager for certs that aren't trusted *yet*.

@SuppressLint("CustomX509TrustManager") // see above
public class LGTMTrustManager implements X509TrustManager {
    private static final String TAG = LGTMTrustManager.class.getSimpleName();

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        Log.d(TAG, "got cert chain 4 client: " + Arrays.toString(chain));
        Log.d(TAG, "auth type: " + authType);
        Log.d(TAG, "LGTM");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        Log.d(TAG, "got cert chain 4 server: " + Arrays.toString(chain));
        Log.d(TAG, "auth type: " + authType);
        Log.d(TAG, "LGTM");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        Log.d(TAG, "accepted issuers");
        return new X509Certificate[0];
    }
}
