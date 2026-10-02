package com.planner.photo_calendar.photo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "photo.storage.enabled", havingValue = "true")
public class PhotoReadController {
    private final PhotoReadService service;

    @GetMapping("/{id}/photo-url")
    public ResponseEntity<PhotoReadResponse> getUrl(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getUrl(id));
    }
}
