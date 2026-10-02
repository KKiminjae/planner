package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import java.time.Instant;
import java.time.Duration;
import java.net.URI;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class S3PhotoStorageTest {
    private final S3Client client = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private final S3PhotoStorage storage = new S3PhotoStorage(client, presigner, "private-photo-bucket");

    @Test
    void 버킷과_키와_실제_타입을_지정해_공개_ACL_없이_업로드한다() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        storage.upload("photos/test.png", bytes, "image/png");
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("private-photo-bucket");
        assertThat(request.getValue().key()).isEqualTo("photos/test.png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().aclAsString()).isNull();
        try (java.io.InputStream input = body.getValue().contentStreamProvider().newStream()) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void SDK_실패는_상세정보를_노출하지_않는_저장소_오류로_변환한다() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("private credentials detail"));
        assertThatThrownBy(() -> storage.upload("photos/test.png", new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContain("private credentials detail");
                });
    }
    @Test
    void 비공개_사진에_10분_GET_URL과_SDK의_만료시각을_반환한다() throws Exception {
        Instant expiration = Instant.parse("2026-10-01T10:10:00Z");
        PresignedGetObjectRequest signed = mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(URI.create("https://example.com/photo?signature=test").toURL());
        when(signed.expiration()).thenReturn(expiration);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);
        PhotoReadResponse response = storage.createReadUrl("photos/test.png");
        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo("private-photo-bucket");
        assertThat(request.getValue().getObjectRequest().key()).isEqualTo("photos/test.png");
        assertThat(request.getValue().getObjectRequest().responseCacheControl()).isEqualTo("private, no-store");
        assertThat(response.imageUrl()).isEqualTo("https://example.com/photo?signature=test");
        assertThat(response.expiresAt()).isEqualTo(expiration);
        verifyNoInteractions(client);
    }

    @Test
    void 서명_실패는_자격증명_상세를_노출하지_않는_503_오류로_변환한다() {
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(SdkClientException.create("private credentials detail"));
        assertThatThrownBy(() -> storage.createReadUrl("photos/test.png"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContain("private credentials detail");
                });
    }

    @Test
    void 실제_Presigner도_AWS_접속_없이_서명된_GET_URL을_생성한다() {
        try (S3Presigner local = S3Presigner.builder()
                .region(software.amazon.awssdk.regions.Region.AP_NORTHEAST_2)
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build()) {
            Instant before = Instant.now();
            PhotoReadResponse response = new S3PhotoStorage(client, local, "private-photo-bucket")
                    .createReadUrl("photos/test.png");
            assertThat(response.imageUrl()).startsWith("https://private-photo-bucket.s3.ap-northeast-2.amazonaws.com/photos/test.png?")
                    .contains("X-Amz-Expires=600", "X-Amz-Signature=", "response-cache-control=");
            assertThat(response.expiresAt()).isBetween(before.plusSeconds(599), Instant.now().plusSeconds(601));
            verifyNoInteractions(client);
        }
    }

    @Test
    void 지정된_버킷과_키로_삭제하고_같은_키의_재시도도_허용한다() {
        storage.delete("photos/test.png");
        storage.delete("photos/test.png");
        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client, times(2)).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("private-photo-bucket");
        assertThat(request.getValue().key()).isEqualTo("photos/test.png");
        assertThat(request.getValue().versionId()).isNull();
        verifyNoInteractions(presigner);
    }

    @Test
    void 삭제_SDK_실패는_상세정보를_숨긴_저장소_오류로_변환한다() {
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("private credentials detail"));
        assertThatThrownBy(() -> storage.delete("photos/test.png"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContain("private credentials detail");
                });
    }

}
