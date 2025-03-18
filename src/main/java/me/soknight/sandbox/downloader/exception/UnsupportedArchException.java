package me.soknight.sandbox.downloader.exception;

public final class UnsupportedArchException extends RuntimeException {

    public UnsupportedArchException(String arch) {
        super("Unsupported architecture detected: " + arch, null, false, false);
    }

}