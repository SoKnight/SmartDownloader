package me.soknight.sandbox.downloader.okhttp;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public final class NoopTrustManager implements X509TrustManager {

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        // nothing to do
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        // nothing to do
    }

}
