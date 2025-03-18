package me.soknight.sandbox.downloader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class DownloaderAppLauncher {

    public static void main(String[] args) {
        if (args.length < 1) {
            log.error("Usage: java -jar smart-downloader-all.jar <max-simultaneous-downloads>");
            return;
        }

        int maxSimultaneousDownloads = Integer.parseInt(args[0]);
        try (DownloaderApp app = new DownloaderApp()) {
            app.launch(maxSimultaneousDownloads);
        }
    }

}
