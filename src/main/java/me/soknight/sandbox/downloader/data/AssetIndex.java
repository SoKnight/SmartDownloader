package me.soknight.sandbox.downloader.data;

import me.soknight.sandbox.downloader.DownloadService;
import me.soknight.sandbox.downloader.resource.DirectResourceDownload;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

public record AssetIndex(Map<String, Entry> objects) {

    public record Entry(
            String hash,
            long size
    ) {

        public DirectResourceDownload toResourceDownload(DownloadService service, Path outputDir, String name) {
            Path outputFile = outputDir.resolve(path().replace('/', File.separatorChar));
            return new DirectResourceDownload(service, url(), outputFile, name, size());
        }

        public String path() {
            return hash.substring(0, 2) + '/' + hash;
        }

        public String url() {
            return "https://resources.download.minecraft.net/" + path();
        }

    }

}
