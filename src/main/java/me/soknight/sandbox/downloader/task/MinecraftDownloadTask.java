package me.soknight.sandbox.downloader.task;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.soknight.sandbox.downloader.DownloadService;
import me.soknight.sandbox.downloader.data.AssetIndex;
import me.soknight.sandbox.downloader.data.JavaRuntimeIndex;
import me.soknight.sandbox.downloader.data.ResourceModel;
import me.soknight.sandbox.downloader.library.LibraryFacade;
import me.soknight.sandbox.downloader.resource.ResourceDownloadBase;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

@Setter
@Accessors(chain = true)
@RequiredArgsConstructor
public final class MinecraftDownloadTask extends DownloadTaskBase {

    private final Path outputDir;

    private AssetIndex assetIndex;
    private ResourceModel clientDownload;
    private JavaRuntimeIndex javaRuntimeIndex;
    private Collection<LibraryFacade> libraryFacades;

    @Override
    protected void offerResourceDownloads(DownloadService service, Consumer<ResourceDownloadBase> downloadConsumer) {
        Comparator<ResourceDownloadBase> comparator = Comparator.comparingLong(ResourceDownloadBase::getExpectedSize).reversed()
                .thenComparing(ResourceDownloadBase::getDownloadId);

        Set<ResourceDownloadBase> downloads = new TreeSet<>(comparator);

        if (assetIndex != null) {
            var task = new AssetsDownloadTask(assetIndex, outputDir.resolve("assets"));
            task.offerResourceDownloads(service, downloads::add);
        }

        if (clientDownload != null) {
            var task = new SingleFileDownloadTask(clientDownload, outputDir, "client.jar");
            task.offerResourceDownloads(service, downloads::add);
        }

        if (javaRuntimeIndex != null) {
            var task = new JavaRuntimeDownloadTask(javaRuntimeIndex, outputDir.resolve("runtime"));
            task.offerResourceDownloads(service, downloads::add);
        }

        if (libraryFacades != null) {
            var task = new LibrariesDownloadTask(libraryFacades, outputDir.resolve("libraries"));
            task.offerResourceDownloads(service, downloads::add);
        }

        downloads.forEach(downloadConsumer);
    }

}
