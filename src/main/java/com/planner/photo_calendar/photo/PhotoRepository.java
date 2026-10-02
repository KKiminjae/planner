package com.planner.photo_calendar.photo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface PhotoRepository extends JpaRepository<Photo, String> {
    @Query("""
            select p from Photo p, DailyRecord r
            where p.recordId = :recordId and r.id = p.recordId
              and r.imageKey = p.imageKey and p.uploadedAt is not null
              and r.category.deletedAt is null and p.deleteRequestedAt is null
              and p.ownerId = :ownerId and r.category.ownerId = :ownerId
            """)
    Optional<Photo> findReadableByRecordId(@Param("recordId") Long recordId, @Param("ownerId") Long ownerId);

    @Modifying
    @Transactional
    @Query("""
            update Photo p set p.uploadedAt = :now, p.unlinkedAt = :now
            where p.imageKey = :key and p.uploadedAt is null
              and p.recordId is null and p.deleteRequestedAt is null
            """)
    int markUploadedIfAvailable(@Param("key") String key, @Param("now") LocalDateTime now);

    @Query("""
            select p.imageKey from Photo p
            where p.recordId is null
              and not exists (select r.id from DailyRecord r where r.imageKey = p.imageKey)
              and ((p.deleteRequestedAt is null and coalesce(p.unlinkedAt, p.uploadedAt, p.createdAt) <= :cutoff)
                   or (p.deleteRequestedAt is not null and p.nextDeleteAttemptAt <= :now))
            order by p.createdAt, p.imageKey
            """)
    List<String> findDeletionCandidates(@Param("now") LocalDateTime now,
                                        @Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Photo p where p.imageKey = :key")
    Optional<Photo> findByKeyForUpdate(@Param("key") String key);
}
