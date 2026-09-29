package tn.bricocomptoir.media.adapter.out.storage;

import java.net.URI;
import java.time.Duration;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tn.bricocomptoir.media.application.port.out.ObjectStorage;

/** S3 client supporting endpoint path prefixes, including Supabase Storage. */
@Component
@ConditionalOnProperty(name = "brico.media.provider", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage, AutoCloseable {
    private final S3Client client;
    private final String bucket;

    public S3ObjectStorage(@Value("${brico.media.endpoint}") String endpoint,
                           @Value("${brico.media.access-key}") String accessKey,
                           @Value("${brico.media.secret-key}") String secretKey,
                           @Value("${brico.media.bucket}") String bucket,
                           @Value("${brico.media.region}") String region) {
        URI uri = URI.create(endpoint);
        if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Invalid storage endpoint");
        if (region == null || region.isBlank() || bucket == null || bucket.isBlank())
            throw new IllegalArgumentException("Storage region and bucket are required");
        this.bucket = bucket;
        this.client = S3Client.builder()
                .endpointOverride(uri).region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false).build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                // Supabase does not implement all optional AWS checksum extensions.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(10)).socketTimeout(Duration.ofSeconds(30)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(60))
                        .apiCallAttemptTimeout(Duration.ofSeconds(30))
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(2).build()))
                .build();
    }

    @Override public void put(String key, byte[] bytes, String contentType) {
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType(contentType).cacheControl("private, no-store").build(), RequestBody.fromBytes(bytes));
        } catch (RuntimeException failure) { throw new IllegalStateException("Object storage write failed", failure); }
    }

    @Override public byte[] get(String key) {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        } catch (RuntimeException failure) { throw new IllegalStateException("Object storage read failed", failure); }
    }

    @Override public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException failure) { throw new IllegalStateException("Object storage cleanup failed", failure); }
    }

    @Override @PreDestroy public void close() { client.close(); }
}
