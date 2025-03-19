package me.soknight.sandbox.downloader.resource;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.soknight.sandbox.downloader.DownloadService;
import me.soknight.sandbox.downloader.io.CountingByteChannel;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongConsumer;

@Slf4j
@Getter
public abstract class ResourceDownloadBase extends CompletableFuture<Path> implements AutoCloseable, Callable<Path>, Callback {

    private static final AtomicLong ID_COUNTER = new AtomicLong();

    private final DownloadService service;
    private final long downloadId;
    private final String name;
    private final long expectedSize;
    private final Path outputFile;

    @Getter(AccessLevel.NONE) private final Lock syncLock;
    @Getter(AccessLevel.NONE) private final Request.Builder requestBuilder;

    private boolean batchDataKnown;
    private int completedChunksCount;
    private int batchSize;
    private long totalSize;

    @Getter(AccessLevel.NONE)
    private LongConsumer bytesReceivedCallback;

    ResourceDownloadBase(DownloadService service, String url, Path outputFile, String name, long expectedSize) {
        this.service = service;
        this.downloadId = ID_COUNTER.incrementAndGet();
        this.name = name;
        this.expectedSize = expectedSize;
        this.outputFile = outputFile;

        this.syncLock = new ReentrantLock();
        this.requestBuilder = new Request.Builder()
                .header("User-Agent", DownloadService.USER_AGENT)
                .url(url);
    }

    protected abstract long transferFrom(ReadableByteChannel source, long position, long count) throws IOException;

    @Override
    public Path call() throws Exception {
        try {
            long rangeEnd = service.getChunkSize() - 1;
            enqueue(requestBuilder.header("Range", "bytes=0-" + rangeEnd).build());
            return join();
        } finally {
            close();
        }
    }

    @Override
    public void onResponse(Call call, Response response) throws IOException {
        try (response) {
            if (response.isSuccessful()) {
                handleSuccessfulResponse(call, response);
                return;
            }

            // 416 Range Not Satisfiable
            // try perform request again to download whole content
            if (response.code() == 416) {
                try {
                    syncLock.lock();
                    enqueue(requestBuilder.removeHeader("Range").build());
                    return;
                } finally {
                    syncLock.unlock();
                }
            }

            // TODO throw UnsuccessfulResponseException
            log.error("[{}] {}", response.code(), response.request().url());

            try {
                syncLock.lock();
                if (++completedChunksCount >= batchSize) {
                    complete(null);
                }
            } finally {
                syncLock.unlock();
            }
        }
    }

    @Override
    public void onFailure(Call call, IOException ex) {
        if (ex instanceof SocketTimeoutException) {
            log.info("- '{}': <timeout> ... retrying ...", name);
            retryRequest(call);
            return;
        }

        completeExceptionally(ex);
    }

    public void useBytesReceivedCallback(LongConsumer bytesReceivedCallback) {
        try {
            syncLock.lock();
            this.bytesReceivedCallback = bytesReceivedCallback;
        } finally {
            syncLock.unlock();
        }
    }

    public void onBytesReceived(long bytesReceived) {
        try {
            syncLock.lock();
            if (bytesReceivedCallback != null) {
                bytesReceivedCallback.accept(bytesReceived);
            }
        } finally {
            syncLock.unlock();
        }
    }

    private void handleSuccessfulResponse(Call call, Response response) throws IOException {
        // partial content
        if (response.code() == 206 && handlePartialContent(call, response))
            return;

        // --- full content
        try (CountingByteChannel channel = CountingByteChannel.wrap(response, this)) {
            this.totalSize = channel.contentLength();
            if (expectedSize > 0L && totalSize != expectedSize)
                log.warn("Resource download has incorrect size (expected: {}, actual: {})", expectedSize, totalSize);

            try {
                long transferred = transferFrom(channel, 0L, channel.contentLength());
                if (transferred != totalSize) {
                    log.warn("Transferred data has incorrect size (expected: {}, actual: {})", totalSize, transferred);
                }
            } catch (SocketTimeoutException ex) {
                log.info("- '{}': <timeout> ... retrying ...", name);
                retryRequest(call);
            } catch (IOException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IOException(ex);
            } finally {
                this.completedChunksCount = 1;
                complete(outputFile);
            }
        }
    }

    private boolean handlePartialContent(Call call, Response response) throws IOException {
        String contentRange = response.header("Content-Range");
        if (contentRange == null || contentRange.isEmpty()) {
            log.error("No Content-Range header found!");
            return false;
        }

        long[] rangeData = parseContentRange(contentRange);
        if (rangeData == null) {
            log.error("Invalid Content-Range header found: '{}'", contentRange);
            return false;
        }

        if (rangeData[0] == 0L && rangeData[2] >= rangeData[3])
            return false;

        try {
            syncLock.lock();
            if (!batchDataKnown) {
                initializeBatchData(rangeData);
                runBatchRequests();
            }
        } finally {
            syncLock.unlock();
        }

        downloadPartialContentChunk(call, response, rangeData);
        return true;
    }

    private void downloadPartialContentChunk(Call call, Response response, long[] rangeData) throws IOException {
        try (CountingByteChannel channel = CountingByteChannel.wrap(response, this)) {
            long transferred = transferFrom(channel, rangeData[0], rangeData[2]);
            if (transferred != rangeData[2]) {
                log.warn("Transferred data chunk has incorrect size (expected: {}, actual: {})", rangeData[2], transferred);
            }
        } catch (SocketTimeoutException ex) {
            log.info("- '{}': <timeout> ... retrying ...", name);
            retryRequest(call);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException(ex);
        } finally {
            try {
                syncLock.lock();
                if (++completedChunksCount >= batchSize) {
                    complete(outputFile);
                }
            } finally {
                syncLock.unlock();
            }
        }
    }

    private void runBatchRequests() {
        long chunkSize = service.getChunkSize();
        long from = chunkSize, offset = chunkSize - 1L;
        while (from < totalSize) {
            long to = Math.min(from + offset, totalSize - 1);
            enqueue(requestBuilder.header("Range", "bytes=" + from + "-" + to).build());
            from = to + 1;
        }
    }

    private void initializeBatchData(long[] rangeData) {
        this.totalSize = rangeData[3];
        if (expectedSize > 0L && totalSize != expectedSize)
            log.warn("Resource download has incorrect size (expected: {}, actual: {})", expectedSize, totalSize);

        long chunkSize = service.getChunkSize();
        this.batchSize = (int) (totalSize / chunkSize);
        if (totalSize % chunkSize != 0)
            this.batchSize++;

        this.completedChunksCount = 0;
        this.batchDataKnown = true;
    }

    // [from] [to] [length] [totalLength]
    private long[] parseContentRange(String input) {
        if (!input.startsWith("bytes "))
            return null;

        input = input.substring(6);
        int slashIndex = input.indexOf('/');
        int dashIndex = input.indexOf('-');
        if (slashIndex == -1 || dashIndex == -1)
            return null;

        long from = Long.parseLong(input.substring(0, dashIndex));
        long to = Long.parseLong(input.substring(dashIndex + 1, slashIndex));
        if (to < from)
            return null;

        long length = to - from + 1;
        long totalLength = Long.parseLong(input.substring(slashIndex + 1));
        return new long[] { from, to, length, totalLength };
    }

    private void retryRequest(Call call) {
        try {
            syncLock.lock();

            String rangeHeader = call.request().header("Range");
            if (rangeHeader != null) {
                requestBuilder.header("Range", rangeHeader);
            } else {
                requestBuilder.removeHeader("Range");
            }

            enqueue(requestBuilder.build());
        } finally {
            syncLock.unlock();
        }
    }

    private void enqueue(Request request) {
        service.enqueue(request, this);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResourceDownloadBase that = (ResourceDownloadBase) o;
        return downloadId == that.downloadId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(downloadId);
    }

}
