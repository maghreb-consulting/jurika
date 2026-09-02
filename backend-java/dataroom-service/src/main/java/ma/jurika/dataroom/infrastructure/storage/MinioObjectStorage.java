package ma.jurika.dataroom.infrastructure.storage;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorage(MinioClient client, MinioConfig config) {
        this.client = client;
        this.bucket = config.getBucket();
    }

    @Override
    public void upload(String objectKey, InputStream content, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, size, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
        } catch (Exception ex) {
            throw new BusinessException("STORAGE_UPLOAD_FAILED", "Echec upload MinIO : " + ex.getMessage());
        }
    }

    @Override
    public DownloadResult download(String objectKey) {
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
            GetObjectResponse stream = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
            return new DownloadResult(stream, stat.size(), stat.contentType());
        } catch (Exception ex) {
            throw new BusinessException("STORAGE_DOWNLOAD_FAILED", "Echec download MinIO : " + ex.getMessage());
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            throw new BusinessException("STORAGE_DELETE_FAILED", "Echec delete MinIO : " + ex.getMessage());
        }
    }
}
