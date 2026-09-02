package ma.jurika.dataroom.domain.port;

import java.io.InputStream;

public interface ObjectStorage {

    /**
     * Upload a file. The objectKey is the full path inside the bucket.
     */
    void upload(String objectKey, InputStream content, long size, String contentType);

    /**
     * Download a file as a stream. Caller must close the stream.
     */
    DownloadResult download(String objectKey);

    /**
     * Delete an object by key.
     */
    void delete(String objectKey);

    record DownloadResult(InputStream stream, long size, String contentType) {}
}
