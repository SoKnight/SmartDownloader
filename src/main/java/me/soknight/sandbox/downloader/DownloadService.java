package me.soknight.sandbox.downloader;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.soknight.sandbox.downloader.okhttp.NoopHostnameVerifier;
import me.soknight.sandbox.downloader.okhttp.NoopTrustManager;
import me.soknight.sandbox.downloader.resource.DirectResourceDownload;
import me.soknight.sandbox.downloader.resource.LzmaResourceDownload;
import me.soknight.sandbox.downloader.task.DownloadTaskBase;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Accessors(fluent = true)
public final class DownloadService implements AutoCloseable {

    public static final OpenOption[] CHANNEL_OPEN_OPTIONS = {
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
    };

    public static final long CHUNK_SIZE = 2L * 1024L * 1024L;
    public static final String USER_AGENT = "SmartDownloader/1.0";

    @Getter(AccessLevel.PACKAGE)
    private final Dispatcher dispatcher;
    private final OkHttpClient httpClient;

    @Getter private final DownloadWatchdogService watchdogService;
    @Getter private final DownloadOptimizerService optimizerService;
    private final Set<DownloadTaskBase> runningTasks;
    private final Lock tasksSyncLock;

    @Getter
    private final Path tempDir;

    public DownloadService() throws IOException {
        this.dispatcher = new Dispatcher(Executors.newVirtualThreadPerTaskExecutor());
        this.httpClient = createHttpClient();

        this.watchdogService = new DownloadWatchdogService();
        this.optimizerService = new DownloadOptimizerService(this);
        this.runningTasks = new HashSet<>();
        this.tasksSyncLock = new ReentrantLock();

        this.tempDir = Files.createTempDirectory("smart-downloader-");
    }

    public void performTask(DownloadTaskBase task) {
        try {
            tasksSyncLock.lock();
            runningTasks.add(task);
            watchdogService().start();
            optimizerService.start();
        } finally {
            tasksSyncLock.unlock();
        }

        try {
            task.processTask(this);
            task.join();
        } finally {
            try {
                tasksSyncLock.lock();
                if (runningTasks.remove(task) && runningTasks.isEmpty()) {
                    watchdogService().stop();
                    optimizerService.stop();
                }
            } finally {
                tasksSyncLock.unlock();
            }
        }
    }

    public void enqueue(Request request, Callback callback) {
        httpClient.newCall(request).enqueue(callback);
    }

    public DirectResourceDownload directDownload(String url, Path outputFile, String name) {
        return new DirectResourceDownload(this, url, outputFile, name);
    }

    public DirectResourceDownload directDownload(String url, Path outputFile, String name, long expectedSize) {
        return new DirectResourceDownload(this, url, outputFile, name, expectedSize);
    }

    public LzmaResourceDownload lzmaDownload(String url, Path outputFile, String name) {
        return new LzmaResourceDownload(this, url, outputFile, name);
    }

    public LzmaResourceDownload lzmaDownload(String url, Path outputFile, String name, long expectedSize) {
        return new LzmaResourceDownload(this, url, outputFile, name, expectedSize);
    }

    public long getChunkSize() {
        return CHUNK_SIZE;
    }

    public int getActiveConnectionsCount() {
        var pool = httpClient.connectionPool();
        return pool.connectionCount() - pool.idleConnectionCount();
    }

    @Override
    public void close() throws IOException {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();

        watchdogService.shutdown();
        optimizerService.shutdown();

        if (Files.isDirectory(tempDir)) {
            try (Stream<Path> paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    private OkHttpClient createHttpClient() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            NoopTrustManager noopTrustManager = new NoopTrustManager();
            sslContext.init(null, new TrustManager[] {noopTrustManager}, new SecureRandom());

            return new OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .hostnameVerifier(new NoopHostnameVerifier())
                    .sslSocketFactory(sslContext.getSocketFactory(), noopTrustManager)
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException ex) {
            throw new RuntimeException(ex);
        }
    }

}
