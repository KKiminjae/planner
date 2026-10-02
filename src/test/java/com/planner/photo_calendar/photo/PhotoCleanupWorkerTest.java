package com.planner.photo_calendar.photo;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhotoCleanupWorkerTest {
    private final PhotoRepository repository = mock(PhotoRepository.class);
    private final PhotoCleanupTransactions transactions = mock(PhotoCleanupTransactions.class);
    private final PhotoStorage storage = mock(PhotoStorage.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.now(clock);
    private final PhotoCleanupWorker worker = new PhotoCleanupWorker(repository, transactions, storage, clock);

    @Test
    void 후보_선택_후_획득을_커밋하고_S3_삭제_이후_DB_삭제를_처리한다() {
        PhotoDeletionClaim claim = new PhotoDeletionClaim("photos/a.png", "token");
        when(repository.findDeletionCandidates(eq(now), eq(now.minusHours(24)), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable page = invocation.getArgument(2);
                    assertThat(page.getPageSize()).isEqualTo(100);
                    assertThat(page.getPageNumber()).isZero();
                    return List.of(claim.imageKey());
                });
        when(transactions.claim(claim.imageKey(), now, now.minusHours(24))).thenReturn(Optional.of(claim));
        worker.cleanup();
        org.mockito.InOrder order = inOrder(transactions, storage);
        order.verify(transactions).claim(claim.imageKey(), now, now.minusHours(24));
        order.verify(storage).delete(claim.imageKey());
        order.verify(transactions).complete(claim);
        verify(transactions, never()).retry(any(), any());
    }

    @Test
    void 다른_작업이_획득했거나_다시_연결된_사진은_S3에서_삭제하지_않는다() {
        when(repository.findDeletionCandidates(any(), any(), any())).thenReturn(List.of("photos/a.png"));
        when(transactions.claim(anyString(), any(), any())).thenReturn(Optional.empty());
        worker.cleanup();
        verifyNoInteractions(storage);
    }

    @Test
    void S3_실패는_재시도를_저장하고_다음_사진을_계속_처리한다() {
        PhotoDeletionClaim first = new PhotoDeletionClaim("photos/a.png", "first");
        PhotoDeletionClaim second = new PhotoDeletionClaim("photos/b.png", "second");
        when(repository.findDeletionCandidates(any(), any(), any())).thenReturn(List.of(first.imageKey(), second.imageKey()));
        when(transactions.claim(eq(first.imageKey()), any(), any())).thenReturn(Optional.of(first));
        when(transactions.claim(eq(second.imageKey()), any(), any())).thenReturn(Optional.of(second));
        doThrow(new IllegalStateException("S3 failure")).when(storage).delete(first.imageKey());
        worker.cleanup();
        verify(transactions).retry(first, now);
        verify(transactions, never()).complete(first);
        verify(transactions).complete(second);
    }

    @Test
    void S3_성공_후_DB_삭제_실패도_재시도_상태로_남긴다() {
        PhotoDeletionClaim claim = new PhotoDeletionClaim("photos/a.png", "token");
        when(repository.findDeletionCandidates(any(), any(), any())).thenReturn(List.of(claim.imageKey()));
        when(transactions.claim(anyString(), any(), any())).thenReturn(Optional.of(claim));
        doThrow(new IllegalStateException("DB failure")).when(transactions).complete(claim);
        worker.cleanup();
        verify(storage).delete(claim.imageKey());
        verify(transactions).retry(claim, now);
    }

    @Test
    void 재시도_정보_저장_실패나_획득_실패도_다른_사진_처리를_막지_않는다() {
        PhotoDeletionClaim claim = new PhotoDeletionClaim("photos/b.png", "token");
        PhotoDeletionClaim last = new PhotoDeletionClaim("photos/c.png", "last");
        when(repository.findDeletionCandidates(any(), any(), any())).thenReturn(List.of("photos/a.png", claim.imageKey(), last.imageKey()));
        when(transactions.claim(eq("photos/a.png"), any(), any())).thenThrow(new IllegalStateException("DB failure"));
        when(transactions.claim(eq(claim.imageKey()), any(), any())).thenReturn(Optional.of(claim));
        when(transactions.claim(eq(last.imageKey()), any(), any())).thenReturn(Optional.of(last));
        doThrow(new IllegalStateException("S3 failure")).when(storage).delete(claim.imageKey());
        doThrow(new IllegalStateException("DB failure")).when(transactions).retry(claim, now);
        worker.cleanup();
        verify(transactions).complete(last);
    }
}
