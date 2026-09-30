package com.planner.photo_calendar.record;

import com.planner.photo_calendar.category.Category;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import com.planner.photo_calendar.common.exception.GlobalExceptionHandler;
import com.planner.photo_calendar.common.exception.ErrorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyRecordRepositoryTest extends MySqlIntegrationTest {
    @Autowired DailyRecordRepository repository;
    @Autowired CategoryRepository categories;
    @Autowired EntityManager entityManager;
    private final LocalDate date = LocalDate.of(2026, 9, 15);

    @Test
    void 같은_카테고리와_날짜의_중복기록은_DB에서_거부한다() {
        Category category = category("운동");
        save(category, date, 9);
        entityManager.clear();
        assertThatThrownBy(() -> save(category, date, 18))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, exception -> {
                    var response = new GlobalExceptionHandler().handleIntegrity(exception);
                    assertThat(response.getStatusCode().value()).isEqualTo(409);
                    assertThat(((ErrorResponse) response.getBody()).code()).isEqualTo("DUPLICATE_DAILY_RECORD");
                });
    }

    @Test
    void 카테고리나_날짜가_다르면_저장할_수_있다() {
        Category first = category("운동");
        Category second = category("공부");
        save(first, date, 9);
        save(first, date.plusDays(1), 9);
        save(second, date, 9);
        entityManager.clear();

        assertThat(repository.existsByCategoryIdAndRecordDate(first.getId(), date)).isTrue();
        assertThat(repository.existsByCategoryIdAndRecordDate(first.getId(), date.minusDays(1))).isFalse();
        assertThat(repository.existsByCategoryIdAndRecordDate(Long.MAX_VALUE, date)).isFalse();
        assertThat(repository.countByRecordDate(date)).isEqualTo(2);
        assertThat(repository.countByRecordDate(date.minusDays(1))).isZero();
        assertThat(repository.findAllByCategoryId(first.getId())).hasSize(2)
                .allSatisfy(record -> assertThat(record.getCategory().getId()).isEqualTo(first.getId()));
    }

    @Test
    void 당일기록은_실제_기록시간_오름차순으로_조회한다() {
        DailyRecord late = save(category("저녁"), date, 21);
        DailyRecord early = save(category("아침"), date, 6);
        DailyRecord middle = save(category("점심"), date, 12);
        save(category("다른 날"), date.plusDays(1), 0);
        entityManager.clear();

        assertThat(repository.findAllByRecordDateOrderByRecordTimeAsc(date))
                .extracting(DailyRecord::getId).containsExactly(early.getId(), middle.getId(), late.getId());
    }

    @Test
    void 날짜범위는_양끝을_포함하고_날짜순으로_정렬한다() {
        Category category = category("운동");
        LocalDate end = date.plusDays(2);
        DailyRecord last = save(category, end, 6);
        save(category, end.plusDays(1), 6);
        DailyRecord first = save(category, date, 21);
        save(category, date.minusDays(1), 6);
        DailyRecord middle = save(category, date.plusDays(1), 12);
        save(category("다른 카테고리"), date, 6);
        entityManager.clear();

        assertThat(repository.findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(category.getId(), date, end))
                .extracting(DailyRecord::getId).containsExactly(first.getId(), middle.getId(), last.getId());
        assertThat(repository.findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(category.getId(), date, date))
                .extracting(DailyRecord::getId).containsExactly(first.getId());
        assertThat(repository.findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(category.getId(), end.plusDays(2), end.plusDays(3)))
                .isEmpty();
    }

    private Category category(String name) {
        return categories.saveAndFlush(new Category(name, "#FF0000", 1, false));
    }

    private DailyRecord save(Category category, LocalDate date, int hour) {
        return repository.saveAndFlush(new DailyRecord(category, date, LocalTime.of(hour, 0), "기록", "images/test.jpg"));
    }
}
