package com.planner.photo_calendar.photo;

public interface PhotoStorage {
    PhotoReadResponse createReadUrl(String key);

    void delete(String key);

    void upload(String key, byte[] bytes, String contentType);
}
