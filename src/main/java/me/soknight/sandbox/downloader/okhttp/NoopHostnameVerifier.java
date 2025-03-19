package me.soknight.sandbox.downloader.okhttp;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public final class NoopHostnameVerifier implements HostnameVerifier {

    @Override
    public boolean verify(String hostname, SSLSession session) {
        return true;
    }

}
