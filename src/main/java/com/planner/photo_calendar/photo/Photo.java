package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;
import com.planner.photo_calendar.common.time.ApplicationTime;
import java.util.Objects;

@Entity
@Table(name = "photos")
@Getter
public class Photo {
    @Id
    @Column(name = "image_key", length = 500)
    private String imageKey;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(name = "content_type", length = 100)
    private String contentType;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;
    @Column(name = "unlinked_at")
    private LocalDateTime unlinkedAt;
    @Column(name = "delete_requested_at")
    private LocalDateTime deleteRequestedAt;
    @Column(name = "delete_attempts", nullable = false)
    private int deleteAttempts;
    @Column(name = "next_delete_attempt_at")
    private LocalDateTime nextDeleteAttemptAt;
    @Column(name = "deletion_token", length = 36)
    private String deletionToken;
    @Column(name = "record_id", unique = true)
    private Long recordId;

    protected Photo() { }

    public Photo(String imageKey, String contentType, long sizeBytes) {
        this.ownerId = 1L;
        this.imageKey = imageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = ApplicationTime.nowUtc();
        this.unlinkedAt = createdAt;
    }

    public Photo(Long ownerId, String imageKey, String contentType, long sizeBytes) {
        this(imageKey, contentType, sizeBytes);
        this.ownerId = ownerId;
    }

    public void markUploaded() {
        this.uploadedAt = ApplicationTime.nowUtc();
        this.unlinkedAt = uploadedAt;
    }

    public void checkAvailable(Long targetRecordId) {
        if (uploadedAt == null || deleteRequestedAt != null) {
            throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);
        }
        if (recordId != null && !Objects.equals(recordId, targetRecordId)) {
            throw new BusinessException(ErrorCode.PHOTO_ALREADY_LINKED);
        }
    }

    public void attach(Long recordId) {
        checkAvailable(recordId);
        this.recordId = Objects.requireNonNull(recordId);
        this.unlinkedAt = null;
    }

    public void detach(Long recordId) {
        if (!Objects.equals(this.recordId, recordId)) {
            throw new IllegalStateException("사진과 기록의 연결 정보가 일치하지 않습니다.");
        }
        this.recordId = null;
        this.unlinkedAt = ApplicationTime.nowUtc();
    }

    public boolean canClaimDeletion(LocalDateTime now, LocalDateTime cutoff) {
        if (recordId != null) {
            return false;
        }
        if (deleteRequestedAt != null) {
            return nextDeleteAttemptAt != null && !nextDeleteAttemptAt.isAfter(now);
        }
        LocalDateTime orphanedAt = unlinkedAt != null ? unlinkedAt
                : uploadedAt != null ? uploadedAt : createdAt;
        return !orphanedAt.isAfter(cutoff);
    }

    public void claimDeletion(LocalDateTime now, String token) {
        if (deleteRequestedAt == null) {
            deleteRequestedAt = now;
        }
        deletionToken = token;
        if (deleteAttempts < Integer.MAX_VALUE) {
            deleteAttempts++;
        }
        // 작업 중단 후에도 5분 뒤 재획득합니다. S3 호출 상한은 60초입니다.
        nextDeleteAttemptAt = now.plusMinutes(5);
    }

    public boolean ownsDeletion(String token) {
        return deleteRequestedAt != null && recordId == null && Objects.equals(deletionToken, token);
    }

    public void retryDeletion(LocalDateTime now) {
        long delayMinutes = Math.min(5L << Math.min(deleteAttempts - 1, 7), 360);
        nextDeleteAttemptAt = now.plusMinutes(delayMinutes);
        deletionToken = null;
    }

}
