package me.soknight.sandbox.downloader.exception;

public final class UnsupportedOSException extends RuntimeException {

    public UnsupportedOSException(String os) {
        super("Unsupported OS detected: " + os, null, false, false);
    }

}