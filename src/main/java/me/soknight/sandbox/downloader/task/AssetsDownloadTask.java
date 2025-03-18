package me.soknight.sandbox.downloader.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.soknight.sandbox.downloader.DownloadService;
import me.soknight.sandbox.downloader.data.AssetIndex;
import me.soknight.sandbox.downloader.resource.ResourceDownloadBase;

import java.nio.file.Path;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public final class AssetsDownloadTask extends DownloadTaskBase {

    private final AssetIndex assetIndex;
    private final Path outputDir;

    @Override
    protected void offerResourceDownloads(DownloadService service, Consumer<ResourceDownloadBase> downloadConsumer) {
        assetIndex.objects().forEach((path, asset) -> {
            var resourceDownload = asset.toResourceDownload(service, outputDir, path);
            if (resourceDownload != null) {
                downloadConsumer.accept(resourceDownload);
            } else {
                log.warn("Skipped asset '{}': {}", path, asset);
            }
        });
    }

}
