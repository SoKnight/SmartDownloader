package me.soknight.sandbox.downloader.data;

import me.soknight.sandbox.downloader.data.Environment.OsType;
import me.soknight.sandbox.downloader.data.Rule.Action;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public record Library(
        Downloads downloads,
        String name,
        String url,
        Map<OsType, String> natives,
        List<Rule> rules
) {

    public boolean hasLegacyNatives() {
        return natives != null && !natives.isEmpty() && downloads.hasClassifiers();
    }

    public Optional<String> findLegacyNativeKey(OsType osType) {
        return Optional.ofNullable(natives.get(osType));
    }

    public Optional<LibraryResourceModel> findLegacyNative(String key) {
        var classifiers = downloads.classifiers();
        if (classifiers == null || classifiers.isEmpty())
            return Optional.empty();

        return Optional.ofNullable(classifiers.get(key));
    }

    public Optional<MavenCoordinates> tryParseCoordinates() {
        return MavenCoordinates.tryParse(name);
    }

    public Optional<LibraryResourceModel> artifactDownload() {
        return downloads != null ? Optional.ofNullable(downloads.artifact()) : Optional.empty();
    }

    public boolean passRules() {
        return passRules(Environment.CURRENT);
    }

    public boolean passRules(Environment environment) {
        if (rules == null || rules.isEmpty())
            return true;

        Action defaultAction = rules.stream()
                .filter(Rule::isEmpty)
                .map(Rule::action)
                .findFirst().orElse(Action.DISALLOW);

        boolean defaultValue = defaultAction == Action.ALLOW;
        var iterator = rules.stream()
                .filter(Predicate.not(Rule::isEmpty))
                .iterator();

        if (!iterator.hasNext())
            return defaultValue;

        while (iterator.hasNext()) {
            if (iterator.next().isAllowed(environment, defaultValue)) {
                if (!defaultValue) {
                    return true;
                }
            } else {
                if (defaultValue) {
                    return false;
                }
            }
        }

        return defaultValue;
    }

    public record Downloads(
            LibraryResourceModel artifact,
            Map<String, LibraryResourceModel> classifiers
    ) {

        public boolean hasClassifiers() {
            return classifiers != null && !classifiers.isEmpty();
        }

    }

}
