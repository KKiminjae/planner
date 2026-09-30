package com.planner.photo_calendar.completionhistory;

import com.planner.photo_calendar.category.Category;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionHistoryRepositoryTest extends MySqlIntegrationTest {
    @Autowired CompletionHistoryRepository repository;
    @Autowired CategoryRepository categories;
    @Autowired EntityManager entityManager;

    @Test
    void 복합키로_저장하고_새로운_키객체로_조회한다() {
        Category category = category("운동");
        LocalDate date = LocalDate.of(2026, 9, 15);
        repository.saveAndFlush(new CompletionHistory(category, new CompletionHistoryId(category.getId(), date)));
        entityManager.clear();

        assertThat(repository.findById(new CompletionHistoryId(category.getId(), date))).isPresent();
        assertThat(repository.findById(new CompletionHistoryId(category.getId(), date.plusDays(1)))).isEmpty();
        assertThat(repository.findById(new CompletionHistoryId(Long.MAX_VALUE, date))).isEmpty();
    }

    @Test
    void 복합키의_두_요소를_구분하고_해당날짜의_이력만_집계한다() {
        Category first = category("운동");
        Category second = category("공부");
        LocalDate date = LocalDate.of(2026, 9, 15);
        repository.save(new CompletionHistory(first, new CompletionHistoryId(first.getId(), date)));
        repository.save(new CompletionHistory(first, new CompletionHistoryId(first.getId(), date.plusDays(1))));
        repository.save(new CompletionHistory(second, new CompletionHistoryId(second.getId(), date)));
        first.delete();
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.count()).isEqualTo(3);
        assertThat(repository.countById_RecordDate(date)).isEqualTo(2);
        assertThat(repository.countById_RecordDate(date.plusDays(1))).isEqualTo(1);
        assertThat(repository.countById_RecordDate(date.minusDays(1))).isZero();
    }

    private Category category(String name) {
        return categories.saveAndFlush(new Category(name, "#FF0000", 1, false));
    }
}
