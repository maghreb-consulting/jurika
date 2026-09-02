package ma.jurika.common.observability;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Readiness probe for MinIO / S3-compatible object storage. Pings the configured bucket
 * via {@link MinioClient#bucketExists(BucketExistsArgs)} (lightweight HEAD on the bucket).
 *
 * <p>Registered automatically by {@link ObservabilityAutoConfiguration} when a
 * {@code MinioClient} bean is present (dataroom-service, ticket-service). Bean name
 * is {@code minio} so it can be referenced in
 * {@code management.endpoint.health.group.readiness.include}.
 */
public class MinIOHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;
    private final String bucket;

    public MinIOHealthIndicator(MinioClient minioClient, String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public Health health() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (exists) {
                return Health.up().withDetail("bucket", bucket).build();
            }
            return Health.down()
                    .withDetail("bucket", bucket)
                    .withDetail("reason", "bucket-missing")
                    .build();
        } catch (Exception ex) {
            return Health.down(ex).withDetail("bucket", bucket).build();
        }
    }
}
