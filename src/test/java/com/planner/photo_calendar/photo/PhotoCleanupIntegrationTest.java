package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.category.Category;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import com.planner.photo_calendar.record.DailyRecordRepository;
import com.planner.photo_calendar.record.DailyRecordService;
import com.planner.photo_calendar.record.dto.request.DailyRecordCreateRequest;
import com.planner.photo_calendar.record.dto.request.DailyRecordUpdateRequest;
import com.planner.photo_calendar.record.dto.response.DailyRecordResponse;
import com.planner.photo_calendar.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PhotoCleanupIntegrationTest extends MySqlIntegrationTest {
    @Autowired PhotoRepository photos;
    @Autowired PhotoCleanupTransactions transactions;
    @Autowired DailyRecordRepository records;
    @Autowired DailyRecordService service;
    @Autowired CategoryRepository categories;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private final PhotoStorage storage = mock(PhotoStorage.class);
    private final List<String> keys = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private final LocalDateTime now = LocalDateTime.now(Clock.systemUTC()).withNano(0);
    private final LocalTime time = LocalTime.of(12, 30);

    @AfterEach
    void 커밋된_테스트_데이터를_정리한다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            photos.deleteAllById(keys);
            photos.flush();
            for (Long categoryId : categoryIds) {
                records.deleteAll(records.findAllByCategoryId(categoryId));
            }
            records.flush();
            categories.deleteAllById(categoryIds);
        });
    }

    @Test
    void 미연결과_미완료_사진은_24시간_경계부터_정리하고_새_사진은_보존한다() {
        Photo old = photo(true, now.minusHours(25));
        Photo boundary = photo(true, now.minusHours(24));
        Photo pending = photo(false, now.minusHours(24));
        Photo recent = photo(true, now.minusHours(24).plusSeconds(1));
        Photo recentPending = photo(false, now.minusHours(23));
        worker(now).cleanup();
        for (Photo deleted : List.of(old, boundary, pending)) {
            verify(storage).delete(deleted.getImageKey());
            assertThat(photos.findById(deleted.getImageKey())).isEmpty();
        }
        assertThat(photos.findById(recent.getImageKey())).isPresent();
        assertThat(photos.findById(recentPending.getImageKey())).isPresent();
        verifyNoMoreInteractions(storage);
    }

    @Test
    void 오래된_사진도_연결중이거나_기록에서_참조중이면_삭제하지_않는다() {
        Photo linked = photo(true, now.minusHours(48));
        Photo inconsistent = photo(true, now.minusHours(48));
        create(category(), linked.getImageKey());
        create(category(), inconsistent.getImageKey());
        jdbc.update("UPDATE photos SET record_id = NULL, unlinked_at = ? WHERE image_key = ?",
                now.minusHours(48), inconsistent.getImageKey());
        worker(now).cleanup();
        assertThat(transactions.claim(inconsistent.getImageKey(), now, now.minusHours(24))).isEmpty();
        assertThat(photos.findById(linked.getImageKey())).isPresent();
        assertThat(photos.findById(inconsistent.getImageKey())).isPresent();
        verifyNoInteractions(storage);
    }

    @Test
    void 교체와_기록삭제는_해제시각부터_유예기간을_다시_계산한다() {
        Photo original = photo(true, now.minusHours(48));
        Photo next = photo(true, now);
        DailyRecordResponse record = create(category(), original.getImageKey());
        service.update(record.id(), new DailyRecordUpdateRequest(time, "교체", next.getImageKey(), false));
        LocalDateTime detachedAt = photos.findById(original.getImageKey()).orElseThrow().getUnlinkedAt();
        assertThat(detachedAt).isAfter(now.minusMinutes(1));
        worker(detachedAt.plusHours(24).minusSeconds(1)).cleanup();
        verifyNoInteractions(storage);
        worker(detachedAt.plusHours(24)).cleanup();
        verify(storage).delete(original.getImageKey());
        assertThat(photos.findById(next.getImageKey()).orElseThrow().getRecordId()).isEqualTo(record.id());

        service.delete(record.id());
        LocalDateTime deletedRecordAt = photos.findById(next.getImageKey()).orElseThrow().getUnlinkedAt();
        worker(deletedRecordAt.plusHours(24)).cleanup();
        verify(storage).delete(next.getImageKey());
        assertThat(photos.findById(next.getImageKey())).isEmpty();
    }

    @Test
    void S3_호출_전_삭제표시가_커밋되고_S3_호출중에는_DB_트랜잭션을_열지_않는다() {
        Photo photo = photo(true, now.minusHours(25));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Photo claimed = photos.findById(photo.getImageKey()).orElseThrow();
            assertThat(claimed.getDeleteRequestedAt()).isEqualTo(now);
            assertThat(claimed.getDeletionToken()).isNotNull();
            assertThat(claimed.getDeleteAttempts()).isEqualTo(1);
            assertError(() -> create(category(), photo.getImageKey()), ErrorCode.PHOTO_NOT_FOUND);
            return null;
        }).when(storage).delete(photo.getImageKey());
        worker(now).cleanup();
        assertThat(photos.findById(photo.getImageKey())).isEmpty();
    }

    @Test
    void 삭제_실패는_DB에_남고_작업객체_재생성_후_재시도에_성공한다() {
        Photo photo = photo(true, now.minusHours(25));
        doThrow(new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE))
                .doNothing().when(storage).delete(photo.getImageKey());
        worker(now).cleanup();
        Photo failed = photos.findById(photo.getImageKey()).orElseThrow();
        assertThat(failed.getDeleteRequestedAt()).isEqualTo(now);
        assertThat(failed.getDeleteAttempts()).isEqualTo(1);
        assertThat(failed.getNextDeleteAttemptAt()).isEqualTo(now.plusMinutes(5));
        assertThat(failed.getDeletionToken()).isNull();
        worker(now.plusMinutes(4)).cleanup();
        verify(storage).delete(photo.getImageKey());
        worker(now.plusMinutes(5)).cleanup();
        verify(storage, times(2)).delete(photo.getImageKey());
        assertThat(photos.findById(photo.getImageKey())).isEmpty();
    }

    @Test
    void 재시도_간격은_지수적으로_증가하고_6시간에서_상한을_유지한다() {
        Photo photo = photo(true, now.minusHours(25));
        LocalDateTime attemptAt = now;
        int[] delays = {5, 10, 20, 40, 80, 160, 320, 360, 360};
        for (int index = 0; index < delays.length; index++) {
            PhotoDeletionClaim claim = transactions.claim(photo.getImageKey(), attemptAt, attemptAt.minusHours(24)).orElseThrow();
            transactions.retry(claim, attemptAt);
            Photo failed = photos.findById(photo.getImageKey()).orElseThrow();
            assertThat(failed.getDeleteAttempts()).isEqualTo(index + 1);
            assertThat(failed.getNextDeleteAttemptAt()).isEqualTo(attemptAt.plusMinutes(delays[index]));
            attemptAt = failed.getNextDeleteAttemptAt();
        }
    }

    @Test
    void 작업_중단은_5분뒤_재획득하고_이전_작업자의_완료나_실패를_무시한다() {
        Photo photo = photo(true, now.minusHours(25));
        PhotoDeletionClaim first = transactions.claim(photo.getImageKey(), now, now.minusHours(24)).orElseThrow();
        assertThat(transactions.claim(photo.getImageKey(), now.plusMinutes(4), now.minusHours(24))).isEmpty();
        PhotoDeletionClaim second = transactions.claim(photo.getImageKey(), now.plusMinutes(5), now.minusHours(24)).orElseThrow();
        assertThat(second.token()).isNotEqualTo(first.token());
        transactions.complete(first);
        transactions.retry(first, now.plusMinutes(5));
        assertThat(photos.findById(photo.getImageKey()).orElseThrow().getDeletionToken()).isEqualTo(second.token());
        transactions.complete(second);
        assertThat(photos.findById(photo.getImageKey())).isEmpty();
    }

    @Test
    void 후보조회_이후_재연결되면_삭제_획득을_거부한다() {
        Photo photo = photo(true, now.minusHours(25));
        List<String> candidates = photos.findDeletionCandidates(now, now.minusHours(24),
                org.springframework.data.domain.PageRequest.of(0, 100));
        assertThat(candidates).contains(photo.getImageKey());
        DailyRecordResponse created = create(category(), photo.getImageKey());
        assertThat(transactions.claim(photo.getImageKey(), now, now.minusHours(24))).isEmpty();
        assertThat(photos.findById(photo.getImageKey()).orElseThrow().getRecordId()).isEqualTo(created.id());
    }

    @Test
    void 업로드_완료_조건부_갱신은_삭제중과_삭제된_사진을_되살리지_않는다() {
        Photo pending = photo(false, now.minusHours(25));
        PhotoDeletionClaim claim = transactions.claim(pending.getImageKey(), now, now.minusHours(24)).orElseThrow();
        assertThat(photos.markUploadedIfAvailable(pending.getImageKey(), now)).isZero();
        assertThat(photos.findById(pending.getImageKey()).orElseThrow().getUploadedAt()).isNull();
        transactions.complete(claim);
        assertThat(photos.markUploadedIfAvailable(pending.getImageKey(), now)).isZero();
        assertThat(photos.findById(pending.getImageKey())).isEmpty();

        Photo available = photo(false, now.minusHours(1));
        assertThat(photos.markUploadedIfAvailable(available.getImageKey(), now)).isEqualTo(1);
        Photo uploaded = photos.findById(available.getImageKey()).orElseThrow();
        assertThat(uploaded.getUploadedAt()).isEqualTo(now);
        assertThat(uploaded.getUnlinkedAt()).isEqualTo(now);
        assertThat(photos.markUploadedIfAvailable(available.getImageKey(), now)).isZero();
    }

    @Test
    void 삭제획득과_연결이_동시에_실행되어도_연결된_사진은_삭제되지_않는다() throws Exception {
        Photo photo = photo(true, now.minusHours(25));
        Category category = category();
        ExecutorService executor = new org.springframework.security.concurrent.DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(2));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Optional<PhotoDeletionClaim>> deletion = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("동시 실행 대기 초과"); }
                return transactions.claim(photo.getImageKey(), now, now.minusHours(24));
            });
            Future<Object> attachment = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("동시 실행 대기 초과"); }
                try { return create(category, photo.getImageKey()).id(); }
                catch (BusinessException exception) { return exception.getErrorCode(); }
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Optional<PhotoDeletionClaim> claimed = deletion.get(15, TimeUnit.SECONDS);
            Object result = attachment.get(15, TimeUnit.SECONDS);
            if (claimed.isPresent()) {
                assertThat(result).isEqualTo(ErrorCode.PHOTO_NOT_FOUND);
                assertThat(records.findAllByCategoryId(category.getId())).isEmpty();
                transactions.complete(claimed.get());
                assertThat(photos.findById(photo.getImageKey())).isEmpty();
            } else {
                assertThat(result).isInstanceOf(Long.class);
                assertThat(photos.findById(photo.getImageKey()).orElseThrow().getRecordId()).isEqualTo(result);
                worker(now).cleanup();
                verifyNoInteractions(storage);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private PhotoCleanupWorker worker(LocalDateTime at) {
        return new PhotoCleanupWorker(photos, transactions, storage, Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private Photo photo(boolean complete, LocalDateTime age) {
        Photo photo = new Photo("photos/" + UUID.randomUUID() + ".png", "image/png", 100);
        if (complete) { photo.markUploaded(); }
        keys.add(photo.getImageKey());
        photos.saveAndFlush(photo);
        jdbc.update("UPDATE photos SET created_at = ?, uploaded_at = ?, unlinked_at = ? WHERE image_key = ?",
                age, complete ? age : null, age, photo.getImageKey());
        return photos.findById(photo.getImageKey()).orElseThrow();
    }

    private Category category() {
        Category category = categories.saveAndFlush(new Category("정리 테스트", "#FF0000", 1, false));
        categoryIds.add(category.getId());
        return category;
    }

    private DailyRecordResponse create(Category category, String key) {
        return service.create(new DailyRecordCreateRequest(category.getId(), LocalDate.now(), time, "기록", key));
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
