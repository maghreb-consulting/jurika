package ma.jurika.dataroom.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Value("${jurika.minio.endpoint}")
    private String endpoint;
    @Value("${jurika.minio.access-key}")
    private String accessKey;
    @Value("${jurika.minio.secret-key}")
    private String secretKey;
    @Value("${jurika.minio.bucket}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        // Bootstrap du bucket a la creation du bean, avec le client qu'on vient de construire.
        // Avant : @PostConstruct appelait minioClient() (methode @Bean proxifiee) pendant sa propre
        // initialisation -> « bean currently in creation », capte et avale en WARN -> le bucket
        // n'etait jamais cree sur un MinIO neuf, et l'indicateur de sante minio restait DOWN.
        ensureBucket(client);
        return client;
    }

    private void ensureBucket(MinioClient client) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket '{}' created", bucket);
            } else {
                log.info("MinIO bucket '{}' already present", bucket);
            }
        } catch (Exception ex) {
            log.warn("MinIO bucket bootstrap skipped: {}", ex.getMessage());
        }
    }

    public String getBucket() {
        return bucket;
    }
}
