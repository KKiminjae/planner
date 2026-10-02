package com.planner.photo_calendar.category;

import com.planner.photo_calendar.category.dto.request.CategoryCreateRequest;
import com.planner.photo_calendar.category.dto.request.CategoryReorderRequest;
import com.planner.photo_calendar.category.dto.request.CategoryUpdateRequest;
import com.planner.photo_calendar.category.dto.AnnualRecordStatus;
import com.planner.photo_calendar.category.dto.response.AnnualRecordResponse;
import com.planner.photo_calendar.category.dto.response.CategoryResponse;
import com.planner.photo_calendar.completionhistory.CompletionHistory;
import com.planner.photo_calendar.completionhistory.CompletionHistoryRepository;
import com.planner.photo_calendar.record.DailyRecord;
import com.planner.photo_calendar.record.DailyRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.planner.photo_calendar.auth.CurrentOwner;
import com.planner.photo_calendar.common.exception.BusinessException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Test
    void 한국시간_자정의_오늘을_연간달력에서_미래로_분류하지_않는다() {
        when(applicationTime.today()).thenReturn(LocalDate.of(2026, 10, 2));
        Category category = mock(Category.class);
        when(category.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 10, 1, 15, 5));
        when(categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(1L, 1L)).thenReturn(Optional.of(category));
        when(dailyRecordRepository.findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).thenReturn(List.of());
        List<AnnualRecordResponse> days = categoryService.getAnnualRecords(1L, 2026);
        assertEquals(AnnualRecordStatus.NO_DATE, days.stream().filter(day -> day.month() == 10 && day.day() == 1)
                .findFirst().orElseThrow().status());
        assertEquals(AnnualRecordStatus.MISSED, days.stream().filter(day -> day.month() == 10 && day.day() == 2)
                .findFirst().orElseThrow().status());
        assertEquals(AnnualRecordStatus.FUTURE, days.stream().filter(day -> day.month() == 10 && day.day() == 3)
                .findFirst().orElseThrow().status());
    }

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private DailyRecordRepository dailyRecordRepository;

    @Mock
    private CompletionHistoryRepository completionHistoryRepository;

    @Mock
    private CurrentOwner currentOwner;

    @Mock
    private com.planner.photo_calendar.common.time.ApplicationTime applicationTime;

    @BeforeEach
    void 로그인_소유자를_설정한다() {
        org.mockito.Mockito.lenient().when(currentOwner.id()).thenReturn(1L);
        org.mockito.Mockito.lenient().when(applicationTime.today()).thenReturn(LocalDate.now());
    }

    @Mock
    private com.planner.photo_calendar.record.DailyRecordService dailyRecordService;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void 카테고리를_정상생성한다() {
        CategoryCreateRequest request = new CategoryCreateRequest(
                "운동",
                "#FF0000",
                false
        );

        when(categoryRepository.findMaxDisplayOrderByOwnerId(1L)).thenReturn(2);
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.create(request);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository, times(1)).findMaxDisplayOrderByOwnerId(1L);
        verify(categoryRepository, times(1)).save(categoryCaptor.capture());

        Category savedCategory = categoryCaptor.getValue();
        assertEquals("운동", savedCategory.getName());
        assertEquals("#FF0000", savedCategory.getColor());
        assertEquals(3, savedCategory.getDisplayOrder());
        assertFalse(savedCategory.getIsPrivate());

        assertEquals("운동", response.name());
        assertEquals("#FF0000", response.color());
        assertEquals(3, response.displayOrder());
        assertFalse(response.isPrivate());
    }

    @Test
    void 첫_카테고리의_표시순서는_1이다() {
        CategoryCreateRequest request = new CategoryCreateRequest(
                "공부",
                "#0000FF",
                false
        );

        when(categoryRepository.findMaxDisplayOrderByOwnerId(1L)).thenReturn(0);
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.create(request);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository, times(1)).save(categoryCaptor.capture());

        assertEquals(1, categoryCaptor.getValue().getDisplayOrder());
        assertEquals(1, response.displayOrder());
    }

    @Test
    void 삭제되지_않은_카테고리를_표시순서대로_조회한다() {
        Category firstCategory = new Category("운동", "#FF0000", 1, false);
        Category secondCategory = new Category("공부", "#0000FF", 2, true);

        when(categoryRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(firstCategory, secondCategory));

        List<CategoryResponse> responses = categoryService.getCategories();

        assertEquals(2, responses.size());
        assertEquals("운동", responses.get(0).name());
        assertEquals(1, responses.get(0).displayOrder());
        assertFalse(responses.get(0).isPrivate());
        assertEquals("공부", responses.get(1).name());
        assertEquals(2, responses.get(1).displayOrder());
        assertEquals(true, responses.get(1).isPrivate());

        verify(categoryRepository, times(1))
                .findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(1L);
    }

    @Test
    void 조회할_카테고리가_없으면_빈목록을_반환한다() {
        when(categoryRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of());

        List<CategoryResponse> responses = categoryService.getCategories();

        assertNotNull(responses);
        assertTrue(responses.isEmpty());

        verify(categoryRepository, times(1))
                .findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(1L);
    }

    @Test
    void 카테고리를_정상수정한다() {
        Long categoryId = 1L;
        Category category = new Category("운동", "#FF0000", 1, false);
        CategoryUpdateRequest request = new CategoryUpdateRequest(
                "아침 운동",
                "#00FF00",
                true
        );

        when(categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L))
                .thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.update(categoryId, request);

        assertEquals("아침 운동", category.getName());
        assertEquals("#00FF00", category.getColor());
        assertTrue(category.getIsPrivate());
        assertEquals("아침 운동", response.name());
        assertEquals("#00FF00", response.color());
        assertTrue(response.isPrivate());

        verify(categoryRepository, times(1))
                .findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L);
    }

    @Test
    void 존재하지_않는_카테고리는_수정할_수_없다() {
        Long categoryId = 999L;
        CategoryUpdateRequest request = new CategoryUpdateRequest(
                "수정할 카테고리",
                "#00FF00",
                true
        );

        when(categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> categoryService.update(categoryId, request)
        );

        assertEquals(
                "존재하지 않는 카테고리 입니다.",
                exception.getMessage()
        );

        verify(categoryRepository, times(1))
                .findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L);
    }

    @Test
    void 카테고리_표시순서를_정상변경한다() {
        Category firstCategory = mock(Category.class);
        Category secondCategory = mock(Category.class);
        Category thirdCategory = mock(Category.class);

        when(firstCategory.getId()).thenReturn(1L);
        when(secondCategory.getId()).thenReturn(2L);
        when(thirdCategory.getId()).thenReturn(3L);
        when(categoryRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(firstCategory, secondCategory, thirdCategory));

        CategoryReorderRequest request = new CategoryReorderRequest(
                List.of(3L, 1L, 2L)
        );

        categoryService.reorder(request);

        verify(thirdCategory, times(1)).changeDisplayOrder(1);
        verify(firstCategory, times(1)).changeDisplayOrder(2);
        verify(secondCategory, times(1)).changeDisplayOrder(3);
        verify(categoryRepository, times(1))
                .findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(1L);
    }

    @Test
    void 모든_카테고리_ID를_전달하지_않으면_순서변경에_실패한다() {
        Category firstCategory = mock(Category.class);
        Category secondCategory = mock(Category.class);

        when(categoryRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(firstCategory, secondCategory));

        CategoryReorderRequest request = new CategoryReorderRequest(List.of(1L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> categoryService.reorder(request)
        );

        assertEquals(
                "모든 카테고리의 순서를 전달해야 합니다.",
                exception.getMessage()
        );
        verify(firstCategory, never()).changeDisplayOrder(any(Integer.class));
        verify(secondCategory, never()).changeDisplayOrder(any(Integer.class));
    }

    @Test
    void 존재하지_않는_ID가_있으면_순서변경에_실패한다() {
        Category firstCategory = mock(Category.class);
        Category secondCategory = mock(Category.class);

        when(firstCategory.getId()).thenReturn(1L);
        when(secondCategory.getId()).thenReturn(2L);
        when(categoryRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(firstCategory, secondCategory));

        CategoryReorderRequest request = new CategoryReorderRequest(List.of(999L, 1L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> categoryService.reorder(request)
        );

        assertEquals(
                "활성 카테고리 전체를 중복 없이 전달해야 합니다.",
                exception.getMessage()
        );
        verify(firstCategory, never()).changeDisplayOrder(any(Integer.class));
        verify(secondCategory, never()).changeDisplayOrder(any(Integer.class));
    }

    @Test
    void 중복된_ID가_있으면_순서변경에_실패한다() {
        Category firstCategory = mock(Category.class);
        Category secondCategory = mock(Category.class);

        when(categoryRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(firstCategory, secondCategory));

        CategoryReorderRequest request = new CategoryReorderRequest(List.of(1L, 1L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> categoryService.reorder(request)
        );

        assertEquals(
                "모든 카테고리의 순서를 중복 없이 전달해야 합니다.",
                exception.getMessage()
        );
        verify(firstCategory, never()).changeDisplayOrder(any(Integer.class));
        verify(secondCategory, never()).changeDisplayOrder(any(Integer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 카테고리_삭제시_기록을_완료이력으로_옮기고_삭제한다() {
        Long categoryId = 1L;
        Category category = new Category("운동", "#FF0000", 1, false);
        DailyRecord firstRecord = mock(DailyRecord.class);
        DailyRecord secondRecord = mock(DailyRecord.class);
        List<DailyRecord> records = List.of(firstRecord, secondRecord);

        when(categoryRepository.findActiveOwnedByIdForUpdate(categoryId, 1L))
                .thenReturn(Optional.of(category));
        when(dailyRecordRepository.findAllByCategoryId(categoryId))
                .thenReturn(records);
        when(firstRecord.getId()).thenReturn(11L);
        when(secondRecord.getId()).thenReturn(12L);
        when(firstRecord.getRecordDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(secondRecord.getRecordDate()).thenReturn(LocalDate.of(2026, 9, 2));

        categoryService.delete(categoryId);

        ArgumentCaptor<List<CompletionHistory>> historyCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(completionHistoryRepository, times(1))
                .saveAll(historyCaptor.capture());
        assertEquals(2, historyCaptor.getValue().size());

        verify(dailyRecordService).delete(11L);
        verify(dailyRecordService).delete(12L);
        assertNotNull(category.getDeletedAt());
    }

    @Test
    void 기록이_없어도_카테고리를_정상삭제한다() {
        Long categoryId = 1L;
        Category category = new Category("운동", "#FF0000", 1, false);

        when(categoryRepository.findActiveOwnedByIdForUpdate(categoryId, 1L))
                .thenReturn(Optional.of(category));
        when(dailyRecordRepository.findAllByCategoryId(categoryId))
                .thenReturn(List.of());

        categoryService.delete(categoryId);

        verify(completionHistoryRepository, times(1)).saveAll(List.of());
        org.mockito.Mockito.verifyNoInteractions(dailyRecordService);
        assertNotNull(category.getDeletedAt());
    }

    @Test
    void 존재하지_않는_카테고리는_삭제할_수_없다() {
        Long categoryId = 999L;

        when(categoryRepository.findActiveOwnedByIdForUpdate(categoryId, 1L))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> categoryService.delete(categoryId)
        );

        assertEquals(
                "존재하지 않는 카테고리입니다.",
                exception.getMessage()
        );
        verify(dailyRecordRepository, never()).findAllByCategoryId(categoryId);
        verify(completionHistoryRepository, never()).saveAll(any());
    }

    @Test
    void 연간기록의_상태를_날짜에_맞게_반환한다() {
        Long categoryId = 1L;
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        LocalDate createdDate = today.minusDays(2);
        LocalDate beforeCreatedDate = createdDate.minusDays(1);
        LocalDate recordedDate = today.minusDays(1);
        LocalDate futureDate = today.plusDays(1);
        Category category = mock(Category.class);
        DailyRecord record = mock(DailyRecord.class);

        when(categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L))
                .thenReturn(Optional.of(category));
        when(category.getCreatedAt()).thenReturn(createdDate.atStartOfDay());
        when(dailyRecordRepository
                .findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                        categoryId,
                        LocalDate.of(year, 1, 1),
                        LocalDate.of(year, 12, 31)
                ))
                .thenReturn(List.of(record));
        when(record.getRecordDate()).thenReturn(recordedDate);

        List<AnnualRecordResponse> responses =
                categoryService.getAnnualRecords(categoryId, year);

        assertEquals(372, responses.size());
        assertEquals(
                AnnualRecordStatus.NO_DATE,
                findAnnualStatus(responses, beforeCreatedDate)
        );
        assertEquals(
                AnnualRecordStatus.RECORDED,
                findAnnualStatus(responses, recordedDate)
        );
        assertEquals(
                AnnualRecordStatus.MISSED,
                findAnnualStatus(responses, today)
        );
        assertEquals(
                AnnualRecordStatus.FUTURE,
                findAnnualStatus(responses, futureDate)
        );
    }

    @Test
    void 존재하지_않는_카테고리의_연간기록은_조회할_수_없다() {
        Long categoryId = 999L;

        when(categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> categoryService.getAnnualRecords(categoryId, 2026)
        );

        assertEquals(
                "존재하지 않는 카테고리입니다.",
                exception.getMessage()
        );
        verify(dailyRecordRepository, never())
                .findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                        any(Long.class),
                        any(LocalDate.class),
                        any(LocalDate.class)
                );
    }

    @Test
    void 달력에_존재하지_않는_날짜는_NO_DATE를_반환한다() {
        Long categoryId = 1L;
        int year = 2025;
        Category category = mock(Category.class);

        when(categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L))
                .thenReturn(Optional.of(category));
        when(category.getCreatedAt()).thenReturn(LocalDate.of(2025, 1, 1).atStartOfDay());
        when(dailyRecordRepository
                .findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                        categoryId,
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 31)
                ))
                .thenReturn(List.of());

        List<AnnualRecordResponse> responses =
                categoryService.getAnnualRecords(categoryId, year);

        assertEquals(AnnualRecordStatus.NO_DATE, findAnnualStatus(responses, 2, 30));
        assertEquals(AnnualRecordStatus.NO_DATE, findAnnualStatus(responses, 4, 31));
    }

    @Test
    void 윤년_2월_29일의_기록을_정상조회한다() {
        Long categoryId = 1L;
        int year = 2024;
        LocalDate leapDay = LocalDate.of(2024, 2, 29);
        Category category = mock(Category.class);
        DailyRecord record = mock(DailyRecord.class);

        when(categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L))
                .thenReturn(Optional.of(category));
        when(category.getCreatedAt()).thenReturn(LocalDate.of(2024, 1, 1).atStartOfDay());
        when(dailyRecordRepository
                .findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                        categoryId,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 12, 31)
                ))
                .thenReturn(List.of(record));
        when(record.getRecordDate()).thenReturn(leapDay);

        List<AnnualRecordResponse> responses =
                categoryService.getAnnualRecords(categoryId, year);

        assertEquals(AnnualRecordStatus.RECORDED, findAnnualStatus(responses, leapDay));
    }

    private AnnualRecordStatus findAnnualStatus(
            List<AnnualRecordResponse> responses,
            LocalDate date
    ) {
        return findAnnualStatus(responses, date.getMonthValue(), date.getDayOfMonth());
    }

    private AnnualRecordStatus findAnnualStatus(
            List<AnnualRecordResponse> responses,
            int month,
            int day
    ) {
        return responses.stream()
                .filter(response -> response.month() == month && response.day() == day)
                .findFirst()
                .orElseThrow()
                .status();
    }
}
