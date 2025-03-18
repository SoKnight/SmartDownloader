package me.soknight.sandbox.downloader.io;

import lombok.Getter;
import lombok.experimental.Accessors;
import me.soknight.sandbox.downloader.resource.ResourceDownloadBase;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

@Accessors(fluent = true)
public final class CountingByteChannel implements ReadableByteChannel {

    private final ReadableByteChannel delegate;
    private final ResourceDownloadBase boundDownload;
    @Getter private final long contentLength;
    @Getter private long bytesReceived;

    private CountingByteChannel(ReadableByteChannel delegate, ResourceDownloadBase boundDownload, long contentLength) {
        this.delegate = delegate;
        this.boundDownload = boundDownload;
        this.contentLength = contentLength;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int read = delegate.read(dst);

        if (boundDownload != null)
            boundDownload.onBytesReceived(read);

        this.bytesReceived += read;
        return read;
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    public static CountingByteChannel wrap(ReadableByteChannel channel, ResourceDownloadBase boundDownload, long contentLength) {
        return new CountingByteChannel(channel, boundDownload, contentLength);
    }

    public static CountingByteChannel wrap(ResponseBody responseBody, ResourceDownloadBase boundDownload) {
        return wrap(responseBody.source(), boundDownload, responseBody.contentLength());
    }

    public static CountingByteChannel wrap(Response response, ResourceDownloadBase boundDownload) {
        return wrap(response.body(), boundDownload);
    }

}
