package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "photo.storage.enabled", havingValue = "true")
public class S3PhotoStorage implements PhotoStorage {
    private static final Duration READ_URL_DURATION = Duration.ofMinutes(10);
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3PhotoStorage(S3Client client, S3Presigner presigner, @Value("${photo.storage.bucket}") String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public PhotoReadResponse createReadUrl(String key) {
        try {
            GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key)
                    .responseCacheControl("private, no-store").build();
            PresignedGetObjectRequest signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(READ_URL_DURATION).getObjectRequest(get).build());
            return new PhotoReadResponse(signed.url().toString(), signed.expiration());
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public void upload(String key, byte[] bytes, String contentType) {
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType(contentType).build(), RequestBody.fromBytes(bytes));
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }
}
