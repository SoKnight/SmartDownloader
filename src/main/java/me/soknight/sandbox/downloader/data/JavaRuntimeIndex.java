package me.soknight.sandbox.downloader.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.soknight.sandbox.downloader.DownloadService;
import me.soknight.sandbox.downloader.resource.ResourceDownloadBase;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public record JavaRuntimeIndex(@JsonProperty("files") Map<String, Entry> entries) {

    public record Entry(
            Map<String, ResourceModel> downloads,
            boolean executable,
            String type
    ) {

        public ResourceDownloadBase toResourceDownload(DownloadService service, Path outputDir, String path) {
            Path filePath = outputDir.resolve(path.replace('/', File.separatorChar));

            var model = lzmaDownload();
            if (model.isPresent())
                return service.lzmaDownload(model.get().url(), filePath, path, model.get().size());

            model = rawDownload();
            if (model.isPresent())
                return service.directDownload(model.get().url(), filePath, path, model.get().size());

            return null;
        }

        public Optional<ResourceModel> lzmaDownload() {
            return Optional.ofNullable(downloads.get("lzma"));
        }

        public Optional<ResourceModel> rawDownload() {
            return Optional.ofNullable(downloads.get("raw"));
        }

        public boolean isDirectory() {
            return "directory".equalsIgnoreCase(type);
        }

        public boolean isFile() {
            return "file".equalsIgnoreCase(type);
        }

    }

}
