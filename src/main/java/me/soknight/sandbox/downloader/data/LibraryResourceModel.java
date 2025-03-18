package me.soknight.sandbox.downloader.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Objects;

@Getter
@Accessors(fluent = true)
public final class LibraryResourceModel extends ResourceModel {

    private final String path;

    @JsonCreator
    public LibraryResourceModel(
            @JsonProperty("path") String path,
            @JsonProperty("sha1") String sha1,
            @JsonProperty("size") long size,
            @JsonProperty("url") String url
    ) {
        super(sha1, size, url);
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass() || !super.equals(o))
            return false;

        LibraryResourceModel that = (LibraryResourceModel) o;
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), path);
    }

    @Override
    public String toString() {
        return "LibraryResourceModel{" +
                "path='" + path + '\'' +
                ", sha1='" + sha1 + '\'' +
                ", url='" + url + '\'' +
                ", size=" + size +
                '}';
    }

}
