package com.smartportx.identity.service;

import com.smartportx.identity.domain.User;
import com.smartportx.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AccountSecurity {

    private static final Duration SHORT_LOCKOUT = Duration.ofMinutes(15);
    private static final Duration LONG_LOCKOUT = Duration.ofHours(1);
    private static final int LOCK_THRESHOLD = 15;

    private final UserRepository users;

    public AccountSecurity(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public void registerLoginFailure(User user) {
        int fails = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(fails);
        Instant now = Instant.now();
        if (fails >= LOCK_THRESHOLD) {
            user.setEnabled(false);
            user.setLockoutUntil(null);
        } else if (fails == 10) {
            user.setLockoutUntil(now.plus(LONG_LOCKOUT));
        } else if (fails >= 5) {
            user.setLockoutUntil(now.plus(SHORT_LOCKOUT));
        } else {
            user.setLockoutUntil(null);
        }
        users.save(user);
    }

    @Transactional
    public void resetLoginFailures(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        users.save(user);
    }
}