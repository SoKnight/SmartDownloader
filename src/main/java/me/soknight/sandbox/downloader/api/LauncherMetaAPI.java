package me.soknight.sandbox.downloader.api;

import me.soknight.sandbox.downloader.data.*;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Url;

public interface LauncherMetaAPI {

    @GET("/mc/game/version_manifest_v2.json")
    Call<VersionManifest> getVersionListManifest();

    @GET("/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json")
    Call<JavaRuntimeManifest> getJavaRuntimeListManifest();

    @GET
    Call<ClientJson> getVersionManifest(@Url String url);

    @GET
    Call<AssetIndex> getAssetIndex(@Url String url);

    @GET
    Call<JavaRuntimeIndex> getJavaRuntimeManifest(@Url String url);

}
