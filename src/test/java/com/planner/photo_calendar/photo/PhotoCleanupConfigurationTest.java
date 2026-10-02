package com.planner.photo_calendar.photo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhotoCleanupConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PhotoCleanupConfiguration.class)
            .withBean(PhotoRepository.class, () -> mock(PhotoRepository.class))
            .withBean(PhotoCleanupTransactions.class, () -> mock(PhotoCleanupTransactions.class))
            .withBean(PhotoStorage.class, () -> mock(PhotoStorage.class));

    @Test
    void 저장소가_활성화되면_기본_정리작업을_스케줄러에_등록한다() {
        context.withPropertyValues("photo.storage.enabled=true").run(application -> {
            assertThat(application).hasSingleBean(PhotoCleanupWorker.class);
            assertThat(application.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(1);
            verifyNoInteractions(application.getBean(PhotoStorage.class));
        });
    }

    @Test
    void 저장소가_비활성화되면_정리작업과_스케줄러를_등록하지_않는다() {
        context.withPropertyValues("photo.storage.enabled=false").run(application -> {
            assertThat(application).doesNotHaveBean(PhotoCleanupWorker.class);
            assertThat(application).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

    @Test
    void 정리기능을_끄면_사진기능이_활성화되어도_자동삭제를_실행하지_않는다() {
        context.withPropertyValues("photo.storage.enabled=true", "photo.cleanup.enabled=false").run(application -> {
            assertThat(application).doesNotHaveBean(PhotoCleanupWorker.class);
            assertThat(application.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
            verifyNoInteractions(application.getBean(PhotoStorage.class));
        });
    }
}
