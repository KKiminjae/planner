package com.planner.photo_calendar.record;

import com.planner.photo_calendar.category.Category;
import com.planner.photo_calendar.photo.PhotoLinkService;
import com.planner.photo_calendar.category.CategoryRepository;
import com.planner.photo_calendar.record.dto.request.DailyRecordCreateRequest;
import com.planner.photo_calendar.record.dto.request.DailyRecordUpdateRequest;
import com.planner.photo_calendar.record.dto.response.DailyRecordResponse;
import com.planner.photo_calendar.record.dto.response.MonthlyRecordResponse;
import lombok.RequiredArgsConstructor;
import com.planner.photo_calendar.auth.CurrentOwner;
import com.planner.photo_calendar.common.time.ApplicationTime;
import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyRecordService {
    private final CurrentOwner currentOwner;
    private final DailyRecordRepository recordRepository;
    private final CategoryRepository categoryRepository;
    private final PhotoLinkService photoLinkService;
    private final ApplicationTime applicationTime;

    @Transactional
    public DailyRecordResponse create(DailyRecordCreateRequest request){
        Category category = categoryRepository.findActiveOwnedByIdForUpdate(request.categoryId(), currentOwner.id())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "존재하지 않는 카테고리 입니다."));

        if(request.recordDate().isAfter(applicationTime.today())){
            throw new BusinessException(ErrorCode.FUTURE_RECORD_NOT_ALLOWED, "미래 날짜에는 기록할 수 없습니다.");
        }

        boolean exists = recordRepository.existsByCategoryIdAndRecordDate(category.getId(), request.recordDate());

        if(exists){
            throw new BusinessException(ErrorCode.DUPLICATE_DAILY_RECORD, "해당 날짜에 이미 기록이 존재합니다.");
        }

        DailyRecord dailyRecord = new DailyRecord(
                category,
                request.recordDate(),
                request.recordTime(),
                request.memo(),
                null);
        DailyRecord savedRecord = recordRepository.save(dailyRecord);

        photoLinkService.replace(savedRecord.getId(), null, request.imageKey());
        savedRecord.changeImage(request.imageKey());
        return DailyRecordResponse.from(savedRecord);
    }

    @Transactional(readOnly = true)
    public DailyRecordResponse getRecord(Long id) {
        DailyRecord dailyRecord = recordRepository.findByIdAndCategoryOwnerId(id, currentOwner.id())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.RECORD_NOT_FOUND, "존재하지 않는 기록입니다."));

        return DailyRecordResponse.from(dailyRecord);
    }

    @Transactional
    public DailyRecordResponse update(Long id, DailyRecordUpdateRequest request){
        DailyRecord dailyRecord = recordRepository.findOwnedByIdForUpdate(id, currentOwner.id())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.RECORD_NOT_FOUND, "존재하지 않는 기록 입니다."));

        if (!request.isImageChangeValid()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String nextKey = Boolean.TRUE.equals(request.removeImage()) ? null
                : request.imageKey() == null ? dailyRecord.getImageKey() : request.imageKey();
        photoLinkService.replace(id, dailyRecord.getImageKey(), nextKey);
        dailyRecord.changeImage(nextKey);
        dailyRecord.update(request.recordTime(), request.memo());

        return DailyRecordResponse.from(dailyRecord);
    }

    @Transactional(readOnly = true)
    public List<DailyRecordResponse> getDailyRecords(LocalDate date){
        return recordRepository
                .findAllByRecordDateAndCategoryOwnerIdOrderByRecordTimeAsc(date, currentOwner.id())
                .stream()
                .map(DailyRecordResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long id){
        DailyRecord dailyRecord = recordRepository.findOwnedByIdForUpdate(id, currentOwner.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND, "존재하지 않는 기록입니다."));

        photoLinkService.replace(id, dailyRecord.getImageKey(), null);
        recordRepository.delete(dailyRecord);
    }

    @Transactional(readOnly = true)
    public List<MonthlyRecordResponse> getMonthlyRecords(Long categoryId, int year, int month) {
        categoryRepository.findByIdAndOwnerIdAndDeletedAtIsNull(categoryId, currentOwner.id())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "존재하지 않는 카테고리입니다."));
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
