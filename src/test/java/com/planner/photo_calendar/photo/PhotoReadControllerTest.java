package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = PhotoReadController.class, properties = "photo.storage.enabled=true")
class PhotoReadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PhotoReadService service;

    @Test
    void 기록_ID로_URL과_UTC_만료시각을_받고_응답을_캐시하지_않는다() throws Exception {
        when(service.getUrl(7L)).thenReturn(new PhotoReadResponse("https://example.com/photo?signature=test",
                Instant.parse("2026-10-01T10:10:00Z")));
        mvc.perform(get("/api/records/7/photo-url")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {"imageUrl":"https://example.com/photo?signature=test","expiresAt":"2026-10-01T10:10:00Z"}
                        """));
        verify(service).getUrl(7L);
    }

    @Test
    void 사진이_없으면_404_공통_오류를_반환한다() throws Exception {
        when(service.getUrl(7L)).thenThrow(new BusinessException(ErrorCode.PHOTO_NOT_FOUND));
        mvc.perform(get("/api/records/7/photo-url")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"));
    }

    @Test
    void 서명_실패는_503_공통_오류를_반환한다() throws Exception {
        when(service.getUrl(7L)).thenThrow(new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE));
        mvc.perform(get("/api/records/7/photo-url")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IMAGE_STORAGE_UNAVAILABLE"));
    }

    @Test
    void 숫자가_아닌_기록_ID는_서비스_호출_없이_400을_반환한다() throws Exception {
        mvc.perform(get("/api/records/invalid/photo-url")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }
}
