package com.namatdang.namatdang.media.storage;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Component
public class S3ImageStorage implements ImageStorage {

    private final String bucket;
    private final S3Client s3Client;

    @Autowired
    public S3ImageStorage(@Value("${media.images.s3.bucket}") String bucket,
                          @Value("${media.images.s3.region}") String region) {
        this(bucket, createClient(region));
    }

    S3ImageStorage(String bucket, S3Client s3Client) {
        if (!StringUtils.hasText(bucket)) {
            s3Client.close();
            throw new IllegalStateException("media.images.s3.bucket is required");
        }
        this.bucket = bucket.strip();
        this.s3Client = s3Client;
    }

    private static S3Client createClient(String region) {
        if (!StringUtils.hasText(region)) {
            throw new IllegalStateException("media.images.s3.region is required");
        }
        return S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                                               .apiCallAttemptTimeout(Duration.ofSeconds(5))
                                               .apiCallTimeout(Duration.ofSeconds(10))
                                               .build())
                .build();
    }

    @Override
    public void write(String key, String contentType, byte[] bytes) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .cacheControl("public, max-age=31536000, immutable")
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (SdkException exception) {
            throw new ImageStorageException("Failed to store image in S3", exception);
        }
    }

    @Override
    public Optional<byte[]> read(String key) {
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(response.asByteArray());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw new ImageStorageException("Failed to read image from S3", exception);
        } catch (SdkException exception) {
            throw new ImageStorageException("Failed to read image from S3", exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException exception) {
            throw new ImageStorageException("Failed to delete image from S3", exception);
        }
    }

    @PreDestroy
    public void close() {
        s3Client.close();
    }
}
