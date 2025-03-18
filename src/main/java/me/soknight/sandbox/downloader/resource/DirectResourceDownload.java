package me.soknight.sandbox.downloader.resource;

import me.soknight.sandbox.downloader.DownloadService;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static me.soknight.sandbox.downloader.DownloadService.CHANNEL_OPEN_OPTIONS;

public final class DirectResourceDownload extends ResourceDownloadBase {

    private final Lock syncLock;
    private FileChannel outputChannel;

    public DirectResourceDownload(DownloadService service, String url, Path outputFile, String name) {
        this(service, url, outputFile, name, -1L);
    }

    public DirectResourceDownload(DownloadService service, String url, Path outputFile, String name, long expectedSize) {
        super(service, url, outputFile, name, expectedSize);
        this.syncLock = new ReentrantLock();
    }

    @Override
    protected long transferFrom(ReadableByteChannel source, long position, long count) throws IOException {
        //noinspection resource
        return outputChannel().transferFrom(source, position, count);
    }

    @Override
    public void close() throws Exception {
        if (outputChannel != null && outputChannel.isOpen()) {
            outputChannel.close();
        }
    }

    private FileChannel outputChannel() throws IOException {
        try {
            syncLock.lock();

            if (outputChannel == null) {
                Path outputFile = getOutputFile();
                Files.createDirectories(outputFile.getParent());
                this.outputChannel = FileChannel.open(outputFile, CHANNEL_OPEN_OPTIONS);
            }

            return outputChannel;
        } finally {
            syncLock.unlock();
        }
    }

}
