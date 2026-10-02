package com.planner.photo_calendar.category;

import com.planner.photo_calendar.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryRepositoryTest extends MySqlIntegrationTest {
    @Autowired CategoryRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 삭제된_카테고리는_목록과_ID조회와_최대순서에서_제외한다() {
        Category third = save("세 번째", 3);
        Category deleted = save("삭제", 99);
        Category first = save("첫 번째", 1);
        deleted.delete();
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc())
                .extracting(Category::getId).containsExactly(first.getId(), third.getId());
        assertThat(repository.findByIdAndDeletedAtIsNull(deleted.getId())).isEmpty();
        assertThat(repository.findByIdAndDeletedAtIsNull(first.getId())).isPresent();
        assertThat(repository.findByIdAndDeletedAtIsNull(Long.MAX_VALUE)).isEmpty();
        assertThat(repository.findMaxDisplayOrder()).isEqualTo(3);
    }

    @Test
    void 활성_카테고리가_없으면_최대순서는_0이다() {
        assertThat(repository.findMaxDisplayOrder()).isZero();
        save("삭제", 5).delete();
        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findMaxDisplayOrder()).isZero();
        assertThat(repository.findAllByDeletedAtIsNullOrderByDisplayOrderAsc()).isEmpty();
    }

    @Test
    void 활성_카테고리_집계는_생성일과_삭제일의_경계를_적용한다() {
        LocalDateTime start = LocalDate.of(2026, 9, 15).atStartOfDay();
        LocalDateTime next = start.plusDays(1);
        saveAt("이전 생성", start.minusDays(1), null);
        saveAt("당일 0시 생성", start, null);
        saveAt("당일 마지막 생성", next.minusSeconds(1), null);
        saveAt("다음날 생성", next, null);
        saveAt("이전 삭제", start.minusDays(2), start.minusSeconds(1));
        saveAt("당일 0시 삭제", start.minusDays(2), start);
        saveAt("당일 삭제", start.minusDays(2), start.plusHours(12));
        saveAt("다음날 삭제", start.minusDays(2), next);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.countActiveCategoriesAt(start, next)).isEqualTo(6);
    }

    @Test
    void 카테고리가_없으면_활성_집계는_0이다() {
        LocalDateTime start = LocalDate.of(2026, 9, 15).atStartOfDay();
        assertThat(repository.countActiveCategoriesAt(start, start.plusDays(1))).isZero();
    }

    @Test
    void 날짜별_목록은_생성삭제경계와_소유자를_적용하고_집계분모와_일치한다() {
        LocalDateTime start = com.planner.photo_calendar.common.time.ApplicationTime.startOfDayUtc(LocalDate.of(2026, 10, 2));
        LocalDateTime next = start.plusDays(1);
        saveAt("기존", start.minusDays(1), null);
        saveAt("당일 생성", next.minusSeconds(1), null);
        saveAt("이후 생성", next, null);
        saveAt("이전 삭제", start.minusDays(2), start.minusSeconds(1));
        saveAt("당일 삭제", start.minusDays(2), start);
        Category other = repository.saveAndFlush(new Category(2L, "다른 소유자", "#123456", 1, false));
        entityManager.createNativeQuery("UPDATE categories SET created_at = :created WHERE id = :id")
                .setParameter("created", start.minusDays(1)).setParameter("id", other.getId()).executeUpdate();
        entityManager.flush();
        entityManager.clear();
        var categories = repository.findActiveCategoriesByOwnerAt(1L, start, next);
        assertThat(categories).extracting(Category::getName).containsExactly("기존", "당일 생성", "당일 삭제");
        assertThat(categories).hasSize((int) repository.countActiveCategoriesByOwnerAt(1L, start, next));
    }

    private Category save(String name, int order) {
        return repository.saveAndFlush(new Category(name, "#FF0000", order, false));
    }

    private void saveAt(String name, LocalDateTime createdAt, LocalDateTime deletedAt) {
        Category category = save(name, 1);
        entityManager.createNativeQuery("UPDATE categories SET created_at = :created, deleted_at = :deleted WHERE id = :id")
                .setParameter("created", createdAt)
                .setParameter("deleted", deletedAt)
                .setParameter("id", category.getId())
                .executeUpdate();
    }
}
