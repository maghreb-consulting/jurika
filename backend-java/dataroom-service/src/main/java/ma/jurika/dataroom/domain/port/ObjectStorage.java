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

    /**
     * Lot L7 (E4) : cles de tous les objets dont la cle commence par {@code prefixe}
     * (suppression definitive d'un essai, RG-ESS-10).
     */
    java.util.List<String> lister(String prefixe);

    record DownloadResult(InputStream stream, long size, String contentType) {}
}
