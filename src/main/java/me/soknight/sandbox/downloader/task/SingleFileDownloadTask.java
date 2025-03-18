package me.soknight.sandbox.downloader.task;

import lombok.RequiredArgsConstructor;
import me.soknight.sandbox.downloader.DownloadService;
import me.soknight.sandbox.downloader.data.ResourceModel;
import me.soknight.sandbox.downloader.resource.ResourceDownloadBase;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class SingleFileDownloadTask extends DownloadTaskBase {

    private final String url;
    private final Path outputDir;
    private final String name;
    private final long size;

    public SingleFileDownloadTask(ResourceModel model, Path outputDir, String name) {
        this(model.url(), outputDir, name, model.size());
    }

    @Override
    protected void offerResourceDownloads(DownloadService service, Consumer<ResourceDownloadBase> downloadConsumer) {
        Path outputFile = outputDir.resolve(name.replace('/', File.separatorChar));
        downloadConsumer.accept(service.directDownload(url, outputFile, name, size));
    }

}
