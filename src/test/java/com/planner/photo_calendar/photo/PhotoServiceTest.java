package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhotoServiceTest {
    private final com.planner.photo_calendar.auth.CurrentOwner currentOwner = mock(com.planner.photo_calendar.auth.CurrentOwner.class);
    private final PhotoStorage storage = mock(PhotoStorage.class);
    private final PhotoRepository repository = mock(PhotoRepository.class);
    private final PhotoService service = new PhotoService(currentOwner, storage, repository);

    @BeforeEach
    void 메타데이터_저장은_전달된_사진을_반환한다() {
        when(currentOwner.id()).thenReturn(1L);
        when(repository.markUploadedIfAvailable(anyString(), any(java.time.LocalDateTime.class))).thenReturn(1);
    }

    @Test
    void 실제_PNG와_JPEG는_파일명과_선언된_타입_대신_내용을_검증하고_고유키로_저장한다() throws Exception {
        for (String format : new String[]{"png", "jpeg"}) {
            byte[] bytes = image(format);
            MockMultipartFile file = new MockMultipartFile("file", "../../wrong.txt", "text/plain", bytes);
            PhotoUploadResponse first = service.upload(file);
            PhotoUploadResponse second = service.upload(file);
            assertThat(first.imageKey()).matches("photos/[0-9a-f-]{36}\\." + format);
            assertThat(second.imageKey()).isNotEqualTo(first.imageKey());
            verify(storage).upload(first.imageKey(), bytes, "image/" + format);
            verify(storage).upload(second.imageKey(), bytes, "image/" + format);
        }
    }

    @Test
    void 빈_파일과_이미지로_위장한_텍스트는_저장하지_않는다() {
        for (byte[] bytes : new byte[][]{new byte[0], "not an image".getBytes()}) {
            assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "photo.png", "image/png", bytes)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE));
        }
        verifyNoInteractions(storage);
    }

    @Test
    void 파일이_정확히_20MB이면_저장한다() throws Exception {
        byte[] bytes = java.util.Arrays.copyOf(image("png"), 20 * 1024 * 1024);
        PhotoUploadResponse response = service.upload(new MockMultipartFile("file", bytes));
        verify(storage).upload(response.imageKey(), bytes, "image/png");
    }

    @Test
    void 파일이_20MB를_초과하면_저장하지_않는다() {
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", new byte[20 * 1024 * 1024 + 1])))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_TOO_LARGE));
        verifyNoInteractions(storage);
    }

    @Test
    void 지원하지_않는_GIF는_저장하지_않는다() throws Exception {
        byte[] bytes = image("gif");
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", bytes)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE));
        verifyNoInteractions(storage);
    }

    @Test
    void 아이폰_24MP_해상도_사진은_저장한다() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4284, 5712, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        byte[] bytes = output.toByteArray();
        PhotoUploadResponse response = service.upload(new MockMultipartFile("file", "iphone.jpg", "image/jpeg", bytes));
        verify(storage).upload(response.imageKey(), bytes, "image/jpeg");
    }

    @Test
    void 해상도가_5천만_화소를_초과하면_디코딩_전에_거부한다() throws Exception {
        byte[] bytes = image("png");
        java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(bytes);
        header.putInt(16, 10000);
        header.putInt(20, 5001);
        java.util.zip.CRC32 checksum = new java.util.zip.CRC32();
        checksum.update(bytes, 12, 17);
        header.putInt(29, (int) checksum.getValue());
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", bytes)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_TOO_LARGE));
        verifyNoInteractions(storage);
    }

    private byte[] image(String format) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }
    @Test
    void 메타데이터를_먼저_등록하고_S3_성공_후_업로드_완료를_저장한다() throws Exception {
        byte[] bytes = image("png");
        when(repository.saveAndFlush(any(Photo.class))).thenAnswer(invocation -> {
            Photo photo = invocation.getArgument(0);
            assertThat(photo.getCreatedAt()).isNotNull();
            assertThat(photo.getContentType()).isEqualTo("image/png");
            assertThat(photo.getSizeBytes()).isEqualTo((long) bytes.length);
            assertThat(photo.getRecordId()).isNull();
            return photo;
        });
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            verify(repository).saveAndFlush(argThat(photo -> photo.getImageKey().equals(key)
                    && photo.getUploadedAt() == null));
            return null;
        }).when(storage).upload(anyString(), any(byte[].class), anyString());
        PhotoUploadResponse response = service.upload(new MockMultipartFile("file", bytes));
        InOrder order = inOrder(repository, storage);
        order.verify(repository).saveAndFlush(any(Photo.class));
        order.verify(storage).upload(response.imageKey(), bytes, "image/png");
        order.verify(repository).markUploadedIfAvailable(eq(response.imageKey()), any(java.time.LocalDateTime.class));
    }

    @Test
    void S3_실패는_완료되지_않은_메타데이터만_남기고_키를_반환하지_않는다() throws Exception {
        doThrow(new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE))
                .when(storage).upload(anyString(), any(byte[].class), anyString());
        byte[] bytes = image("png");
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", bytes)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_STORAGE_UNAVAILABLE));
        verify(repository).saveAndFlush(argThat(photo -> photo.getUploadedAt() == null));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void 메타데이터_등록_실패시_S3에_전송하지_않는다() throws Exception {
        when(repository.saveAndFlush(any(Photo.class))).thenThrow(new IllegalStateException("DB unavailable"));
        byte[] bytes = image("png");
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", bytes)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void 삭제중이거나_사라진_사진은_완료_정보를_재생성하지_않고_업로드_실패로_반환한다() throws Exception {
        when(repository.markUploadedIfAvailable(anyString(), any(java.time.LocalDateTime.class))).thenReturn(0);
        byte[] bytes = image("png");
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", bytes)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_STORAGE_UNAVAILABLE));
        verify(repository).saveAndFlush(any(Photo.class));
        verify(repository).markUploadedIfAvailable(anyString(), any(java.time.LocalDateTime.class));
        verifyNoMoreInteractions(repository);
    }

}
