package ma.jurika.common.observability;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MinIOHealthIndicatorTest {

    @Test
    void reportsUpWhenBucketExists() throws Exception {
        MinioClient client = Mockito.mock(MinioClient.class);
        Mockito.when(client.bucketExists(Mockito.any(BucketExistsArgs.class))).thenReturn(true);

        Health health = new MinIOHealthIndicator(client, "jurika-dataroom").health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("bucket", "jurika-dataroom");

        ArgumentCaptor<BucketExistsArgs> captor = ArgumentCaptor.forClass(BucketExistsArgs.class);
        Mockito.verify(client).bucketExists(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("jurika-dataroom");
    }

    @Test
    void reportsDownWhenBucketMissing() throws Exception {
        MinioClient client = Mockito.mock(MinioClient.class);
        Mockito.when(client.bucketExists(Mockito.any(BucketExistsArgs.class))).thenReturn(false);

        Health health = new MinIOHealthIndicator(client, "jurika").health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("bucket", "jurika")
                .containsEntry("reason", "bucket-missing");
    }

    @Test
    void reportsDownWhenMinioThrows() throws Exception {
        MinioClient client = Mockito.mock(MinioClient.class);
        Mockito.when(client.bucketExists(Mockito.any(BucketExistsArgs.class)))
                .thenThrow(new IOException("connection refused"));

        Health health = new MinIOHealthIndicator(client, "jurika").health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
