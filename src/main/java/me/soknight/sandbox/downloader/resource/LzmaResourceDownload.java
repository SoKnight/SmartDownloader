package me.soknight.sandbox.downloader.resource;

import me.soknight.sandbox.downloader.DownloadService;
import org.tukaani.xz.LZMAInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static me.soknight.sandbox.downloader.DownloadService.CHANNEL_OPEN_OPTIONS;

public final class LzmaResourceDownload extends ResourceDownloadBase {

    private final Lock syncLock;
    private ByteBuffer outputBuffer;

    public LzmaResourceDownload(DownloadService service, String url, Path outputFile, String name) {
        this(service, url, outputFile, name, -1L);
    }

    public LzmaResourceDownload(DownloadService service, String url, Path outputFile, String name, long expectedSize) {
        super(service, url, outputFile, name, expectedSize);
        this.syncLock = new ReentrantLock();
    }

    @Override
    protected long transferFrom(ReadableByteChannel source, long position, long count) throws IOException {
        try {
            syncLock.lock();

            if (outputBuffer == null)
                this.outputBuffer = ByteBuffer.allocateDirect(Math.toIntExact(getTotalSize()));

            InputStream sourceStream = Channels.newInputStream(source);
            byte[] buffer = new byte[8192];
            int outputPosition = Math.toIntExact(position);
            int bytesRead = 0;

            while (bytesRead < count) {
                int read = sourceStream.read(buffer);
                if (read <= 0)
                    break;

                outputBuffer.put(outputPosition + bytesRead, buffer, 0, read);
                bytesRead += read;
            }

            source.close();
            if (bytesRead != count)
                throw new IllegalStateException("Unexpected read count %d, expected %d".formatted(bytesRead, count));

            return bytesRead;
        } finally {
            syncLock.unlock();
        }
    }

    @Override
    public void close() throws Exception {
        if (outputBuffer != null) {
            ByteArrayInputStream compressedStream;
            if (outputBuffer.hasArray()) {
                compressedStream = new ByteArrayInputStream(outputBuffer.array(), outputBuffer.position(), outputBuffer.remaining());
            } else {
                byte[] byteArray = new byte[outputBuffer.remaining()];
                outputBuffer.get(byteArray);
                compressedStream = new ByteArrayInputStream(byteArray);
            }

            LZMAInputStream decompressedStream = new LZMAInputStream(compressedStream);

            Path outputFile = getOutputFile();
            Files.createDirectories(outputFile.getParent());

            try (var outputStream = Files.newOutputStream(outputFile, CHANNEL_OPEN_OPTIONS)) {
                decompressedStream.transferTo(outputStream);
            }
        }
    }

}
