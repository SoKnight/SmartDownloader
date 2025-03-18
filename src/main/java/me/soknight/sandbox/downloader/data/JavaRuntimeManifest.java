package me.soknight.sandbox.downloader.data;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public final class JavaRuntimeManifest extends HashMap<String, JavaRuntimeManifest.RuntimeList> {

    public Optional<Runtime> findRuntime(String systemKey, String runtimeId) {
        RuntimeList runtimeList = get(systemKey);
        return runtimeList != null ? runtimeList.findRuntime(runtimeId) : Optional.empty();
    }

    public static final class RuntimeList extends HashMap<String, List<Runtime>> {

        public Optional<Runtime> findRuntime(String id) {
            List<Runtime> runtimes = get(id);
            return runtimes != null && !runtimes.isEmpty()
                    ? Optional.of(runtimes.getFirst())
                    : Optional.empty();
        }

    }

    public record Runtime(
            ResourceModel manifest
    ) { }

}
