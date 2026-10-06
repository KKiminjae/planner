package com.planner.photo_calendar.record;

import com.planner.photo_calendar.record.dto.request.*;
import com.planner.photo_calendar.record.dto.response.DailyRecordResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(DailyRecordController.class)
class DailyRecordControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DailyRecordService service;

    private final LocalDate date = LocalDate.of(2026, 9, 15);
    private final LocalTime time = LocalTime.of(9, 30);
    private final DailyRecordResponse record = new DailyRecordResponse(
            11L, 7L, "운동", date, time, "아침 운동", null);
    private final String response = """
            {"id":11,"categoryId":7,"categoryName":"운동","recordDate":"2026-09-15",
             "recordTime":"09:30:00","memo":"아침 운동","imageKey":null}
            """;

    @Test
    void 기록_생성은_날짜와_시간을_변환해_전달하고_201과_기록을_반환한다() throws Exception {
        DailyRecordCreateRequest request = new DailyRecordCreateRequest(7L, date, time, "아침 운동");
        when(service.create(request)).thenReturn(record);
        mvc.perform(post("/api/records").contentType("application/json").content("""
                        {"categoryId":7,"recordDate":"2026-09-15","recordTime":"09:30:00","memo":"아침 운동"}
                        """))
                .andExpect(status().isCreated()).andExpect(content().json(response));
        verify(service).create(request);
    }

    @Test
    void 단건_기록_조회는_ID를_전달하고_기록을_반환한다() throws Exception {
        when(service.getRecord(11L)).thenReturn(record);
        mvc.perform(get("/api/records/11")).andExpect(status().isOk())
                .andExpect(content().json(response));
        verify(service).getRecord(11L);
    }

    @Test
    void 일별_기록_조회는_날짜를_변환해_전달하고_JSON_배열을_반환한다() throws Exception {
        when(service.getDailyRecords(date)).thenReturn(List.of(record));
        mvc.perform(get("/api/records").param("date", "2026-09-15"))
                .andExpect(status().isOk()).andExpect(content().json("[" + response + "]"));
        verify(service).getDailyRecords(date);
    }

    @Test
    void 기록_수정은_ID와_변환된_요청값을_전달하고_기록을_반환한다() throws Exception {
        DailyRecordUpdateRequest request = new DailyRecordUpdateRequest(time, "아침 운동");
        when(service.update(11L, request)).thenReturn(record);
        mvc.perform(patch("/api/records/11").contentType("application/json").content("""
                        {"recordTime":"09:30:00","memo":"아침 운동"}
                        """))
                .andExpect(status().isOk()).andExpect(content().json(response));
        verify(service).update(11L, request);
    }

    @Test
    void 기록_삭제는_ID를_전달하고_204와_빈_본문을_반환한다() throws Exception {
        mvc.perform(delete("/api/records/11"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(11L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"memo\":null}", "{\"memo\":\"\"}", "{\"memo\":\" \"}"})
    void 메모는_생성과_수정에서_선택사항이다(String memoJson) throws Exception {
        String memoFields = memoJson.substring(1, memoJson.length() - 1);
        String suffix = memoFields.isEmpty() ? "" : "," + memoFields;
        mvc.perform(post("/api/records").contentType("application/json")
                        .content("{\"categoryId\":7,\"recordDate\":\"2026-09-15\",\"recordTime\":\"09:30:00\"" + suffix + "}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/records/11").contentType("application/json")
                        .content("{\"recordTime\":\"09:30:00\"" + suffix + "}"))
                .andExpect(status().isOk());
        String expectedMemo = memoJson.contains(" ") ? " " : "";
        verify(service).create(new DailyRecordCreateRequest(7L, date, time, expectedMemo));
        verify(service).update(11L, new DailyRecordUpdateRequest(time, expectedMemo));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"recordDate\":\"2026-09-15\",\"recordTime\":\"09:30:00\",\"memo\":\"기록\"}",
            "{\"categoryId\":7,\"recordTime\":\"09:30:00\",\"memo\":\"기록\"}",
            "{\"categoryId\":7,\"recordDate\":\"2026-09-15\",\"memo\":\"기록\"}"
    })
    void 기록_생성은_필수값이_없으면_서비스_호출_없이_400을_반환한다(String body) throws Exception {
        mvc.perform(post("/api/records").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"memo\":\"기록\"}"})
    void 기록_수정은_필수값이_없으면_서비스_호출_없이_400을_반환한다(String body) throws Exception {
        mvc.perform(patch("/api/records/11").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"invalid,09:30:00", "2026-09-15,invalid", "2026-09-15,25:00:00"})
    void 기록_생성은_날짜나_시간_형식이_잘못되면_서비스_호출_없이_400을_반환한다(String date, String time) throws Exception {
        String body = """
                {"categoryId":7,"recordDate":"%s","recordTime":"%s","memo":"기록"}
                """.formatted(date, time);
        mvc.perform(post("/api/records").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void 기록_수정은_시간_형식이_잘못되면_서비스_호출_없이_400을_반환한다() throws Exception {
        mvc.perform(patch("/api/records/11").contentType("application/json").content("""
                        {"recordTime":"25:00:00","memo":"기록"}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "2026-09-31"})
    void 일별_조회는_날짜가_없거나_잘못되면_서비스_호출_없이_400을_반환한다(String date) throws Exception {
        mvc.perform(get("/api/records").param("date", date))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void 일별_기록이_없으면_빈_JSON_배열을_반환한다() throws Exception {
        when(service.getDailyRecords(date)).thenReturn(List.of());
        mvc.perform(get("/api/records").param("date", "2026-09-15"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).getDailyRecords(date);
    }
    private final String imageKey = "photos/12345678-1234-1234-1234-123456789abc.png";

    @Test
    void 사진키를_포함한_기록_생성은_키를_전달하고_응답에_반영한다() throws Exception {
        DailyRecordCreateRequest request = new DailyRecordCreateRequest(7L, date, time, "기록", imageKey);
        when(service.create(request)).thenReturn(new DailyRecordResponse(
                11L, 7L, "운동", date, time, "기록", imageKey));
        mvc.perform(post("/api/records").contentType("application/json").content("""
                {"categoryId":7,"recordDate":"2026-09-15","recordTime":"09:30:00",
                 "memo":"기록","imageKey":"%s"}
                """.formatted(imageKey)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.imageKey").value(imageKey))
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        verify(service).create(request);
    }

    @Test
    void 사진_교체와_제거_요청을_구분해_전달한다() throws Exception {
        DailyRecordUpdateRequest replace = new DailyRecordUpdateRequest(time, "기록", imageKey, false);
        DailyRecordUpdateRequest remove = new DailyRecordUpdateRequest(time, "기록", null, true);
        when(service.update(11L, replace)).thenReturn(record);
        when(service.update(11L, remove)).thenReturn(record);
        mvc.perform(patch("/api/records/11").contentType("application/json").content("""
                {"recordTime":"09:30:00","memo":"기록","imageKey":"%s","removeImage":false}
                """.formatted(imageKey))).andExpect(status().isOk());
        mvc.perform(patch("/api/records/11").contentType("application/json").content("""
                {"recordTime":"09:30:00","memo":"기록","removeImage":true}
                """)).andExpect(status().isOk());
        verify(service).update(11L, replace);
        verify(service).update(11L, remove);
    }

    @Test
    void 사진_교체와_제거를_동시에_요청하면_400으로_거부한다() throws Exception {
        mvc.perform(patch("/api/records/11").contentType("application/json").content("""
                {"recordTime":"09:30:00","memo":"기록","imageKey":"%s","removeImage":true}
                """.formatted(imageKey)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "../secret.png", "https://example.com/photo.png",
            "photos/not-uploaded.png", "photos/12345678-1234-1234-1234-123456789abc.gif"})
    void 잘못된_사진키는_생성과_수정에서_서비스_호출_없이_거부한다(String key) throws Exception {
        String create = """
                {"categoryId":7,"recordDate":"2026-09-15","recordTime":"09:30:00","memo":"기록","imageKey":"%s"}
                """.formatted(key);
        String update = """
                {"recordTime":"09:30:00","memo":"기록","imageKey":"%s"}
                """.formatted(key);
        mvc.perform(post("/api/records").contentType("application/json").content(create))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(patch("/api/records/11").contentType("application/json").content(update))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void 존재하지_않거나_이미_연결된_사진의_오류를_HTTP_응답으로_반환한다() throws Exception {
        when(service.create(any())).thenThrow(new com.planner.photo_calendar.common.exception.BusinessException(
                com.planner.photo_calendar.common.exception.ErrorCode.PHOTO_NOT_FOUND));
        mvc.perform(post("/api/records").contentType("application/json").content("""
                {"categoryId":7,"recordDate":"2026-09-15","recordTime":"09:30:00","memo":"기록","imageKey":"%s"}
                """.formatted(imageKey)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"));
        when(service.update(anyLong(), any())).thenThrow(new com.planner.photo_calendar.common.exception.BusinessException(
                com.planner.photo_calendar.common.exception.ErrorCode.PHOTO_ALREADY_LINKED));
        mvc.perform(patch("/api/records/11").contentType("application/json").content("""
                {"recordTime":"09:30:00","memo":"기록","imageKey":"%s"}
                """.formatted(imageKey)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHOTO_ALREADY_LINKED"));
    }

}
