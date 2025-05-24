package io.benwiegand.atvremote.phone.dummytv;

import static io.benwiegand.atvremote.phone.helper.TestUtil.catchAll;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import io.benwiegand.atvremote.phone.auth.ssl.KeyUtil;

public class FakeKeystoreManager {

    private static final String TEST_PRIVATE_KEY_B64 = "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";
    private static final String TEST_PUBLIC_KEY_B64 = "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA0NTRzpFPpEQzTe7leU4roK2eBb+OksToRuROzJ0/HcF5qPivCkHV9xYz5CaS72FcOOnZOgDBM0J2qzGCOz5Fuomdc1aQ0Y7YW5sxafr+KRkDPYDQ4Qv5SAajkFneRqI2DtAFC7/L7WbfC/NmHURNNTqp4O1Voi53CSRIj1/nqDdR2/vwoeZikyUzMWlYrXmc44xEAF91LdisSWFWMbGrzcd4wrbVRWVPbKLv/4sysXJgSJzpASJb46bwHIsFGcxl+hG9zOmH2IwRJiT/P3Bphn+OTfOYkp5xI7lYky60oJBXc11IRsXtTxO5NqCql065RvyWnYjB2MdeAJwSyPGVwGHxcwN9tgSmb6gHtVeNJAJuAdX+MuYI8XsUujCXv7pnGDDV5N3QRIxsJ9mBYwIbXVAQ8ATIyBB+sfOst73rp6+Bk4tB/H/6jysV8HbTP+tG8Bqj2Q05QB5L5ffWQq9d+qJjSKcfR0qQrvuPPe+dxEvHMxoJm5ZExYfmmvzPxXGCw+GJ/2igyknWAZLytksNlgLXLGmi9rzxDyS5Q7lnRolA3OK4f/tobGEg5JoXKxtPDwEcu99lZL5s0CLYyayuzzXWTQURryFkT9T9IU38eME7kKzjvpKS0M1ArBvek8SL0BX8PJBv3L44dqRlz3yy57fhhqv9fONUY0s6MCykPY8CAwEAAQ==";
    private static final String TEST_CERTIFICATE_B64 = "MIIEmTCCAoGgAwIBAgIDAKRVMA0GCSqGSIb3DQEBCwUAMA4xDDAKBgNVBAMMA0JvYjAgFw03MDAxMDEwMDAwMDBaGA81MTM4MTExNjA5NDYzOVowDjEMMAoGA1UEAwwDQm9iMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA0NTRzpFPpEQzTe7leU4roK2eBb+OksToRuROzJ0/HcF5qPivCkHV9xYz5CaS72FcOOnZOgDBM0J2qzGCOz5Fuomdc1aQ0Y7YW5sxafr+KRkDPYDQ4Qv5SAajkFneRqI2DtAFC7/L7WbfC/NmHURNNTqp4O1Voi53CSRIj1/nqDdR2/vwoeZikyUzMWlYrXmc44xEAF91LdisSWFWMbGrzcd4wrbVRWVPbKLv/4sysXJgSJzpASJb46bwHIsFGcxl+hG9zOmH2IwRJiT/P3Bphn+OTfOYkp5xI7lYky60oJBXc11IRsXtTxO5NqCql065RvyWnYjB2MdeAJwSyPGVwGHxcwN9tgSmb6gHtVeNJAJuAdX+MuYI8XsUujCXv7pnGDDV5N3QRIxsJ9mBYwIbXVAQ8ATIyBB+sfOst73rp6+Bk4tB/H/6jysV8HbTP+tG8Bqj2Q05QB5L5ffWQq9d+qJjSKcfR0qQrvuPPe+dxEvHMxoJm5ZExYfmmvzPxXGCw+GJ/2igyknWAZLytksNlgLXLGmi9rzxDyS5Q7lnRolA3OK4f/tobGEg5JoXKxtPDwEcu99lZL5s0CLYyayuzzXWTQURryFkT9T9IU38eME7kKzjvpKS0M1ArBvek8SL0BX8PJBv3L44dqRlz3yy57fhhqv9fONUY0s6MCykPY8CAwEAATANBgkqhkiG9w0BAQsFAAOCAgEAyMUBZz7zv05rCkU/pTNmUqa2P+bFu9N9I4SchJoZI5e4OMgngwIbR0c6QQrQaNUqFDdAYumAY23MSZ3TJgJW4IcY7ZDHKkILGSDQuttRTi0ctUDBJKl6VkOL3mjKZyVCs2B27DsMtbwLTVR0dmKwqqup4qTcqULAsJQV0oByULQGBGWL8IqtEvWWNwZEK7oAh+HJXWZCY+UeekuMTaaGDshJN10/ohVpavEvm/T9RNdopP7lA4HyokEbel5/G22TiyPlut8nNKjzniier71g132Putbe83beaW8I8BC7fBhTOBw028kgvnNPyu59HEUXJV5lUrm7pPt8zFCDcV/1Xo+GvoB7YQxHm0IuJXkOfiiwF4fXe7EmdYlTW1ga4SRla9IOfawTOBjGwl6pO8lqMpmbeLWTJHQn6wrRIhke6ZPgmTUQaeqCmQ4N4Yzska2e42LvQrTRw+sKe4VTpJKx2qxPMBAXg94+dDZKem5aJn5te6oRzE4epcXVC5hWjARJhR6v1bkV+Pu/FZhrM6WmbU09VgRVyEPE8N45XlAxVjf3hRfHjyoZ3kOiMabO5B2dMTk1b2Z4cxyg0rUz4HxmB7mT8E169oLckRbkiSWAUsfTHLvNGY5c/nfxaC/pKycDQr6zBfXjmnseHMCQDl2rQ7gDVRINbSYfR3JPDWE8Njg=";

    // echo '<the whole TEST_CERTIFICATE_B64>' | base64 -d | sha256sum -
    public static final String TEST_CERTIFICATE_FINGERPRINT = "b75621a1919645a56f9a51b7690f469a24f6c12404576af8ec1baf8a5345504d";

    private static final char[] PASSWORD = "hunter2".toCharArray();

    public static KeyPair getTestKeyPair() {
        return catchAll(() -> {
            KeyFactory kfactory = KeyFactory.getInstance("RSA");

            EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(Base64.decode(TEST_PRIVATE_KEY_B64, 0));
            EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(Base64.decode(TEST_PUBLIC_KEY_B64, 0));

            PrivateKey privKey = kfactory.generatePrivate(privKeySpec);
            PublicKey pubKey = kfactory.generatePublic(pubKeySpec);

            return new KeyPair(pubKey, privKey);
        });
    }

    public static Certificate getTestCert() {
        return catchAll(() -> {
            CertificateFactory cfactory = CertificateFactory.getInstance("X509");

            InputStream certStream = new ByteArrayInputStream(Base64.decode(TEST_CERTIFICATE_B64, 0));
            return cfactory.generateCertificate(certStream);
        });
    }

    public static byte[] getTestCertFingerprint() {
        return catchAll(() -> KeyUtil.calculateCertificateFingerprint(getTestCert()));
    }

    private KeyStore ks;

    public FakeKeystoreManager() {
        catchAll(() -> {
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, PASSWORD);

            // load the test keys
            KeyPair testKP = getTestKeyPair();
            Certificate cert = getTestCert();
            ks.setKeyEntry("test_key", testKP.getPrivate(), PASSWORD, new Certificate[]{cert});
        });
    }

    public TrustManager[] getTrustManagers() {
        return catchAll(() -> {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(ks);

            return tmf.getTrustManagers();
        });
    }

    public KeyManager[] getKeyManagers() {
        return catchAll(() -> {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
            kmf.init(ks, PASSWORD);

            return kmf.getKeyManagers();
        });
    }

}
