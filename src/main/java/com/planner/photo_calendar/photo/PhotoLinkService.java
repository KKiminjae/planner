package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import com.planner.photo_calendar.auth.CurrentOwner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PhotoLinkService {
    private final CurrentOwner currentOwner;
    private final PhotoRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(Long recordId, String previousKey, String nextKey) {
        Long ownerId = currentOwner.id();
        if (Objects.equals(previousKey, nextKey)) {
            return;
        }
        // 기록 잠금 이후 사진 키 순서로 잠가 교차 교체의 잠금 순서를 통일합니다.
        List<String> keys = java.util.stream.Stream.of(previousKey, nextKey)
                .filter(Objects::nonNull).distinct().sorted().toList();
        Map<String, Photo> photos = new HashMap<>();
        for (String key : keys) {
            photos.put(key, repository.findByKeyForUpdate(key)
                    .filter(photo -> ownerId.equals(photo.getOwnerId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.PHOTO_NOT_FOUND)));
        }
        if (nextKey != null) {
            photos.get(nextKey).checkAvailable(recordId);
        }
        if (previousKey != null) {
            photos.get(previousKey).detach(recordId);
            // 한 기록에 한 사진만 연결하는 DB 유니크 제약을 교체 중에도 지킵니다.
            repository.flush();
        }
        if (nextKey != null) {
            photos.get(nextKey).attach(recordId);
        }
    }
}
