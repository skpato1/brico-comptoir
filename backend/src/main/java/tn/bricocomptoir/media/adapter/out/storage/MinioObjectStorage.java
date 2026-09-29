package tn.bricocomptoir.media.adapter.out.storage;

import java.io.ByteArrayInputStream;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.media.application.port.out.ObjectStorage;

@Component
@ConditionalOnProperty(name = "brico.media.provider", havingValue = "minio", matchIfMissing = true)
public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final String bucket;
    private volatile boolean ready;

    public MinioObjectStorage(@Value("${brico.media.endpoint}") String endpoint,
                              @Value("${brico.media.access-key}") String accessKey,
                              @Value("${brico.media.secret-key}") String secretKey,
                              @Value("${brico.media.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    private synchronized void ensureBucket() throws Exception {
        if (ready) return;
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        ready = true;
    }

    @Override public void put(String key, byte[] bytes, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1L).contentType(contentType).build());
        } catch (Exception failure) { throw new IllegalStateException("Object storage write failed", failure); }
    }

    @Override public byte[] get(String key) {
        try (var stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return stream.readAllBytes();
        } catch (Exception failure) { throw new IllegalStateException("Object storage read failed", failure); }
    }

    @Override public void delete(String key) {
        try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
        catch (Exception failure) { throw new IllegalStateException("Object storage cleanup failed", failure); }
    }
}
