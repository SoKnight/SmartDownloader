package me.soknight.sandbox.downloader;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
public final class DownloadWatchdogService {

    private static final long COMPUTE_INTERVAL_THRESHOLD = 500L;
    private static final int SPEED_MARKS_HISTORY_SIZE = 64;

    private final ScheduledExecutorService scheduledAsyncExecutor;
    private final double[] speedMarks;
    private final Lock syncLock;

    private ScheduledFuture<?> taskFuture;

    private int speedMarksCursor;
    private long currentBytesReceived;
    private boolean firstSpeedMarksRound;
    private long lastMeasureAt;
    private long lastComputeAt;
    private double minAverageSpeed;
    private double lastAverageSpeed;
    private double maxAverageSpeed;

    public DownloadWatchdogService() {
        this.scheduledAsyncExecutor = Executors.newSingleThreadScheduledExecutor();
        this.speedMarks = new double[SPEED_MARKS_HISTORY_SIZE];
        this.syncLock = new ReentrantLock();
    }

    // [last] [min] [max]
    public double[] getAverageSpeedMbps() {
        try {
            syncLock.lock();
            return new double[] { lastAverageSpeed, minAverageSpeed, maxAverageSpeed };
        } finally {
            syncLock.unlock();
        }
    }

    public void onBytesReceived(long bytesReceived) {
        if (bytesReceived > 0L) {
            try {
                syncLock.lock();
                this.currentBytesReceived += bytesReceived;
            } finally {
                syncLock.unlock();
            }
        }
    }

    private void runWatchdog() {
        try {
            syncLock.lock();

            if (lastMeasureAt == 0L) {
                this.lastMeasureAt = lastComputeAt = System.currentTimeMillis();
                return;
            }

            double currentSpeed = measureSpeedMbps();
            this.lastMeasureAt = System.currentTimeMillis();
            this.currentBytesReceived = 0L;

            if (currentSpeed == 0D)
                return;

            if (speedMarksCursor >= SPEED_MARKS_HISTORY_SIZE) {
                this.speedMarksCursor %= SPEED_MARKS_HISTORY_SIZE;
                this.firstSpeedMarksRound = false;
            }

            this.speedMarks[speedMarksCursor++] = currentSpeed;

            if (lastMeasureAt - lastComputeAt >= COMPUTE_INTERVAL_THRESHOLD) {
                this.lastComputeAt = lastMeasureAt;
                this.lastAverageSpeed = computeAverageSpeed();

                if (lastAverageSpeed != 0L) {
                    this.minAverageSpeed = (minAverageSpeed > 0L) ? Math.min(minAverageSpeed, lastAverageSpeed) : lastAverageSpeed;
                    this.maxAverageSpeed = Math.max(maxAverageSpeed, lastAverageSpeed);
                }
            }
        } finally {
            syncLock.unlock();
        }
    }

    private double measureSpeedMbps() {
        if (currentBytesReceived == 0L)
            return 0D;

        double dKBytesReceived = Math.max(0D, currentBytesReceived / 1024D);
        if (dKBytesReceived == 0L)
            return 0D;

        double timeElapsed = Math.max(1D, System.currentTimeMillis() - lastMeasureAt) / 1000D;
        return Math.max(0D, (dKBytesReceived / 128D) / timeElapsed);
    }

    private double computeAverageSpeed() {
        int limit = firstSpeedMarksRound ? Math.min(speedMarksCursor, SPEED_MARKS_HISTORY_SIZE) : SPEED_MARKS_HISTORY_SIZE;
        return switch (limit) {
            case 0 -> 0D;
            case 1 -> speedMarks[0];
            default -> {
                double sum = 0D;
                for (int i = 0; i < limit; i++)
                    sum += speedMarks[i];
                yield sum / limit;
            }
        };
    }

    void start() {
        if (taskFuture != null && !taskFuture.isCancelled())
            return;

        try {
            syncLock.lock();
            this.speedMarksCursor = 0;
            this.firstSpeedMarksRound = true;
            this.lastMeasureAt = lastComputeAt = 0L;
            this.minAverageSpeed = lastAverageSpeed = maxAverageSpeed = 0D;
        } finally {
            syncLock.unlock();
        }

        this.taskFuture = scheduledAsyncExecutor.scheduleAtFixedRate(this::runWatchdog, 0L, 50L, MILLISECONDS);
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

}
