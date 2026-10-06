package com.planner.photo_calendar.record;

import com.planner.photo_calendar.category.Category;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.record.dto.request.DailyRecordCreateRequest;
import com.planner.photo_calendar.record.dto.request.DailyRecordUpdateRequest;
import com.planner.photo_calendar.record.dto.response.DailyRecordResponse;
import com.planner.photo_calendar.record.dto.response.MonthlyRecordResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.planner.photo_calendar.auth.CurrentOwner;
import com.planner.photo_calendar.common.exception.BusinessException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyRecordServiceTest {

    @Test
    void 한국시간_자정이_지나면_UTC의_내일인_오늘기록을_허용한다() {
        when(applicationTime.today()).thenReturn(LocalDate.of(2026, 10, 2));
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(1L);
        when(categoryRepository.findActiveOwnedByIdForUpdate(1L, 1L)).thenReturn(Optional.of(category));
        when(dailyRecordRepository.save(any(DailyRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DailyRecordResponse response = dailyRecordService.create(new DailyRecordCreateRequest(
                1L, LocalDate.of(2026, 10, 2), LocalTime.of(0, 30), "자정 기록"));
        assertEquals(LocalDate.of(2026, 10, 2), response.recordDate());
        assertEquals(LocalTime.of(0, 30), response.recordTime());
    }

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private DailyRecordRepository dailyRecordRepository;

    @Mock
    private com.planner.photo_calendar.photo.PhotoLinkService photoLinkService;

    @Mock
    private CurrentOwner currentOwner;

    @Mock
    private com.planner.photo_calendar.common.time.ApplicationTime applicationTime;

    @BeforeEach
    void 로그인_소유자를_설정한다() {
        org.mockito.Mockito.lenient().when(currentOwner.id()).thenReturn(1L);
        org.mockito.Mockito.lenient().when(applicationTime.today()).thenReturn(LocalDate.now());
    }

    @InjectMocks
    private DailyRecordService dailyRecordService;

    @Test
    void 같은_카테고리에_같은날짜_기록은_중복x(){

        Long categoryId = 1L;
        LocalDate recordDate = LocalDate.now();

        Category category = mock(Category.class);

        when(categoryRepository.findActiveOwnedByIdForUpdate(categoryId, 1L))
                .thenReturn(Optional.of(category));

        when(category.getId())
                .thenReturn(categoryId);


        when(dailyRecordRepository.existsByCategoryIdAndRecordDate(
                categoryId,
                recordDate
        )).thenReturn(true);

        DailyRecordCreateRequest request = new DailyRecordCreateRequest(
                categoryId,
                recordDate,
                LocalTime.of(12, 0),
                "테스트 기록"
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> dailyRecordService.create(request)
        );

        assertEquals(
                "해당 날짜에 이미 기록이 존재합니다.",
                exception.getMessage()
        );

        verify(dailyRecordRepository, never())
                .save(any(DailyRecord.class));
    }

    @Test
    void 카테고리_생성일_이전날짜에도_기록할_수_있다() {
        Category category = new Category(1L, "과거 기록", "#123456", 1, false);
        org.springframework.test.util.ReflectionTestUtils.setField(category, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(category, "createdAt",
                java.time.LocalDateTime.of(2026, 9, 15, 0, 0));
        when(applicationTime.today()).thenReturn(LocalDate.of(2026, 10, 2));
        when(categoryRepository.findActiveOwnedByIdForUpdate(1L, 1L)).thenReturn(Optional.of(category));
        when(dailyRecordRepository.save(any(DailyRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var response = dailyRecordService.create(new DailyRecordCreateRequest(
                1L, LocalDate.of(2025, 2, 1), LocalTime.NOON, ""));
        assertEquals(LocalDate.of(2025, 2, 1), response.recordDate());
        assertEquals("", response.memo());
        verify(dailyRecordRepository).save(any(DailyRecord.class));
    }

    @Test
    void 미래날짜_기록시_생성x(){
        Long categoryId = 1L;
        LocalDate recordDate = LocalDate.now().plusDays(1);

        Category category = mock(Category.class);

        when(categoryRepository.findActiveOwnedByIdForUpdate(categoryId, 1L))
                .thenReturn(Optional.of(category));


        DailyRecordCreateRequest request = new DailyRecordCreateRequest(
                categoryId,
                recordDate,
                LocalTime.of(12, 0),
                "테스트 기록"
        );

        BusinessException exception = assertThrows(BusinessException.class,
                () -> dailyRecordService.create(request));

        assertEquals(
                "미래 날짜에는 기록할 수 없습니다.",
                exception.getMessage()
        );

        verify(dailyRecordRepository, never())
                .existsByCategoryIdAndRecordDate(anyLong(), any());

        verify(dailyRecordRepository, never())
                .save(any(DailyRecord.class));
    }

    @Test
    void 정상적인_기록은_생성o(){
        Long categoryId = 1L;
        LocalDate recordDate = LocalDate.now();
        LocalTime recordTime = LocalTime.of(12,30);

        Category category = mock(Category.class);

        when(categoryRepository.findActiveOwnedByIdForUpdate(categoryId, 1L))
                .thenReturn(Optional.of(category));

        when(dailyRecordRepository.existsByCategoryIdAndRecordDate(
                categoryId,
                recordDate
        )).thenReturn(false);


        when(category.getId())
                .thenReturn(categoryId);

        when(category.getName())
                .thenReturn("점심");

        when(dailyRecordRepository.save(any(DailyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DailyRecordCreateRequest request = new DailyRecordCreateRequest(categoryId,
                recordDate,
                recordTime,
                "오늘 점심 기록");

        DailyRecordResponse response = dailyRecordService.create(request);

        assertEquals(categoryId, response.categoryId());
        assertEquals(recordDate, response.recordDate());
        assertEquals(recordTime, response.recordTime());
        assertEquals("오늘 점심 기록", response.memo());

        verify(dailyRecordRepository, times(1))
                .save(any(DailyRecord.class));
    }

    @Test
    void 존재하지_않는_카테고리에_생성x(){
        Long categoryId = 999L;

        when(categoryRepository.findActiveOwnedByIdForUpdate(categoryId, 1L))
                .thenReturn(Optional.empty());

        DailyRecordCreateRequest request = new DailyRecordCreateRequest(
                categoryId,
                LocalDate.now(),
                LocalTime.of(12, 30),
                "테스트 기록"
        );

        BusinessException exception = assertThrows(BusinessException.class,
                () -> dailyRecordService.create(request));

        assertEquals("존재하지 않는 카테고리 입니다.",
                exception.getMessage());

        verify(dailyRecordRepository, never())
                .existsByCategoryIdAndRecordDate(anyLong(),any());

        verify(dailyRecordRepository, never())
                .save(any(DailyRecord.class));
    }

    @Test
    void 정상_단건조회(){
        Long recordId = 1L;

        DailyRecord dailyRecord = mock(DailyRecord.class);
        Category category = mock(Category.class);

        when(dailyRecordRepository.findByIdAndCategoryOwnerId(recordId, 1L))
                .thenReturn(Optional.of(dailyRecord));

        when(dailyRecord.getCategory())
                .thenReturn(category);

        when(dailyRecord.getId())
                .thenReturn(recordId);

        when(category.getId())
                .thenReturn(1L);

        when(category.getName())
                .thenReturn("점심");

        when(dailyRecord.getRecordDate())
                .thenReturn(LocalDate.of(2026, 9 ,20));

        when(dailyRecord.getRecordTime())
                .thenReturn(LocalTime.of(12,30));

        when(dailyRecord.getMemo())
                .thenReturn("점심 기록");

        when(dailyRecord.getImageKey())
                .thenReturn(null);

        DailyRecordResponse response = dailyRecordService.getRecord(recordId);

        assertEquals(recordId, response.id());
        assertEquals(1L, response.categoryId());
        assertEquals("점심", response.categoryName());
        assertEquals(LocalDate.of(2026, 9 ,20), response.recordDate());
        assertEquals(LocalTime.of(12,30), response.recordTime());
        assertEquals("점심 기록", response.memo());
    }

    @Test
    void 존재하지_않는_기록은_조회x() {
        Long recordId = 999L;

        when(dailyRecordRepository.findByIdAndCategoryOwnerId(recordId, 1L))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> dailyRecordService.getRecord(recordId)
        );

        assertEquals(
                "존재하지 않는 기록입니다.",
                exception.getMessage()
        );

        verify(dailyRecordRepository, times(1))
                .findByIdAndCategoryOwnerId(recordId, 1L);
    }

    @Test
    void 특정날짜의_기록을_정상조회한다() {
        LocalDate recordDate = LocalDate.of(2026, 9, 20);

        Category lunchCategory = mock(Category.class);
        Category dinnerCategory = mock(Category.class);
        DailyRecord lunchRecord = mock(DailyRecord.class);
        DailyRecord dinnerRecord = mock(DailyRecord.class);

        when(lunchRecord.getId()).thenReturn(1L);
        when(lunchRecord.getCategory()).thenReturn(lunchCategory);
        when(lunchCategory.getId()).thenReturn(1L);
        when(lunchCategory.getName()).thenReturn("점심");
        when(lunchRecord.getRecordDate()).thenReturn(recordDate);
        when(lunchRecord.getRecordTime()).thenReturn(LocalTime.of(12, 30));
        when(lunchRecord.getMemo()).thenReturn("점심 기록");

        when(dinnerRecord.getId()).thenReturn(2L);
        when(dinnerRecord.getCategory()).thenReturn(dinnerCategory);
        when(dinnerCategory.getId()).thenReturn(2L);
        when(dinnerCategory.getName()).thenReturn("저녁");
        when(dinnerRecord.getRecordDate()).thenReturn(recordDate);
        when(dinnerRecord.getRecordTime()).thenReturn(LocalTime.of(18, 30));
        when(dinnerRecord.getMemo()).thenReturn("저녁 기록");

        when(dailyRecordRepository.findAllByRecordDateAndCategoryOwnerIdOrderByRecordTimeAsc(recordDate, 1L))
                .thenReturn(List.of(lunchRecord, dinnerRecord));

        List<DailyRecordResponse> responses = dailyRecordService.getDailyRecords(recordDate);

        assertEquals(2, responses.size());
        assertEquals(1L, responses.get(0).id());
        assertEquals(LocalTime.of(12, 30), responses.get(0).recordTime());
        assertEquals(2L, responses.get(1).id());
        assertEquals(LocalTime.of(18, 30), responses.get(1).recordTime());

        verify(dailyRecordRepository, times(1))
                .findAllByRecordDateAndCategoryOwnerIdOrderByRecordTimeAsc(recordDate, 1L);
    }

    @Test
    void 특정날짜에_기록이_없으면_빈목록을_반환한다() {
        LocalDate recordDate = LocalDate.of(2026, 9, 21);

        when(dailyRecordRepository.findAllByRecordDateAndCategoryOwnerIdOrderByRecordTimeAsc(recordDate, 1L))
                .thenReturn(List.of());

        List<DailyRecordResponse> responses = dailyRecordService.getDailyRecords(recordDate);

        assertNotNull(responses);
        assertTrue(responses.isEmpty());

        verify(dailyRecordRepository, times(1))
                .findAllByRecordDateAndCategoryOwnerIdOrderByRecordTimeAsc(recordDate, 1L);
    }

    @Test
    void 기록_정상적_수정o (){
        Long recordId = 1L;
        LocalTime newRecordTime = LocalTime.of(18,30);
        String newMemo = "수정된 기록";

        DailyRecord dailyRecord = mock(DailyRecord.class);
        Category category = mock(Category.class);

        when(dailyRecordRepository.findOwnedByIdForUpdate(recordId, 1L))
                .thenReturn(Optional.of(dailyRecord));

        when(dailyRecord.getId())
                .thenReturn(recordId);

        when(dailyRecord.getCategory())
                .thenReturn(category);

        when(category.getId())
                .thenReturn(1L);

        when(category.getName())
                .thenReturn("저녁");

        when(dailyRecord.getRecordDate())
                .thenReturn(LocalDate.of(2026, 9, 20));

        when(dailyRecord.getRecordTime())
                .thenReturn(newRecordTime);

        when(dailyRecord.getMemo())
                .thenReturn(newMemo);

        when(dailyRecord.getImageKey())
                .thenReturn(null);

        DailyRecordUpdateRequest request =
                new DailyRecordUpdateRequest(
                        newRecordTime,
                        newMemo
                );

        DailyRecordResponse response = dailyRecordService.update(recordId, request);

        verify(dailyRecord, times(1))
                .update(newRecordTime, newMemo);

        assertEquals(newRecordTime, response.recordTime());
        assertEquals(newMemo, response.memo());

        verify(dailyRecordRepository, never())
                .save(any(DailyRecord.class));
    }

    @Test
    void 존재하지_않는_기록은_수정x(){
        Long recordId = 999L;

        when(dailyRecordRepository.findOwnedByIdForUpdate(recordId, 1L))
                .thenReturn(Optional.empty());

        DailyRecordUpdateRequest request =
                new DailyRecordUpdateRequest(
                        LocalTime.of(18, 30),
                        "수정된 기록"
                );

        BusinessException exception = assertThrows(BusinessException.class,
                () -> dailyRecordService.update(recordId, request));

        assertEquals(
                "존재하지 않는 기록 입니다.",
                exception.getMessage()
        );

        verify(dailyRecordRepository, never())
                .save(any(DailyRecord.class));

    }

    @Test
    void 존재하는_기록을_정상삭제한다() {
        Long recordId = 1L;
        DailyRecord dailyRecord = mock(DailyRecord.class);

        when(dailyRecordRepository.findOwnedByIdForUpdate(recordId, 1L))
                .thenReturn(Optional.of(dailyRecord));

        dailyRecordService.delete(recordId);

        verify(dailyRecordRepository, times(1))
                .findOwnedByIdForUpdate(recordId, 1L);
        verify(dailyRecordRepository, times(1))
                .delete(dailyRecord);
    }

    @Test
    void 존재하지_않는_기록은_삭제x() {
        Long recordId = 999L;

        when(dailyRecordRepository.findOwnedByIdForUpdate(recordId, 1L))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> dailyRecordService.delete(recordId)
        );

        assertEquals(
                "존재하지 않는 기록입니다.",
                exception.getMessage()
        );

        verify(dailyRecordRepository, times(1))
                .findOwnedByIdForUpdate(recordId, 1L);
        verify(dailyRecordRepository, never())
                .delete(any(DailyRecord.class));
    }

    @Test
    void 특정카테고리의_월별기록을_정상조회한다() {
        Long categoryId = 1L;
        int year = 2026;
        int month = 9;
        Category category = mock(Category.class);
        DailyRecord firstRecord = mock(DailyRecord.class);
        DailyRecord secondRecord = mock(DailyRecord.class);

        when(categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L))
                .thenReturn(Optional.of(category));

        when(firstRecord.getId()).thenReturn(1L);
        when(firstRecord.getRecordDate()).thenReturn(LocalDate.of(2026, 9, 3));
        when(firstRecord.getRecordTime()).thenReturn(LocalTime.of(12, 30));

        when(secondRecord.getId()).thenReturn(2L);
        when(secondRecord.getRecordDate()).thenReturn(LocalDate.of(2026, 9, 20));
        when(secondRecord.getRecordTime()).thenReturn(LocalTime.of(18, 30));

        when(dailyRecordRepository
                .findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                        categoryId,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30)
                ))
                .thenReturn(List.of(firstRecord, secondRecord));

        List<MonthlyRecordResponse> responses =
                dailyRecordService.getMonthlyRecords(categoryId, year, month);

        assertEquals(2, responses.size());
        assertEquals(1L, responses.get(0).id());
        assertEquals(LocalDate.of(2026, 9, 3), responses.get(0).recordDate());
        assertEquals(2L, responses.get(1).id());
        assertEquals(LocalDate.of(2026, 9, 20), responses.get(1).recordDate());

        verify(categoryRepository, times(1))
                .findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L);
        verify(dailyRecordRepository, times(1))
                .findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                        categoryId,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30)
                );
    }

    @Test
    void 존재하지_않는_카테고리의_월별조회는_실패한다() {
        Long categoryId = 999L;

        when(categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> dailyRecordService.getMonthlyRecords(categoryId, 2026, 9)
        );

        assertEquals(
                "존재하지 않는 카테고리입니다.",
                exception.getMessage()
        );

        verify(categoryRepository, times(1))
                .findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L);
        verify(dailyRecordRepository, never())
                .findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                        anyLong(),
                        any(LocalDate.class),
                        any(LocalDate.class)
                );
    }

    @Test
    void 윤년_2월의_월별조회_범위는_29일까지이다() {
        Long categoryId = 1L;
        Category category = mock(Category.class);
        LocalDate startDate = LocalDate.of(2028, 2, 1);
        LocalDate endDate = LocalDate.of(2028, 2, 29);

        when(categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L))
                .thenReturn(Optional.of(category));
        when(dailyRecordRepository
                .findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                        categoryId,
                        startDate,
                        endDate
                ))
                .thenReturn(List.of());

        List<MonthlyRecordResponse> responses =
                dailyRecordService.getMonthlyRecords(categoryId, 2028, 2);

        assertTrue(responses.isEmpty());

        verify(categoryRepository, times(1))
                .findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, 1L);
        verify(dailyRecordRepository, times(1))
                .findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                        categoryId,
                        startDate,
                        endDate
                );
    }

}
