package me.soknight.sandbox.downloader;

public final class DownloaderAppLauncher {

    public static void main(String[] args) {
        try (DownloaderApp app = new DownloaderApp()) {
            app.launch();
        }
    }

}
