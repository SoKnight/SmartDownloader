package me.soknight.sandbox.downloader;

import lombok.extern.slf4j.Slf4j;
import me.soknight.sandbox.downloader.io.CountingByteChannel;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public final class Tests {

    public static final OpenOption[] CHANNEL_OPTIONS = {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};

    public static void main(String[] args) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder().build();

        Request request = new Request.Builder()
                .header("User-Agent", "SmartDownloader/1.0")
                .url("https://piston-data.mojang.com/v1/objects/a7e5a6024bfd3cd614625aa05629adf760020304/client.jar")
                .build();

        try (
                var response = client.newCall(request).execute();
                var output = FileChannel.open(Paths.get("client.jar"), CHANNEL_OPTIONS);
                var executorService = Executors.newSingleThreadScheduledExecutor()
        ) {
            long contentLength = response.body().contentLength();
            log.info("Downloading ({} bytes)...", contentLength);

            CountingByteChannel channel = CountingByteChannel.wrap(response, null);
            DownloadSpeedMeter speedMeter = new DownloadSpeedMeter(channel, contentLength);
            executorService.scheduleAtFixedRate(speedMeter, 100L, 100L, TimeUnit.MILLISECONDS);

            long start = System.nanoTime();
            output.transferFrom(channel, 0L, Long.MAX_VALUE);
            double timeTook = System.nanoTime() - start;
            double timeInSeconds = timeTook / 1000000000D;
            double speed = (contentLength / 131072D) / timeInSeconds;
            log.info("Download complete in {}s, avg speed: {}mbps", String.format("%.2f", timeInSeconds), String.format("%.2f", speed));
        }

        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private static final class DownloadSpeedMeter implements Runnable {

        private static final int SPEED_MARKS_HISTORY_SIZE = 25;

        private final CountingByteChannel channel;
        private final long contentLength;
        private final double[] speedMarks;
        private final Lock syncLock;

        private int speedMarksCursor;
        private long lastBytesReceived;
        private long lastMeasureAt;
        private long lastPrintAt;

        public DownloadSpeedMeter(CountingByteChannel channel, long contentLength) {
            this.channel = channel;
            this.contentLength = contentLength;
            this.speedMarks = new double[SPEED_MARKS_HISTORY_SIZE];
            this.syncLock = new ReentrantLock();
        }

        @Override
        public void run() {
            if (!channel.isOpen())
                return;

            if (lastMeasureAt == 0L) {
                this.lastMeasureAt = lastPrintAt = System.currentTimeMillis();
                return;
            }

            try {
                syncLock.lock();
                double currentSpeed = measureSpeedMbps();
                this.lastMeasureAt = System.currentTimeMillis();
                this.speedMarks[(speedMarksCursor++ % SPEED_MARKS_HISTORY_SIZE)] = currentSpeed;
                log.info("    {}", currentSpeed);

                if (lastMeasureAt - lastPrintAt > 500L) {
                    this.lastPrintAt = lastMeasureAt;
                    double progress = ((double) lastBytesReceived / contentLength) * 100D;
                    double avgSpeed = computeAverageSpeed();
                    log.info("  Downloaded: {} ({} mbps)", "%3s%%".formatted("%.0f".formatted(progress)), "%.2f".formatted(avgSpeed));
                }
            } finally {
                syncLock.unlock();
            }
        }

        public double getAverageSpeed() {
            try {
                syncLock.lock();
                return computeAverageSpeed();
            } finally {
                syncLock.unlock();
            }
        }

        private double measureSpeedMbps() {
            long bytesReceived = channel.bytesReceived();
            double dKBytesReceived = Math.max(0D, (bytesReceived - lastBytesReceived) / 1024D);
            this.lastBytesReceived = bytesReceived;

            double timeElapsed = (System.currentTimeMillis() - lastMeasureAt) / 1000D;
            return Math.max(0D, (dKBytesReceived / 128D) / timeElapsed);
        }

        private double computeAverageSpeed() {
            int limit = Math.min(speedMarksCursor, SPEED_MARKS_HISTORY_SIZE);
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

    }

}
