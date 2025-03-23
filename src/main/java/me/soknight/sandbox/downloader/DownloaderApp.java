package me.soknight.sandbox.downloader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.soknight.sandbox.downloader.api.LauncherMetaAPI;
import me.soknight.sandbox.downloader.data.*;
import me.soknight.sandbox.downloader.data.VersionManifest.Version;
import me.soknight.sandbox.downloader.library.LibraryMapper;
import me.soknight.sandbox.downloader.task.MinecraftDownloadTask;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public final class DownloaderApp implements AutoCloseable {

    private final Path cacheRootDir;
    private final OkHttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final LauncherMetaAPI launcherMetaAPI;

    public DownloaderApp() {
        this.cacheRootDir = Paths.get("cache");
        this.httpClient = new OkHttpClient();
        this.jsonMapper = initializeJsonMapper();
        this.launcherMetaAPI = initializeRetrofit().create(LauncherMetaAPI.class);
    }

    @SneakyThrows
    void launch() {
        log.info("Requesting version list manifest...");
        VersionManifest versionManifest = performCall(launcherMetaAPI.getVersionListManifest());
        if (versionManifest == null)
            return;

        log.info("Loaded {} versions, latest: {}", versionManifest.versions().size(), versionManifest.latest());
        Map<String, Version> versions = versionManifest.versions().stream().collect(Collectors.toMap(Version::id, Function.identity()));

        Scanner scanner = new Scanner(System.in);
        scanner.useDelimiter("\n");

        String versionId;
        while (true) {
            System.out.print("Select version -> ");
            versionId = scanner.nextLine();
            if (!versions.containsKey(versionId)) {
                log.error("Version '{}' doesns't exist!", versionId);
            } else {
                break;
            }
        }

        scanner.close();

        log.info("Requesting Java runtime list manifest...");
        JavaRuntimeManifest javaRuntimeManifest = performCall(launcherMetaAPI.getJavaRuntimeListManifest());
        if (javaRuntimeManifest == null)
            return;

        String clientJsonUrl = versions.get(versionId).url();
        log.info("Requesting client.json for version '{}'...", versionId);
        ClientJson clientJson = performCall(launcherMetaAPI.getVersionManifest(clientJsonUrl));

        String assetIndexUrl = clientJson.assetIndex().url();
        log.info("Requesting assets index '{}'...", clientJson.assetIndex().id());
        AssetIndex assetIndex = performCall(launcherMetaAPI.getAssetIndex(assetIndexUrl));

        ResourceModel clientDownload = clientJson.clientDownload().orElseThrow();

        ClientJson.JavaVersion javaVersion = clientJson.javaVersion();
        JavaRuntimeManifest.Runtime runtime = javaRuntimeManifest.findRuntime("windows-x64", javaVersion.component()).orElseThrow();
        String javaRuntimeIndexUrl = runtime.manifest().url();

        log.info("Requesting manifest for Java runtime '{}'...", javaVersion.component());
        JavaRuntimeIndex javaRuntimeIndex = performCall(launcherMetaAPI.getJavaRuntimeManifest(javaRuntimeIndexUrl));

        log.info("Mapping libraries...");
        List<Library> libraries = clientJson.libraries();
        var libraryFacades = LibraryMapper.mapLibraryFacades(libraries);

        if (Files.isDirectory(cacheRootDir)) {
            log.info("Cleaning up cache directory...");
            try (Stream<Path> paths = Files.walk(cacheRootDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }

        double timeSpentSeconds, contentSizeKBytes;
        double[] avgSpeed;
        OptionalDouble avgLatency;

        try (
                var downloadService = new DownloadService();
                var scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        ) {
            MinecraftDownloadTask task = new MinecraftDownloadTask(cacheRootDir)
                    .setAssetIndex(assetIndex)
                    .setClientDownload(clientDownload)
                    .setJavaRuntimeIndex(javaRuntimeIndex)
                    .setLibraryFacades(libraryFacades.values());

            log.info("Downloading client distribution...");
            long start = System.currentTimeMillis();

            Random random = new Random();
            scheduledExecutor.scheduleAtFixedRate(() -> {
                double progress = task.computeProgress();
                if (progress <= 0D)
                    return;

                double lastAverageSpeed = downloadService.watchdogService().getAverageSpeedMbps()[0];
                if (lastAverageSpeed <= 0D)
                    return;

                log.info(
                        "[{}%] Downloaded: {} MB of {} MB (AVG speed: {} mbps), calls: {}R / {}Q",
                        "%3s".formatted("%.0f".formatted(progress * 100D)),
                        "%5s".formatted("%.1f".formatted(task.getReceivedBytes() / 1048576D)),
                        "%5s".formatted("%.1f".formatted(task.getExpectedBytes() / 1048576D)),
                        "%5s".formatted("%.1f".formatted(lastAverageSpeed)),
                        downloadService.dispatcher().runningCallsCount(),
                        downloadService.dispatcher().queuedCallsCount()
                );
            }, 500L, 500L, TimeUnit.MILLISECONDS);

            downloadService.performTask(task);

            timeSpentSeconds = (System.currentTimeMillis() - start) / 1000D;
            contentSizeKBytes = task.getExpectedBytes() / 1024D;
            avgSpeed = downloadService.watchdogService().getAverageSpeedMbps();
            avgLatency = task.getAverageLatency();
        }

        log.info("-----------------------------------------------------------------");
        log.info("Minecraft {} distribution download complete!", versionId);

        log.info("  Total size: {} MB", "%.1f".formatted(contentSizeKBytes / 1024D));
        log.info("  Time spent: {} second(s)", "%.0f".formatted(timeSpentSeconds));
        log.info("  Total average latency: {} ms", avgLatency.isPresent() ? "%.1f".formatted(avgLatency.getAsDouble()) : "<N/A>");
        log.info("  Total average speed: {} mbps", "%.1f".formatted(Math.max(0D, (contentSizeKBytes / 128D) / timeSpentSeconds)));
        log.info("  Min average speed: {} mbps", "%.1f".formatted(avgSpeed[1]));
        log.info("  Max average speed: {} mbps", "%.1f".formatted(avgSpeed[2]));
    }

    private <T> T performCall(Call<T> call) {
        try {
            Response<T> response = call.execute();
            if (response.isSuccessful())
                return response.body();

            try (ResponseBody body = response.errorBody()) {
                if (body != null)
                    log.error("Received status code '{}' ({}) with body: {}", response.code(), response.message(), body.string());
                else
                    log.error("Received status code '{}' ({}), no additional info provided.", response.code(), response.message());
                return null;
            }
        } catch (IOException ex) {
            log.error("Couldn't perform HTTP request ('{}')!", call.request().url(), ex);
            return null;
        }
    }

    private JsonMapper initializeJsonMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    private Retrofit initializeRetrofit() {
        return new Retrofit.Builder()
                .addConverterFactory(JacksonConverterFactory.create(jsonMapper))
                .baseUrl("https://launchermeta.mojang.com/")
                .client(httpClient)
                .build();
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

}
