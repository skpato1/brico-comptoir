package tn.bricocomptoir.media;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tn.bricocomptoir.media.adapter.out.storage.MinioObjectStorage;
import tn.bricocomptoir.media.adapter.out.storage.S3ObjectStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinioStorageLocalIT {
    @Test
    @EnabledIfEnvironmentVariable(named = "BRICO_LOCAL_MINIO_TEST", matches = "true")
    void s3AdapterRoundTripsBinaryPhotosThroughRealLocalMinio() {
        String bucket = "bricocomptoir-media-smoke";
        String key = "smoke/" + UUID.randomUUID() + ".jpg";
        var minio = new MinioObjectStorage(System.getenv("MEDIA_ENDPOINT"), System.getenv("MEDIA_ACCESS_KEY"),
                System.getenv("MEDIA_SECRET_KEY"), bucket);
        // The generic S3 adapter deliberately never provisions buckets.
        byte[] photo = {(byte) 0xff, (byte) 0xd8, 0, 42, (byte) 0xff, (byte) 0xd9};
        minio.put(key, photo, "image/jpeg");
        try (var storage = new S3ObjectStorage(System.getenv("MEDIA_ENDPOINT"), System.getenv("MEDIA_ACCESS_KEY"),
                System.getenv("MEDIA_SECRET_KEY"), bucket, "us-east-1")) {
            try {
                storage.put(key, photo, "image/jpeg");
                assertThat(storage.get(key)).isEqualTo(photo);
                assertThat(minio.get(key)).isEqualTo(photo);
            } finally { storage.delete(key); }
            assertThatThrownBy(() -> storage.get(key)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BRICO_LOCAL_MINIO_TEST", matches = "true")
    void roundTripsThroughRealLocalMinio() {
        var storage = new MinioObjectStorage(System.getenv("MEDIA_ENDPOINT"), System.getenv("MEDIA_ACCESS_KEY"),
                System.getenv("MEDIA_SECRET_KEY"), "bricocomptoir-media-smoke");
        String key = "smoke/" + UUID.randomUUID() + ".txt";
        byte[] source = "BricoComptoir MinIO local".getBytes(StandardCharsets.UTF_8);
        try {
            storage.put(key, source, "text/plain");
            assertThat(storage.get(key)).isEqualTo(source);
        } finally { storage.delete(key); }
        assertThatThrownBy(() -> storage.get(key)).isInstanceOf(IllegalStateException.class);
    }
}
