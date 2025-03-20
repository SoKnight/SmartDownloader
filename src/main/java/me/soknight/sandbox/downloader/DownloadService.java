package me.soknight.sandbox.downloader;

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
import java.util.Collections;
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

    public static final long CHUNK_SIZE = 4L * 1024L * 1024L;
    public static final String USER_AGENT = "SmartDownloader/1.0";

    private final Dispatcher dispatcher;
    private final OkHttpClient httpClient;
    private final Lock dispatcherSyncLock;

    @Getter
    private final DownloadWatchdogService watchdogService;
    private final Set<DownloadTaskBase> runningTasks;
    private final Lock tasksSyncLock;

    @Getter
    private final Path tempDir;

    public DownloadService() throws IOException {
        this.dispatcher = createDispatcher();
        this.httpClient = createHttpClient();
        this.dispatcherSyncLock = new ReentrantLock();

        this.watchdogService = new DownloadWatchdogService();
        this.runningTasks = new HashSet<>();
        this.tasksSyncLock = new ReentrantLock();

        this.tempDir = Files.createTempDirectory("smart-downloader-");
    }

    public Set<DownloadTaskBase> getRunningTasks() {
        try {
            tasksSyncLock.lock();
            return runningTasks.isEmpty() ? Collections.emptySet() : Set.copyOf(runningTasks);
        } finally {
            tasksSyncLock.unlock();
        }
    }

    public void performTask(DownloadTaskBase task) {
        try {
            tasksSyncLock.lock();
            runningTasks.add(task);
            watchdogService().start();
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
                }
            } finally {
                tasksSyncLock.unlock();
            }
        }
    }

    public void configureMaxSimultaneousDownloads(int maxSimultaneousDownloads) {
        try {
            dispatcherSyncLock.lock();
            dispatcher.setMaxRequests(maxSimultaneousDownloads);
        } finally {
            dispatcherSyncLock.unlock();
        }
    }

    public void enqueue(Request request, Callback callback) {
        try {
            dispatcherSyncLock.lock();
            httpClient.newCall(request).enqueue(callback);
        } finally {
            dispatcherSyncLock.unlock();
        }
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

    @Override
    public void close() throws IOException {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();

        watchdogService.shutdown();

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

    private Dispatcher createDispatcher() {
        Dispatcher dispatcher = new Dispatcher(Executors.newVirtualThreadPerTaskExecutor());
        dispatcher.setMaxRequestsPerHost(35);
        return dispatcher;
    }

}
