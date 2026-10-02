package com.planner.photo_calendar.photo;

import java.time.Instant;

public record PhotoReadResponse(String imageUrl, Instant expiresAt) { }
