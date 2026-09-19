package com.planner.photo_calendar.record;

import com.planner.photo_calendar.category.Category;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.record.dto.request.DailyRecordCreateRequest;
import com.planner.photo_calendar.record.dto.request.DailyRecordUpdateRequest;
import com.planner.photo_calendar.record.dto.response.DailyRecordResponse;
import com.planner.photo_calendar.record.dto.response.MonthlyRecordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyRecordService {
    private final DailyRecordRepository recordRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public DailyRecordResponse create(DailyRecordCreateRequest request){
        Category category = categoryRepository.findByIdAndDeletedAtIsNull(request.categoryId())
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 카테고리 입니다."));

        boolean exists = recordRepository.existsByCategoryIdAndRecordDate(category.getId(), request.recordDate());

        if(exists){
            throw new IllegalArgumentException("해당 날짜에 이미 기록이 존재합니다.");
        }

        DailyRecord dailyRecord = new DailyRecord(
                category,
                request.recordDate(),
                request.recordTime(),
                request.memo(),
                null);
        DailyRecord savedRecord = recordRepository.save(dailyRecord);

        return DailyRecordResponse.from(savedRecord);
    }

    @Transactional(readOnly = true)
    public DailyRecordResponse getRecord(Long id) {
        DailyRecord dailyRecord = recordRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 기록입니다."));

        return DailyRecordResponse.from(dailyRecord);
    }

    @Transactional
    public DailyRecordResponse update(Long id, DailyRecordUpdateRequest request){
        DailyRecord dailyRecord = recordRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 기록 입니다."));

        dailyRecord.update(request.recordTime(), request.memo());

        return DailyRecordResponse.from(dailyRecord);
    }

    @Transactional(readOnly = true)
    public List<DailyRecordResponse> getDailyRecords(LocalDate date){
        return recordRepository
                .findAllByRecordDateOrderByRecordTimeAsc(date)
                .stream()
                .map(DailyRecordResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long id){
        DailyRecord dailyRecord = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 기록입니다."));

        recordRepository.delete(dailyRecord);
    }

    @Transactional(readOnly = true)
    public List<MonthlyRecordResponse> getMonthlyRecords(Long categoryId, int year, int month) {
        categoryRepository.findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 카테고리입니다."));
        YearMonth yearMonth = YearMonth.of(year, month);

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return recordRepository
                .findAllByCategoryIdAndRecordDateBetweenOrderByRecordDateAsc(
                        categoryId,
                        startDate,
                        endDate
                )
                .stream()
                .map(MonthlyRecordResponse::from)
                .toList();
    }
}
