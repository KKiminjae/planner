package com.planner.photo_calendar.photo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import java.time.Duration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "photo.storage.enabled", havingValue = "true")
public class PhotoStorageConfiguration {
    @Bean(destroyMethod = "close")
    DefaultCredentialsProvider photoCredentialsProvider(@Value("${photo.storage.profile:}") String profile) {
        var builder = DefaultCredentialsProvider.builder();
        if (!profile.isBlank()) {
            builder.profileName(profile);
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(@Value("${photo.storage.region}") String region,
                            DefaultCredentialsProvider photoCredentialsProvider) {
        return S3Presigner.builder().region(Region.of(region)).credentialsProvider(photoCredentialsProvider).build();
    }

    @Bean(destroyMethod = "close")
    S3Client s3Client(@Value("${photo.storage.region}") String region,
                      @Value("${photo.storage.bucket}") String bucket,
                      DefaultCredentialsProvider photoCredentialsProvider) {
        if (bucket.isBlank()) {
            throw new IllegalStateException("PHOTO_S3_BUCKET 설정이 필요합니다.");
        }
        return S3Client.builder().region(Region.of(region))
                .credentialsProvider(photoCredentialsProvider)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(60))
                        .apiCallAttemptTimeout(Duration.ofSeconds(30)).build())
                .build();
    }
}
