package me.soknight.sandbox.downloader.data;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import me.soknight.sandbox.downloader.exception.UnsupportedArchException;
import me.soknight.sandbox.downloader.exception.UnsupportedOSException;

import java.util.regex.PatternSyntaxException;

public record Environment(
        OsType osType,
        OsArch osArch,
        String version
) {

    public static final Environment CURRENT = obtainCurrentEnvironment();

    public boolean osTypeMatches(OsType osType) {
        return this.osType == osType;
    }

    public boolean osArchMatches(OsArch osArch) {
        return this.osArch == osArch;
    }

    public boolean versionMatches(String versionPattern) {
        try {
            return versionPattern != null && this.version.matches(versionPattern);
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static Environment obtainCurrentEnvironment() {
        return new Environment(
                OsType.current(),
                OsArch.current(),
                System.getProperty("os.version", "unknown")
        );
    }

    @AllArgsConstructor
    public enum OsType {

        LINUX   ("linux"),
        MAC_OS  ("osx"),
        WINDOWS ("windows"),
        ;

        @JsonValue
        private final String key;

        private static OsType CURRENT;

        public static OsType current() {
            if (CURRENT == null)
                CURRENT = detect();

            return CURRENT;
        }

        private static OsType detect() {
            String osName = System.getProperty("os.name");
            if (osName == null || osName.isEmpty())
                throw new UnsupportedOSException("<undefined>");

            String osLower = osName.toLowerCase();
            if (osLower.contains("win")) {
                return OsType.WINDOWS;
            } else if (osLower.contains("nix") || osLower.contains("nux") || osLower.contains("aix")) {
                return OsType.LINUX;
            } else if (osLower.contains("mac")) {
                return OsType.MAC_OS;
            } else {
                throw new UnsupportedOSException(osName);
            }
        }

    }

    @AllArgsConstructor
    public enum OsArch {

        X86     ("x86"),
        X86_64  ("x86_64"),
        ARM32   ("arm32"),
        AARCH64 ("arm64"),
        ;

        @JsonValue
        private final String key;

        private static OsArch CURRENT;

        public static OsArch current() {
            if (CURRENT == null)
                CURRENT = detect();

            return CURRENT;
        }

        private static OsArch detect() {
            String osArch = System.getProperty("os.arch");
            if (osArch == null || osArch.isEmpty())
                throw new UnsupportedArchException("<undefined>");

            return switch (osArch.toLowerCase()) {
                case "x86_64", "amd64" -> OsArch.X86_64;
                case "aarch64" -> OsArch.AARCH64;
                default -> throw new UnsupportedArchException(osArch);
            };
        }

    }

}
