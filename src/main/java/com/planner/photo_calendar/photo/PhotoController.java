package com.planner.photo_calendar.photo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "photo.storage.enabled", havingValue = "true")
public class PhotoController {
    private final PhotoService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoUploadResponse upload(@RequestParam("file") MultipartFile file) {
        return service.upload(file);
    }
}
