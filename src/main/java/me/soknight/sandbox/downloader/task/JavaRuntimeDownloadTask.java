package me.soknight.sandbox.downloader.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.soknight.sandbox.downloader.DownloadService;
import me.soknight.sandbox.downloader.data.JavaRuntimeIndex;
import me.soknight.sandbox.downloader.resource.ResourceDownloadBase;

import java.nio.file.Path;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public final class JavaRuntimeDownloadTask extends DownloadTaskBase {

    private final JavaRuntimeIndex javaRuntimeIndex;
    private final Path outputDir;

    @Override
    protected void offerResourceDownloads(DownloadService service, Consumer<ResourceDownloadBase> downloadConsumer) {
        javaRuntimeIndex.entries().forEach((path, entry) -> {
            if (!entry.isFile())
                return;

            var resourceDownload = entry.toResourceDownload(service, outputDir, path);
            if (resourceDownload != null) {
                downloadConsumer.accept(resourceDownload);
            } else {
                log.warn("Skipped Java runtime entry '{}': {}", path, entry);
            }
        });
    }

}
