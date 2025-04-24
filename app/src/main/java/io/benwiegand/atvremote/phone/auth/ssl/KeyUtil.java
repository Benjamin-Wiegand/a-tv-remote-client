package io.benwiegand.atvremote.phone.auth.ssl;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;

public class KeyUtil {
    private static final String TAG = KeyUtil.class.getSimpleName();

    private static MessageDigest getSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA256");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "no JDK support for SHA256", e);
            throw new UnsupportedOperationException("your device lacks SHA256 support", e);
        }
    }

    public static byte[] calculateCertificateFingerprint(Certificate cert) throws CorruptedKeystoreException {
        try {
            return getSha256Digest().digest(cert.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new CorruptedKeystoreException("certificate encoding is invalid", e);
        }
    }

    public static SecureRandom getSecureRandom() {
        try {
            return SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            Log.w(TAG, "can't create strong secure random!", e);
        }

        return new SecureRandom();
    }

    public static Certificate getRemoteCertificate(SSLSocket socket) {
        try {
            Certificate[] certs = socket.getSession().getPeerCertificates();
            if (certs.length == 0) {
                Log.e(TAG, "peer has no certs?");
                return null;
            }

            return certs[0];
        } catch (SSLPeerUnverifiedException e) {
            Log.e(TAG, "remote did not send a certificate", e);
            return null;
        }
    }

}
