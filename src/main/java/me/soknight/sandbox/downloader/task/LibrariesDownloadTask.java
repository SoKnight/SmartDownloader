package me.soknight.sandbox.downloader.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.soknight.sandbox.downloader.DownloadService;
import me.soknight.sandbox.downloader.library.LibraryFacade;
import me.soknight.sandbox.downloader.resource.ResourceDownloadBase;

import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public final class LibrariesDownloadTask extends DownloadTaskBase {

    private final Collection<LibraryFacade> libraryFacades;
    private final Path outputDir;

    @Override
    protected void offerResourceDownloads(DownloadService service, Consumer<ResourceDownloadBase> downloadConsumer) {
        for (LibraryFacade facade : libraryFacades) {
            facade.toResourceDownloads(service, outputDir, downloadConsumer);
        }
    }

}
