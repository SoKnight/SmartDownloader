package me.soknight.sandbox.downloader;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
public final class DownloadOptimizerService {

    private static final int MIN_MSD = 4, MAX_MSD = 1024;

    private final DownloadService downloadService;
    private final ScheduledExecutorService scheduledAsyncExecutor;
    private final Lock syncLock;

    private ScheduledFuture<?> taskFuture;

    private long latencyMarksSum;
    private int latencyMarksCount;

    public DownloadOptimizerService(DownloadService downloadService) {
        this.downloadService = downloadService;
        this.scheduledAsyncExecutor = Executors.newSingleThreadScheduledExecutor();
        this.syncLock = new ReentrantLock();
    }

    // TODO fails percentage
    private void runOptimizer() {
        try {
            syncLock.lock();

            if (latencyMarksCount < 3) {
                log.warn("[Optimizer] Skipping iteration (not enough latency data)");
                return;
            }

            double maxAvgSpeed = downloadService.watchdogService().getAverageSpeedMbps()[2];
            if (maxAvgSpeed <= 0D) {
                log.warn("[Optimizer] Skipping iteration (not enough network speed data)");
                return;
            }

            double totalAvgLatency = (double) latencyMarksSum / latencyMarksCount;
            this.latencyMarksSum = 0L;
            this.latencyMarksCount = 0;

            int rawMSD = calculateMSD(totalAvgLatency, maxAvgSpeed);
            int globalMSD = Math.min(MAX_MSD, Math.max(MIN_MSD, rawMSD));

            int activeConnectionsCount = Math.max(1, downloadService.getActiveConnectionsCount());
            int perHostMSD = (int) Math.ceil((double) globalMSD / activeConnectionsCount);

            downloadService.dispatcher().setMaxRequests(globalMSD);
            downloadService.dispatcher().setMaxRequestsPerHost(perHostMSD);

            log.info(
                    "[Optimizer] Updated MSD to {} globally and {} per host (raw MSD = {}, TAL = {}, MAS = {})",
                    globalMSD, perHostMSD, rawMSD, String.format("%.1f", totalAvgLatency), String.format("%.1f", maxAvgSpeed)
            );
        } finally {
            syncLock.unlock();
        }
    }

    public void acceptLatencyMark(long latency) {
        if (latency > 0L) {
            try {
                syncLock.lock();
                this.latencyMarksSum += latency;
                this.latencyMarksCount++;
            } finally {
                syncLock.unlock();
            }
        }
    }

    void start() {
        if (taskFuture != null && !taskFuture.isCancelled())
            return;

        try {
            syncLock.lock();
            this.latencyMarksSum = 0L;
            this.latencyMarksCount = 0;
        } finally {
            syncLock.unlock();
        }

        this.taskFuture = scheduledAsyncExecutor.scheduleAtFixedRate(this::runOptimizer, 10L, 5L, SECONDS);
    }

    void stop() {
        if (taskFuture == null || taskFuture.isCancelled())
            return;

        this.taskFuture.cancel(true);
        this.taskFuture = null;
    }

    void shutdown() {
        if (taskFuture != null) {
            taskFuture.cancel(true);
        }

        if (scheduledAsyncExecutor != null) {
            scheduledAsyncExecutor.shutdownNow();
        }
    }

    private static int calculateMSD(double totalAvgLatency, double maxAvgSpeed) {
        double adjustedTAL = Math.max(100D, totalAvgLatency / 2D);
        return (int) Math.round(8.7D * maxAvgSpeed / Math.pow(adjustedTAL, 0.25D));
    }

}
