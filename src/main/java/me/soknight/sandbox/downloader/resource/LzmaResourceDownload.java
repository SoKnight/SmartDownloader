package me.soknight.sandbox.downloader.resource;

import me.soknight.sandbox.downloader.DownloadService;
import org.tukaani.xz.LZMAInputStream;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class LzmaResourceDownload extends ResourceDownloadBase {

    private final Lock syncLock;
    private Path compressedFilePath;
    private FileChannel outputChannel;

    public LzmaResourceDownload(DownloadService service, String url, Path outputFile, String name) {
        this(service, url, outputFile, name, -1L);
    }

    public LzmaResourceDownload(DownloadService service, String url, Path outputFile, String name, long expectedSize) {
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
        if (outputChannel == null)
            return;

        if (outputChannel.isOpen())
            outputChannel.close();

        Path outputFile = getOutputFile();
        Files.createDirectories(outputFile.getParent());

        try (
                var input = new LZMAInputStream(Files.newInputStream(compressedFilePath));
                var output = Files.newOutputStream(outputFile, DownloadService.CHANNEL_OPEN_OPTIONS)
        ) {
            input.transferTo(output);
            output.flush();
        } finally {
            Files.deleteIfExists(compressedFilePath);
        }
    }

    private FileChannel outputChannel() throws IOException {
        try {
            syncLock.lock();

            if (outputChannel == null) {
                this.compressedFilePath = Files.createTempFile(getService().tempDir(), "lzma-", null);
                Files.createDirectories(compressedFilePath.toAbsolutePath().getParent());

                //noinspection resource
                RandomAccessFile file = new RandomAccessFile(compressedFilePath.toFile(), "rw");
                file.setLength(getTotalSize());
                this.outputChannel = file.getChannel();
            }

            return outputChannel;
        } finally {
            syncLock.unlock();
        }
    }

}
