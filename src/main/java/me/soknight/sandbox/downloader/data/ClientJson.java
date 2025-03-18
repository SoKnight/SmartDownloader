package me.soknight.sandbox.downloader.data;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ClientJson(
        AssetIndex assetIndex,
        Map<String, ResourceModel> downloads,
        String id,
        JavaVersion javaVersion,
        List<Library> libraries
) {

    public Optional<ResourceModel> clientDownload() {
        return Optional.ofNullable(downloads.get("client"));
    }

    public Optional<ResourceModel> clientMappingsDownload() {
        return Optional.ofNullable(downloads.get("client_mappings"));
    }

    public Optional<ResourceModel> serverDownload() {
        return Optional.ofNullable(downloads.get("server"));
    }

    public Optional<ResourceModel> serverMappingsDownload() {
        return Optional.ofNullable(downloads.get("server_mappings"));
    }

    public record AssetIndex(
            String id,
            String sha1,
            long size,
            long totalSize,
            String url
    ) { }

    public record JavaVersion(
            @JsonProperty("component") String component,
            @JsonProperty("majorVersion") int majorVersion
    ) { }

}
