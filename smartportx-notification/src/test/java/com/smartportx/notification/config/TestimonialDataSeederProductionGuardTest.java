package com.smartportx.notification.config;

import com.smartportx.notification.domain.TestimonialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TestimonialDataSeederProductionGuardTest {

    @Test
    void neverSeedsTestimonialsWhenProdProfileIsActive() {
        TestimonialRepository repository = mock(TestimonialRepository.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        new TestimonialDataSeeder(repository, true, environment).run(null);

        verifyNoInteractions(repository);
    }

    @Test
    void skipsSeedingWhenFlagDisabled() {
        TestimonialRepository repository = mock(TestimonialRepository.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        new TestimonialDataSeeder(repository, false, environment).run(null);

        verifyNoInteractions(repository);
    }
}
