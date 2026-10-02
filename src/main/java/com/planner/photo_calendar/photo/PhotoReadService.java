package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import com.planner.photo_calendar.auth.CurrentOwner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "photo.storage.enabled", havingValue = "true")
public class PhotoReadService {
    private final CurrentOwner currentOwner;
    private final PhotoRepository repository;
    private final PhotoStorage storage;

    @Transactional(readOnly = true)
    public PhotoReadResponse getUrl(Long recordId) {
        Photo photo = repository.findReadableByRecordId(recordId, currentOwner.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.PHOTO_NOT_FOUND));
        return storage.createReadUrl(photo.getImageKey());
    }
}
