package com.planner.photo_calendar.photo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "photo.storage.enabled", havingValue = "true")
public class PhotoCleanupConfiguration {
    @Bean
    @ConditionalOnProperty(name = "photo.cleanup.enabled", havingValue = "true", matchIfMissing = true)
    PhotoCleanupWorker photoCleanupWorker(PhotoRepository repository, PhotoCleanupTransactions transactions,
                                          PhotoStorage storage) {
        return new PhotoCleanupWorker(repository, transactions, storage, Clock.systemUTC());
    }
}
