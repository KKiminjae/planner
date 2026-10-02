package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.record.DailyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PhotoCleanupTransactions {
    private final PhotoRepository repository;
    private final DailyRecordRepository records;

    public Optional<PhotoDeletionClaim> claim(String key, LocalDateTime now, LocalDateTime cutoff) {
        Optional<Photo> found = repository.findByKeyForUpdate(key);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Photo photo = found.get();
        if (!photo.canClaimDeletion(now, cutoff) || records.existsByImageKey(key)) {
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString();
        photo.claimDeletion(now, token);
        return Optional.of(new PhotoDeletionClaim(key, token));
    }

    public void complete(PhotoDeletionClaim claim) {
        repository.findByKeyForUpdate(claim.imageKey()).filter(photo -> photo.ownsDeletion(claim.token()))
                .ifPresent(repository::delete);
    }

    public void retry(PhotoDeletionClaim claim, LocalDateTime now) {
        repository.findByKeyForUpdate(claim.imageKey()).filter(photo -> photo.ownsDeletion(claim.token()))
                .ifPresent(photo -> photo.retryDeletion(now));
    }
}
