package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhotoReadServiceTest {
    private final PhotoRepository repository = mock(PhotoRepository.class);
    private final com.planner.photo_calendar.auth.CurrentOwner currentOwner = mock(com.planner.photo_calendar.auth.CurrentOwner.class);
    private final PhotoStorage storage = mock(PhotoStorage.class);
    private final PhotoReadService service = new PhotoReadService(currentOwner, repository, storage);

    @org.junit.jupiter.api.BeforeEach
    void 소유자를_설정한다() { when(currentOwner.id()).thenReturn(1L); }

    @Test
    void 기록에_연결된_사진키만_서명하고_조회_URL과_만료시각을_반환한다() {
        Photo photo = new Photo("photos/test.png", "image/png", 100);
        PhotoReadResponse response = new PhotoReadResponse("https://example.com/photo?signature=test",
                Instant.parse("2026-10-01T10:10:00Z"));
        when(repository.findReadableByRecordId(7L, 1L)).thenReturn(Optional.of(photo));
        when(storage.createReadUrl(photo.getImageKey())).thenReturn(response);
        assertThat(service.getUrl(7L)).isEqualTo(response);
        verify(storage).createReadUrl(photo.getImageKey());
    }

    @Test
    void 조회할_사진이_없으면_서명하지_않고_404를_반환한다() {
        when(repository.findReadableByRecordId(7L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUrl(7L)).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PHOTO_NOT_FOUND));
        verifyNoInteractions(storage);
    }

    @Test
    void 저장소의_서명_실패를_공통_오류로_전달한다() {
        Photo photo = new Photo("photos/test.png", "image/png", 100);
        when(repository.findReadableByRecordId(7L, 1L)).thenReturn(Optional.of(photo));
        when(storage.createReadUrl(photo.getImageKey())).thenThrow(new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE));
        assertThatThrownBy(() -> service.getUrl(7L)).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_STORAGE_UNAVAILABLE));
    }
}
