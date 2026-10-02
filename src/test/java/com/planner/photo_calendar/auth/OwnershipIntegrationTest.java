package com.planner.photo_calendar.auth;

import com.planner.photo_calendar.calendar.CalendarService;
import com.planner.photo_calendar.category.*;
import com.planner.photo_calendar.category.dto.request.*;
import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import com.planner.photo_calendar.completionhistory.*;
import com.planner.photo_calendar.record.*;
import com.planner.photo_calendar.record.dto.request.*;
import com.planner.photo_calendar.record.dto.response.DailyRecordResponse;
import com.planner.photo_calendar.photo.*;
import com.planner.photo_calendar.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnershipIntegrationTest extends MySqlIntegrationTest {
    @Autowired CategoryRepository categories;
    @Autowired CategoryService categoryService;
    @Autowired DailyRecordRepository records;
    @Autowired DailyRecordService recordService;
    @Autowired CompletionHistoryRepository histories;
    @Autowired CalendarService calendarService;
    @Autowired PhotoRepository photos;
    @Autowired PhotoLinkService links;
    @Autowired CurrentOwner currentOwner;
    private final LocalDate today = LocalDate.now();

    @Test
    void 목록과_표시순서는_소유자별로_격리된다() {
        Category foreign = categories.saveAndFlush(new Category(2L, "다른 카테고리", "#FF0000", 99, false));
        Category own = categories.saveAndFlush(new Category(1L, "내 카테고리", "#FF0000", 1, false));
        assertThat(categoryService.getCategories()).extracting(response -> response.id()).contains(own.getId()).doesNotContain(foreign.getId());
        assertThat(categoryService.create(new CategoryCreateRequest("새 카테고리", "#FF0000", false)).displayOrder()).isEqualTo(2);
        DailyRecord ownRecord = records.saveAndFlush(new DailyRecord(own, today, LocalTime.NOON, "내 기록", null));
        DailyRecord foreignRecord = records.saveAndFlush(new DailyRecord(foreign, today, LocalTime.NOON, "다른 기록", null));
        assertThat(recordService.getDailyRecords(today)).extracting(DailyRecordResponse::id)
                .contains(ownRecord.getId()).doesNotContain(foreignRecord.getId());
    }

    @Test
    void 다른_소유자의_카테고리_생성참조_수정_삭제_월간_연간조회를_거부한다() {
        Category foreign = categories.saveAndFlush(new Category(2L, "다른 카테고리", "#FF0000", 1, false));
        assertError(() -> recordService.create(new DailyRecordCreateRequest(foreign.getId(), today, LocalTime.NOON, "기록")), ErrorCode.CATEGORY_NOT_FOUND);
        assertError(() -> categoryService.update(foreign.getId(), new CategoryUpdateRequest("변경", "#000000", false)), ErrorCode.CATEGORY_NOT_FOUND);
        assertError(() -> categoryService.delete(foreign.getId()), ErrorCode.CATEGORY_NOT_FOUND);
        assertError(() -> recordService.getMonthlyRecords(foreign.getId(), today.getYear(), today.getMonthValue()), ErrorCode.CATEGORY_NOT_FOUND);
        assertError(() -> categoryService.getAnnualRecords(foreign.getId(), today.getYear()), ErrorCode.CATEGORY_NOT_FOUND);
        assertThat(foreign.getDeletedAt()).isNull();
        assertThat(foreign.getName()).isEqualTo("다른 카테고리");
    }

    @Test
    void 순서변경에_다른_소유자의_ID를_섞으면_자신의_순서도_롤백한다() {
        Category own = categories.saveAndFlush(new Category(1L, "내 카테고리", "#FF0000", 1, false));
        Category foreign = categories.saveAndFlush(new Category(2L, "다른 카테고리", "#FF0000", 1, false));
        assertError(() -> categoryService.reorder(new CategoryReorderRequest(List.of(foreign.getId()))), ErrorCode.INVALID_CATEGORY_ORDER);
        assertThat(own.getDisplayOrder()).isEqualTo(1);
        assertThat(foreign.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void 달력의_전체수와_완료수는_다른_소유자의_기록과_완료이력을_제외한다() {
        Category own = categories.saveAndFlush(new Category(1L, "내 카테고리", "#FF0000", 1, false));
        Category foreign = categories.saveAndFlush(new Category(2L, "다른 카테고리", "#FF0000", 1, false));
        Category deleted = categories.saveAndFlush(new Category(2L, "다른 삭제 카테고리", "#FF0000", 2, false));
        records.saveAndFlush(new DailyRecord(own, today, LocalTime.NOON, "내 기록", null));
        records.saveAndFlush(new DailyRecord(foreign, today, LocalTime.NOON, "다른 기록", null));
        histories.saveAndFlush(new CompletionHistory(deleted, new CompletionHistoryId(deleted.getId(), today)));
        deleted.delete();
        categories.flush();
        assertThat(calendarService.getIntegratedCalender(today.getYear(), today.getMonthValue()))
                .filteredOn(day -> day.date().equals(today)).singleElement().satisfies(day -> {
                    assertThat(day.totalCount()).isEqualTo(1);
                    assertThat(day.completedCount()).isEqualTo(1);
                });
    }

    @Test
    void 미연결_사진도_타인_기록에_연결하거나_조회할_수_없다() {
        Photo foreign = photo(2L);
        Category own = categories.saveAndFlush(new Category(1L, "내 카테고리", "#FF0000", 1, false));
        DailyRecord ownRecord = records.saveAndFlush(new DailyRecord(own, today, LocalTime.NOON, "내 기록", null));
        assertError(() -> links.replace(ownRecord.getId(), null, foreign.getImageKey()), ErrorCode.PHOTO_NOT_FOUND);
        assertThat(foreign.getRecordId()).isNull();
        PhotoStorage storage = mock(PhotoStorage.class);
        PhotoReadService reader = new PhotoReadService(currentOwner, photos, storage);
        assertError(() -> reader.getUrl(ownRecord.getId()), ErrorCode.PHOTO_NOT_FOUND);
        verifyNoInteractions(storage);
    }

    @Test
    void 사진_URL은_사진과_기록의_소유자가_모두_일치할_때만_발급한다() {
        Category foreignCategory = categories.saveAndFlush(new Category(2L, "다른 카테고리", "#FF0000", 1, false));
        Photo foreignPhoto = photo(2L);
        DailyRecord foreignRecord = records.saveAndFlush(new DailyRecord(foreignCategory, today, LocalTime.NOON, "다른 기록", foreignPhoto.getImageKey()));
        foreignPhoto.attach(foreignRecord.getId());
        photos.flush();
        assertThat(photos.findReadableByRecordId(foreignRecord.getId(), 1L)).isEmpty();
        assertThat(photos.findReadableByRecordId(foreignRecord.getId(), 2L)).isPresent();
        PhotoStorage storage = mock(PhotoStorage.class);
        assertError(() -> new PhotoReadService(currentOwner, photos, storage).getUrl(foreignRecord.getId()), ErrorCode.PHOTO_NOT_FOUND);
        verifyNoInteractions(storage);
    }

    @Test
    void 새_사진은_인증된_소유자로_등록한다() throws Exception {
        OwnerPrincipal other = new OwnerPrincipal(2L, "other", "");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(other, null, other.getAuthorities()));
        PhotoStorage storage = mock(PhotoStorage.class);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        PhotoUploadResponse response = new PhotoService(currentOwner, storage, photos)
                .upload(new MockMultipartFile("file", bytes.toByteArray()));
        assertThat(photos.findById(response.imageKey()).orElseThrow().getOwnerId()).isEqualTo(2L);
    }

    @Test
    void 카테고리_삭제도_사진_해제시각을_남기고_자신의_완료이력을_보존한다() {
        Category own = categories.saveAndFlush(new Category(1L, "내 카테고리", "#FF0000", 1, false));
        Photo photo = photo(1L);
        DailyRecordResponse record = recordService.create(new DailyRecordCreateRequest(own.getId(), today, LocalTime.NOON, "내 기록", photo.getImageKey()));
        categoryService.delete(own.getId());
        assertThat(records.findById(record.id())).isEmpty();
        assertThat(photo.getRecordId()).isNull();
        assertThat(photo.getUnlinkedAt()).isNotNull();
        assertThat(histories.countById_RecordDateAndCategoryOwnerId(today, 1L)).isEqualTo(1);
        assertThat(histories.countById_RecordDateAndCategoryOwnerId(today, 2L)).isZero();
    }

    private Photo photo(Long ownerId) {
        Photo photo = new Photo(ownerId, "photos/" + UUID.randomUUID() + ".png", "image/png", 100);
        photo.markUploaded();
        return photos.saveAndFlush(photo);
    }

    private void assertError(Runnable action, ErrorCode error) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(error));
    }
}
