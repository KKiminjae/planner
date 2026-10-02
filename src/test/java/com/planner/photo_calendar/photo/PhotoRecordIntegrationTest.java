package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.category.Category;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import com.planner.photo_calendar.record.DailyRecord;
import com.planner.photo_calendar.record.DailyRecordRepository;
import com.planner.photo_calendar.record.DailyRecordService;
import com.planner.photo_calendar.record.dto.request.DailyRecordCreateRequest;
import com.planner.photo_calendar.record.dto.request.DailyRecordUpdateRequest;
import com.planner.photo_calendar.record.dto.response.DailyRecordResponse;
import com.planner.photo_calendar.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

// 서비스가 실제로 커밋/롤백되도록 테스트 전체 트랜잭션을 사용하지 않습니다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PhotoRecordIntegrationTest extends MySqlIntegrationTest {
    @Autowired PhotoRepository photos;
    @Autowired DailyRecordRepository records;
    @Autowired CategoryRepository categories;
    @Autowired DailyRecordService service;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private final List<Long> categoryIds = new ArrayList<>();
    private final List<String> photoKeys = new ArrayList<>();
    private final LocalTime time = LocalTime.of(12, 30);

    @AfterEach
    void 커밋된_테스트_데이터를_정리한다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            photos.deleteAllById(photoKeys);
            photos.flush();
            for (Long categoryId : categoryIds) {
                records.deleteAll(records.findAllByCategoryId(categoryId));
            }
            records.flush();
            categories.deleteAllById(categoryIds);
        });
    }

    @Test
    void 사진을_연결한_기록은_단건_일별_월별_조회에서_같은_키를_반환한다() {
        Category category = category();
        Photo photo = photo(true);
        DailyRecordResponse created = create(category, photo.getImageKey());

        assertThat(created.imageKey()).isEqualTo(photo.getImageKey());
        assertThat(photos.findById(photo.getImageKey()).orElseThrow().getRecordId()).isEqualTo(created.id());
        assertThat(service.getRecord(created.id()).imageKey()).isEqualTo(photo.getImageKey());
        assertThat(service.getDailyRecords(LocalDate.now())).filteredOn(record -> record.id().equals(created.id()))
                .extracting(DailyRecordResponse::imageKey).containsExactly(photo.getImageKey());
        assertThat(service.getMonthlyRecords(category.getId(), LocalDate.now().getYear(), LocalDate.now().getMonthValue()))
                .singleElement().satisfies(record -> assertThat(record.imageKey()).isEqualTo(photo.getImageKey()));
    }

    @Test
    void 사진_없는_생성과_사진키_생략_null_동일키_수정은_기존_동작을_유지한다() {
        DailyRecordResponse withoutPhoto = create(category(), null);
        assertThat(withoutPhoto.imageKey()).isNull();
        Photo photo = photo(true);
        DailyRecordResponse created = create(category(), photo.getImageKey());

        assertThat(service.update(created.id(), new DailyRecordUpdateRequest(time, "생략")).imageKey())
                .isEqualTo(photo.getImageKey());
        assertThat(service.update(created.id(), new DailyRecordUpdateRequest(time, "null", null, false)).imageKey())
                .isEqualTo(photo.getImageKey());
        assertThat(service.update(created.id(), new DailyRecordUpdateRequest(time, "동일", photo.getImageKey(), null)).imageKey())
                .isEqualTo(photo.getImageKey());
        assertThat(photos.findById(photo.getImageKey()).orElseThrow().getRecordId()).isEqualTo(created.id());
    }

    @Test
    void 사진을_교체하면_이전_연결을_해제하고_새_사진을_연결한다() {
        Photo previous = photo(true);
        Photo next = photo(true);
        DailyRecordResponse created = create(category(), previous.getImageKey());
        DailyRecordResponse updated = service.update(created.id(),
                new DailyRecordUpdateRequest(time, "교체", next.getImageKey(), false));

        assertThat(updated.imageKey()).isEqualTo(next.getImageKey());
        assertThat(service.getRecord(created.id()).imageKey()).isEqualTo(next.getImageKey());
        assertThat(photos.findById(previous.getImageKey()).orElseThrow().getRecordId()).isNull();
        assertThat(photos.findById(next.getImageKey()).orElseThrow().getRecordId()).isEqualTo(created.id());
    }

    @Test
    void 사진을_제거하면_메타데이터를_보존하고_다른_기록에_다시_연결할_수_있다() {
        Photo photo = photo(true);
        DailyRecordResponse created = create(category(), photo.getImageKey());
        assertThat(service.update(created.id(), new DailyRecordUpdateRequest(time, "제거", null, true)).imageKey())
                .isNull();
        assertThat(service.getRecord(created.id()).imageKey()).isNull();
        assertThat(photos.findById(photo.getImageKey()).orElseThrow().getRecordId()).isNull();

        DailyRecordResponse second = create(category(), photo.getImageKey());
        assertThat(photos.findById(photo.getImageKey()).orElseThrow().getRecordId()).isEqualTo(second.id());
        assertThat(service.update(created.id(), new DailyRecordUpdateRequest(time, "반복 제거", null, true)).imageKey())
                .isNull();
    }

    @Test
    void 존재하지_않거나_업로드가_완료되지_않은_사진은_기록_생성도_롤백한다() {
        Category category = category();
        Photo pending = photo(false);
        for (String key : List.of("photos/" + UUID.randomUUID() + ".png", pending.getImageKey())) {
            assertError(() -> create(category, key), ErrorCode.PHOTO_NOT_FOUND);
            assertThat(records.findAllByCategoryId(category.getId())).isEmpty();
        }
        assertThat(photos.findById(pending.getImageKey()).orElseThrow().getRecordId()).isNull();
    }

    @Test
    void 이미_연결된_사진은_다른_기록에_연결할_수_없고_생성도_롤백한다() {
        Photo photo = photo(true);
        DailyRecordResponse first = create(category(), photo.getImageKey());
        Category second = category();
        assertError(() -> create(second, photo.getImageKey()), ErrorCode.PHOTO_ALREADY_LINKED);
        assertThat(records.findAllByCategoryId(second.getId())).isEmpty();
        assertThat(photos.findById(photo.getImageKey()).orElseThrow().getRecordId()).isEqualTo(first.id());
    }

    @Test
    void 교체_실패는_메모_시간_사진과_기존_연결을_모두_보존한다() {
        Photo original = photo(true);
        Photo busy = photo(true);
        Photo pending = photo(false);
        DailyRecordResponse created = create(category(), original.getImageKey());
        DailyRecordResponse other = create(category(), busy.getImageKey());
        for (String key : List.of("photos/" + UUID.randomUUID() + ".png", pending.getImageKey(), busy.getImageKey())) {
            ErrorCode error = key.equals(busy.getImageKey()) ? ErrorCode.PHOTO_ALREADY_LINKED : ErrorCode.PHOTO_NOT_FOUND;
            assertError(() -> service.update(created.id(),
                    new DailyRecordUpdateRequest(time.plusHours(1), "실패할 수정", key, false)), error);
            assertThat(service.getRecord(created.id())).isEqualTo(created);
            assertThat(photos.findById(original.getImageKey()).orElseThrow().getRecordId()).isEqualTo(created.id());
        }
        assertError(() -> service.update(created.id(),
                new DailyRecordUpdateRequest(time, "동시 교체 제거", busy.getImageKey(), true)), ErrorCode.INVALID_REQUEST);
        assertThat(photos.findById(busy.getImageKey()).orElseThrow().getRecordId()).isEqualTo(other.id());
        assertThat(photos.findById(pending.getImageKey()).orElseThrow().getRecordId()).isNull();
    }

    @Test
    void 기록을_삭제하면_사진_연결만_해제하고_메타데이터를_보존한다() {
        Photo photo = photo(true);
        DailyRecordResponse created = create(category(), photo.getImageKey());
        service.delete(created.id());
        assertThat(records.findById(created.id())).isEmpty();
        assertThat(photos.findById(photo.getImageKey()).orElseThrow().getRecordId()).isNull();
    }

    @Test
    void DB도_한_기록에_두_사진을_연결하는_것을_거부한다() {
        Photo first = photo(true);
        Photo second = photo(true);
        DailyRecordResponse created = create(category(), first.getImageKey());
        second.attach(created.id());
        assertThatThrownBy(() -> photos.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(photos.findById(second.getImageKey()).orElseThrow().getRecordId()).isNull();
    }

    @Test
    void 같은_사진을_동시에_연결하면_하나만_성공하고_나머지_기록은_롤백한다() throws Exception {
        Photo photo = photo(true);
        Category first = category();
        Category second = category();
        ExecutorService executor = new org.springframework.security.concurrent.DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(2));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> firstResult = executor.submit(() -> concurrentCreate(first, photo.getImageKey(), ready, start));
            Future<Object> secondResult = executor.submit(() -> concurrentCreate(second, photo.getImageKey(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> results = List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter(Long.class::isInstance)).hasSize(1);
            assertThat(results).contains(ErrorCode.PHOTO_ALREADY_LINKED);
            Long winner = (Long) results.stream().filter(Long.class::isInstance).findFirst().orElseThrow();
            assertThat(photos.findById(photo.getImageKey()).orElseThrow().getRecordId()).isEqualTo(winner);
            assertThat(records.findAllByCategoryId(first.getId()).size()
                    + records.findAllByCategoryId(second.getId()).size()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void 같은_기록의_동시_사진_교체는_마지막_사진_하나만_연결한다() throws Exception {
        Photo original = photo(true);
        Photo first = photo(true);
        Photo second = photo(true);
        DailyRecordResponse created = create(category(), original.getImageKey());
        ExecutorService executor = new org.springframework.security.concurrent.DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(2));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<DailyRecordResponse> firstResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("동시 실행 대기 초과"); }
                return service.update(created.id(), new DailyRecordUpdateRequest(time, first.getImageKey(), first.getImageKey(), false));
            });
            Future<DailyRecordResponse> secondResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("동시 실행 대기 초과"); }
                return service.update(created.id(), new DailyRecordUpdateRequest(time, second.getImageKey(), second.getImageKey(), false));
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstResult.get(15, TimeUnit.SECONDS);
            secondResult.get(15, TimeUnit.SECONDS);
            DailyRecordResponse finalRecord = service.getRecord(created.id());
            assertThat(finalRecord.imageKey()).isIn(first.getImageKey(), second.getImageKey());
            assertThat(finalRecord.memo()).isEqualTo(finalRecord.imageKey());
            assertThat(photos.findById(original.getImageKey()).orElseThrow().getRecordId()).isNull();
            List<Photo> candidates = photos.findAllById(List.of(first.getImageKey(), second.getImageKey()));
            assertThat(candidates).filteredOn(candidate -> created.id().equals(candidate.getRecordId()))
                    .extracting(Photo::getImageKey).containsExactly(finalRecord.imageKey());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }


    @Test
    void 조회용_사진은_생성_교체_제거_삭제에_따라_현재_연결만_반환한다() {
        Photo first = photo(true);
        Photo second = photo(true);
        DailyRecordResponse created = create(category(), first.getImageKey());
        assertThat(photos.findReadableByRecordId(created.id(), 1L).orElseThrow().getImageKey()).isEqualTo(first.getImageKey());
        service.update(created.id(), new DailyRecordUpdateRequest(time, "교체", second.getImageKey(), false));
        assertThat(photos.findReadableByRecordId(created.id(), 1L).orElseThrow().getImageKey()).isEqualTo(second.getImageKey());
        service.update(created.id(), new DailyRecordUpdateRequest(time, "제거", null, true));
        assertThat(photos.findReadableByRecordId(created.id(), 1L)).isEmpty();
        service.update(created.id(), new DailyRecordUpdateRequest(time, "다시 연결", first.getImageKey(), false));
        service.delete(created.id());
        assertThat(photos.findReadableByRecordId(created.id(), 1L)).isEmpty();
        assertThat(photos.findReadableByRecordId(Long.MAX_VALUE, 1L)).isEmpty();
    }

    @Test
    void 삭제된_카테고리와_사진_없는_기록은_조회용_사진을_반환하지_않는다() {
        DailyRecordResponse withoutPhoto = create(category(), null);
        assertThat(photos.findReadableByRecordId(withoutPhoto.id(), 1L)).isEmpty();
        Category category = category();
        Photo photo = photo(true);
        DailyRecordResponse created = create(category, photo.getImageKey());
        category.delete();
        categories.saveAndFlush(category);
        assertThat(photos.findReadableByRecordId(created.id(), 1L)).isEmpty();
    }

    @Test
    void DB의_업로드_미완료나_키_불일치도_조회에서_제외한다() {
        Photo photo = photo(true);
        DailyRecordResponse created = create(category(), photo.getImageKey());
        jdbc.update("UPDATE photos SET uploaded_at = NULL WHERE image_key = ?", photo.getImageKey());
        assertThat(photos.findReadableByRecordId(created.id(), 1L)).isEmpty();
        jdbc.update("UPDATE photos SET uploaded_at = created_at WHERE image_key = ?", photo.getImageKey());
        jdbc.update("UPDATE records SET image_key = NULL WHERE id = ?", created.id());
        assertThat(photos.findReadableByRecordId(created.id(), 1L)).isEmpty();
    }

    private Object concurrentCreate(Category category, String key, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("동시 실행 대기 초과"); }
        try {
            return create(category, key).id();
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private Category category() {
        Category category = categories.saveAndFlush(new Category("사진 테스트", "#FF0000", 1, false));
        categoryIds.add(category.getId());
        return category;
    }

    private Photo photo(boolean complete) {
        Photo photo = new Photo("photos/" + UUID.randomUUID() + ".png", "image/png", 100);
        if (complete) { photo.markUploaded(); }
        photoKeys.add(photo.getImageKey());
        return photos.saveAndFlush(photo);
    }

    private DailyRecordResponse create(Category category, String key) {
        return service.create(new DailyRecordCreateRequest(category.getId(), LocalDate.now(), time, "기록", key));
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
