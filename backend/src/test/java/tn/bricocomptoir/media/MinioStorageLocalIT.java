package tn.bricocomptoir.media;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tn.bricocomptoir.media.adapter.out.storage.MinioObjectStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinioStorageLocalIT {
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
