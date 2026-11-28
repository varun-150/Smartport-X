package com.smartportx.identity.web;

import com.smartportx.identity.config.SecurityProperties;
import com.smartportx.identity.repository.UserRepository;
import com.smartportx.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DemoDataSeederProductionGuardTest {

    @Test
    void neverSeedsDemoUsersWhenProdProfileIsActive() {
        UserService userService = mock(UserService.class);
        UserRepository users = mock(UserRepository.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        // Flag deliberately left enabled to simulate a misconfiguration.
        SecurityProperties properties = new SecurityProperties(null, 12, 5, true);
        new DemoDataSeeder(properties, userService, users, environment).run(null);

        verifyNoInteractions(userService, users);
    }

    @Test
    void skipsSeedingWhenFlagDisabled() {
        UserService userService = mock(UserService.class);
        UserRepository users = mock(UserRepository.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        SecurityProperties properties = new SecurityProperties(null, 12, 5, false);
        new DemoDataSeeder(properties, userService, users, environment).run(null);

        verifyNoInteractions(userService, users);
    }
}
