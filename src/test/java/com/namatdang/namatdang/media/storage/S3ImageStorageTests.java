package com.namatdang.namatdang.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

class S3ImageStorageTests {

    private static final String BUCKET = "private-image-bucket";

    @Test
    void writesPrivateEncryptedObjectToConfiguredBucket() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        S3ImageStorage storage = new S3ImageStorage(BUCKET, s3Client);

        storage.write("images/stores/1/photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo("images/stores/1/photo.jpg");
        assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(request.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(request.getValue().cacheControl()).isEqualTo("public, max-age=31536000, immutable");
    }

    @Test
    void readsAndDeletesObjectFromConfiguredBucket() {
        S3Client s3Client = mock(S3Client.class);
        byte[] bytes = new byte[]{4, 5, 6};
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes));
        S3ImageStorage storage = new S3ImageStorage(BUCKET, s3Client);

        assertThat(storage.read("images/deals/2/photo.webp")).contains(bytes);
        storage.delete("images/deals/2/photo.webp");

        ArgumentCaptor<GetObjectRequest> getRequest = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(getRequest.capture());
        assertThat(getRequest.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(getRequest.getValue().key()).isEqualTo("images/deals/2/photo.webp");

        ArgumentCaptor<DeleteObjectRequest> deleteRequest = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteRequest.capture());
        assertThat(deleteRequest.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(deleteRequest.getValue().key()).isEqualTo("images/deals/2/photo.webp");
    }

    @Test
    void returnsEmptyWhenS3ObjectDoesNotExist() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("missing").build());
        S3ImageStorage storage = new S3ImageStorage(BUCKET, s3Client);

        assertThat(storage.read("images/stores/1/missing.jpg")).isEmpty();
    }

    @Test
    void wrapsAwsFailuresAsStorageFailures() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create("network unavailable"));
        S3ImageStorage storage = new S3ImageStorage(BUCKET, s3Client);

        assertThatThrownBy(() -> storage.read("images/stores/1/photo.jpg"))
                .isInstanceOf(ImageStorageException.class)
                .hasMessageContaining("S3");
    }

    @Test
    void rejectsMissingBucketConfiguration() {
        S3Client s3Client = mock(S3Client.class);

        assertThatThrownBy(() -> new S3ImageStorage(" ", s3Client))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucket");
        verify(s3Client).close();
    }

}
