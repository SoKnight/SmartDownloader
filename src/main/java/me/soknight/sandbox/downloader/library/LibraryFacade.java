package me.soknight.sandbox.downloader.library;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import me.soknight.sandbox.downloader.DownloadService;
import me.soknight.sandbox.downloader.data.Library;
import me.soknight.sandbox.downloader.data.LibraryResourceModel;
import me.soknight.sandbox.downloader.resource.ResourceDownloadBase;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Accessors(fluent = true)
@RequiredArgsConstructor
public final class LibraryFacade {

    @Getter
    private final String genericKey;
    private LibraryResourceModel artifact;
    private Map<String, LibraryResourceModel> classifiers;

    public void toResourceDownloads(DownloadService service, Path outputDir, Consumer<ResourceDownloadBase> downloadConsumer) {
        if (artifact != null) {
            Path outputFile = outputDir.resolve(artifact.path().replace('/', File.separatorChar));
            downloadConsumer.accept(service.directDownload(artifact.url(), outputFile, artifact.path(), artifact.size()));
        }

        if (classifiers != null && !classifiers.isEmpty()) {
            classifiers.values().forEach(model -> {
                Path outputFile = outputDir.resolve(model.path().replace('/', File.separatorChar));
                downloadConsumer.accept(service.directDownload(model.url(), outputFile, model.path(), model.size()));
            });
        }
    }

    public Optional<LibraryResourceModel> artifact() {
        return Optional.ofNullable(artifact);
    }

    public void addClassifier(Library library) {
        var artifact = library.artifactDownload().orElseThrow(() -> new IllegalArgumentException("There is no artifact download!"));
        var coordinates = library.tryParseCoordinates().orElseThrow(() -> new IllegalArgumentException("Invalid library coordinates!"));
        addClassifier(coordinates.classifier(), artifact);
    }

    public void addClassifier(String classifier, LibraryResourceModel model) {
        if (classifiers == null)
            this.classifiers = new LinkedHashMap<>();

        classifiers.put(classifier, model);
    }

    public void useArtifact(LibraryResourceModel artifact) {
        if (artifact == null)
            return;

        this.artifact = artifact;
    }

    @Override
    public String toString() {
        if (classifiers != null && !classifiers.isEmpty()) {
            return String.format("%s (%s)", genericKey, String.join(", ", classifiers.keySet()));
        } else {
            return genericKey;
        }
    }

}
