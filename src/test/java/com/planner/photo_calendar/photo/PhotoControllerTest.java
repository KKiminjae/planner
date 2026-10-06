package com.planner.photo_calendar.photo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = PhotoController.class, properties = "photo.storage.enabled=true")
class PhotoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PhotoService service;

    @Test
    void 사진_업로드는_파일을_전달하고_201과_이미지_키를_반환한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});
        when(service.upload(any())).thenReturn(new PhotoUploadResponse("photos/test.png"));
        mvc.perform(multipart("/api/photos").file(file)).andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"imageKey":"photos/test.png"}
                        """));
        verify(service).upload(file);
    }

    @Test
    void 파일이_없으면_서비스_호출_없이_400을_반환한다() throws Exception {
        mvc.perform(multipart("/api/photos")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void 업로드_용량_제한_오류는_413과_공통_오류를_반환한다() throws Exception {
        when(service.upload(any())).thenThrow(new MaxUploadSizeExceededException(20 * 1024 * 1024));
        mvc.perform(multipart("/api/photos").file(new MockMultipartFile("file", new byte[]{1})))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
    }
}
