package me.soknight.sandbox.downloader.data;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import me.soknight.sandbox.downloader.data.Environment.OsArch;
import me.soknight.sandbox.downloader.data.Environment.OsType;

import java.util.Map;

public record Rule(
        Action action,
        Map<String, Boolean> features,
        OsSpec os
) {

    public Action action() {
        return action != null ? action : Action.ALLOW;
    }

    public boolean isEmpty() {
        return features == null && os == null;
    }

    public boolean isAllowed(Environment environment, boolean defaultValue) {
        if (os != null) {
            if (os.pass(environment)) {
                return action != null ? action == Action.ALLOW : defaultValue;
            } else {
                return defaultValue;
            }
        }

        if (action != null)
            return action == Action.ALLOW;

        return defaultValue;
    }

    @AllArgsConstructor
    public enum Action {

        ALLOW       ("allow"),
        DISALLOW    ("disallow"),
        ;

        @JsonValue
        private final String key;

    }

    public record OsSpec(
            OsType name,
            OsArch arch,
            String version
    ) {

        public boolean pass(Environment environment) {
            if (name != null && !environment.osTypeMatches(name))
                return false;

            if (arch != null && !environment.osArchMatches(arch))
                return false;

            return version == null || environment.versionMatches(version);
        }

    }

}
