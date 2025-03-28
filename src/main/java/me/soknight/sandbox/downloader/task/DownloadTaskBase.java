package me.soknight.sandbox.downloader.task;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.soknight.sandbox.downloader.DownloadService;
import me.soknight.sandbox.downloader.resource.ResourceDownloadBase;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Slf4j
public abstract class DownloadTaskBase extends CompletableFuture<Void> {

    private static final AtomicLong ID_COUNTER = new AtomicLong();

    @Getter
    protected final long taskId;
    protected final AtomicLong receivedBytes;
    protected final AtomicLong expectedBytes;
    protected final AtomicLong latencyMarksSum;
    protected final AtomicInteger latencyMarksCount;

    public DownloadTaskBase() {
        this.taskId = ID_COUNTER.incrementAndGet();
        this.receivedBytes = new AtomicLong();
        this.expectedBytes = new AtomicLong();
        this.latencyMarksSum = new AtomicLong();
        this.latencyMarksCount = new AtomicInteger();
    }

    protected abstract void offerResourceDownloads(
            DownloadService service,
            Consumer<ResourceDownloadBase> downloadConsumer
    );

    public final void processTask(DownloadService service) {
        var optimizerService = service.optimizerService();
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            offerResourceDownloads(service, download -> {
                download.useLatencyCallback(latency -> {
                    latencyMarksSum.addAndGet(latency);
                    latencyMarksCount.incrementAndGet();
                    optimizerService.acceptLatencyMark(latency);
                });

                download.useBytesReceivedCallback(bytesReceived -> {
                    receivedBytes.addAndGet(bytesReceived);
                    service.watchdogService().onBytesReceived(bytesReceived);
                });

//                download.useRequestPassedCallback(optimizerService::onRequestPassed);
//                download.useRequestFailedCallback(optimizerService::onRequestFailed);

                long expectedSize = download.getExpectedSize();
                if (expectedSize > 0L) {
                    expectedBytes.addAndGet(expectedSize);
                }

                scope.fork(download);
            });

            try {
                scope.join().throwIfFailed();
            } catch (InterruptedException ignored) {
            } catch (ExecutionException ex) {
                completeExceptionally(ex);
                return;
            }

            complete(null);
        }
    }

    public final double computeProgress() {
        double expected = expectedBytes.get();
        if (expected <= 0L)
            return Double.POSITIVE_INFINITY;

        double received = Math.max(0D, receivedBytes.get());
        if (received > expected) {
            log.warn("Received {} byte(s), but expected {} byte(s)", received, expected);
            return 1D;
        }

        return Math.min(1D, received / expected);
    }

    public final long getReceivedBytes() {
        return receivedBytes.get();
    }

    public final long getExpectedBytes() {
        return expectedBytes.get();
    }

    public final OptionalDouble getAverageLatency() {
        long sum = latencyMarksSum.get();
        int count = latencyMarksCount.get();

        if (sum <= 0L || count <= 0)
            return OptionalDouble.empty();

        return OptionalDouble.of((double) sum / count);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DownloadTaskBase that = (DownloadTaskBase) o;
        return taskId == that.taskId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(taskId);
    }

}
