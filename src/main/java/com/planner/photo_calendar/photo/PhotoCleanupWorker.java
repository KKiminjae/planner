package com.planner.photo_calendar.photo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class PhotoCleanupWorker {
    private final PhotoRepository repository;
    private final PhotoCleanupTransactions transactions;
    private final PhotoStorage storage;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${photo.cleanup.interval-ms:300000}",
               initialDelayString = "${photo.cleanup.interval-ms:300000}")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minusHours(24);
        for (String key : repository.findDeletionCandidates(now, cutoff, PageRequest.of(0, 100))) {
            try {
                Optional<PhotoDeletionClaim> found = transactions.claim(key, LocalDateTime.now(clock), cutoff);
                if (found.isEmpty()) {
                    continue;
                }
                PhotoDeletionClaim claim = found.get();
                try {
                    // 외부 호출 전 삭제 표시를 커밋하며 S3 호출 중 DB 잠금을 유지하지 않습니다.
                    storage.delete(key);
                    transactions.complete(claim);
                } catch (RuntimeException exception) {
                    transactions.retry(claim, LocalDateTime.now(clock));
                    log.warn("사진 삭제 재시도 예정: key={}", key);
                }
            } catch (RuntimeException exception) {
                // DB 장애로 결과 저장에 실패해도 기존 획득 기한이 끝나면 다시 처리합니다.
                log.warn("사진 정리 처리 실패: key={}, type={}", key, exception.getClass().getSimpleName());
            }
        }
    }
}
