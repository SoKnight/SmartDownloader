package me.soknight.sandbox.downloader.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Objects;

@Getter
@Accessors(fluent = true)
public class ResourceModel {

    protected final String sha1;
    protected final long size;
    protected final String url;

    @JsonCreator
    public ResourceModel(
            @JsonProperty("sha1") String sha1,
            @JsonProperty("size") long size,
            @JsonProperty("url") String url
    ) {
        this.sha1 = sha1;
        this.size = size;
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;

        ResourceModel that = (ResourceModel) o;
        return size == that.size && Objects.equals(sha1, that.sha1) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sha1, size, url);
    }

    @Override
    public String toString() {
        return "ResourceModel{" +
                "sha1='" + sha1 + '\'' +
                ", size=" + size +
                ", url='" + url + '\'' +
                '}';
    }

}
