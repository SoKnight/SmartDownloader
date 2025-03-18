package me.soknight.sandbox.downloader.library;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.soknight.sandbox.downloader.data.Environment.OsType;
import me.soknight.sandbox.downloader.data.Library;
import me.soknight.sandbox.downloader.data.LibraryResourceModel;
import me.soknight.sandbox.downloader.data.MavenCoordinates;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LibraryMapper {

    public static Map<String, LibraryFacade> mapLibraryFacades(Collection<Library> libraries) {
        Map<String, LibraryFacade> facades = new TreeMap<>(String::compareToIgnoreCase);

        for (Library library : libraries) {
            if (!library.passRules()) {
                log.info("- Library '{}' skipped due to rules pass fail.", library.name());
                continue;
            }

            var coordinates = library.tryParseCoordinates().orElseThrow(() -> new IllegalArgumentException("Invalid library coordinates!"));
            var artifact = library.artifactDownload().orElse(null);
            String genericKey = coordinates.asGenericKey();
            facades.compute(genericKey, (key, facade) -> computeFacade(library, coordinates, artifact, key, facade));
        }

        return facades;
    }

    private static LibraryFacade computeFacade(
            Library library,
            MavenCoordinates coordinates,
            LibraryResourceModel artifact,
            String genericKey,
            LibraryFacade facade
    ) {
        return library.hasLegacyNatives()
                ? computeLegacyNativesFacade(library, artifact, genericKey, facade)
                : computeClassifiedFacade(library, coordinates, artifact, genericKey, facade);
    }

    private static LibraryFacade computeLegacyNativesFacade(
            Library library,
            LibraryResourceModel artifact,
            String genericKey,
            LibraryFacade facade
    ) {
        if (facade == null)
            facade = new LibraryFacade(genericKey);

        facade.useArtifact(artifact);

        Optional<String> nativeKey = library.findLegacyNativeKey(OsType.current());
        if (nativeKey.isPresent()) {
            Optional<LibraryResourceModel> nativeModel = library.findLegacyNative(nativeKey.get());
            if (nativeModel.isPresent()) {
                facade.addClassifier(nativeKey.get(), nativeModel.get());
            }
        }

        return facade;
    }

    private static LibraryFacade computeClassifiedFacade(
            Library library,
            MavenCoordinates coordinates,
            LibraryResourceModel artifact,
            String genericKey,
            LibraryFacade facade
    ) {
        if (facade == null)
            facade = new LibraryFacade(genericKey);

        if (coordinates.hasClassifier())
            facade.addClassifier(coordinates.classifier(), artifact);
        else
            facade.useArtifact(artifact);

        return facade;
    }

}
