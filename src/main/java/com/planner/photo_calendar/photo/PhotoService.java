package com.planner.photo_calendar.photo;

import com.planner.photo_calendar.common.time.ApplicationTime;

import com.planner.photo_calendar.common.exception.BusinessException;
import com.planner.photo_calendar.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import com.planner.photo_calendar.auth.CurrentOwner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "photo.storage.enabled", havingValue = "true")
public class PhotoService {
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000;
    private final CurrentOwner currentOwner;
    private final PhotoStorage storage;
    private final PhotoRepository repository;

    public PhotoUploadResponse upload(MultipartFile file) {
        Long ownerId = currentOwner.id();
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }
        byte[] bytes;
        String format;
        try {
            bytes = file.getBytes();
            format = validate(bytes);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
        String key = "photos/" + UUID.randomUUID() + "." + format;
        // 먼저 메타데이터를 남겨 S3 실패 또는 완료 정보 저장 실패도 추적합니다.
        repository.saveAndFlush(new Photo(ownerId, key, "image/" + format, bytes.length));
        storage.upload(key, bytes, "image/" + format);
        // 조건부 갱신으로 삭제 중이거나 삭제된 메타데이터를 다시 생성하지 않습니다.
        if (repository.markUploadedIfAvailable(key, ApplicationTime.nowUtc()) != 1) {
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
        return new PhotoUploadResponse(key);
    }

    private String validate(byte[] bytes) throws IOException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE);
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw new BusinessException(ErrorCode.INVALID_IMAGE);
                }
                reader.setInput(input);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
                }
                reader.read(0);
                return format;
            } finally {
                reader.dispose();
            }
        }
    }
}
