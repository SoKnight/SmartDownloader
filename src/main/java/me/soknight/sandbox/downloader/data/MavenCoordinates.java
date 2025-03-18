package me.soknight.sandbox.downloader.data;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MavenCoordinates(
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String extension
) {

    private static final Pattern REG_EXP = Pattern.compile(
            "^(?<groupId>[^:@\\s]+):(?<artifactId>[^:@\\s]+):(?<version>[^:@\\s]+)" +
            "(?::(?<classifier>[^:@\\s]+))?(?:@(?<extension>[^:@\\s]+))?$"
    );

    public static Optional<MavenCoordinates> tryParse(String input) {
        if (input == null || input.isEmpty())
            return Optional.empty();

        Matcher matcher = REG_EXP.matcher(input);
        if (!matcher.matches())
            return Optional.empty();

        String groupId = matcher.group("groupId");
        String artifactId = matcher.group("artifactId");
        String version = matcher.group("version");
        String classifier = matcher.group("classifier");
        String extension = matcher.group("extension");

        return Optional.of(new MavenCoordinates(
                groupId, artifactId, version,
                (classifier != null && !classifier.isEmpty()) ? classifier : null,
                (extension != null && !extension.isEmpty()) ? extension : "jar"
        ));
    }

    public String asGenericKey() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public boolean hasClassifier() {
        return classifier != null && !classifier.isEmpty();
    }

    public String groupPath() {
        return groupId.replace('.', '/');
    }

    public String formatFileName() {
        StringBuilder fileNameBuilder = new StringBuilder();
        fileNameBuilder.append(artifactId).append('-').append(version);

        if (hasClassifier())
            fileNameBuilder.append('-').append(classifier);

        return fileNameBuilder.append('.').append(extension).toString();
    }

    public String formatFilePath() {
        return groupPath() + '/' + formatFileName();
    }

    public Path filePath() {
        return Paths.get(formatFilePath().replace('/', File.separatorChar));
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(groupId).append(':').append(artifactId).append(':').append(version);

        if (hasClassifier())
            builder.append(':').append(classifier);

        if (extension != null && !extension.equalsIgnoreCase("jar"))
            builder.append('@').append(extension);

        return builder.toString();
    }

}
