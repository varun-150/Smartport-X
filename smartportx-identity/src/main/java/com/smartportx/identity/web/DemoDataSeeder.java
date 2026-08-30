package com.smartportx.identity.web;

import com.smartportx.identity.config.SecurityProperties;
import com.smartportx.identity.domain.RoleName;
import com.smartportx.identity.repository.UserRepository;
import com.smartportx.identity.service.UserService;
import com.smartportx.identity.web.dto.CreateUserRequest;
import com.smartportx.identity.web.dto.Status;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoDataSeeder implements ApplicationRunner {

    private final SecurityProperties properties;
    private final UserService userService;
    private final UserRepository users;

    public DemoDataSeeder(SecurityProperties properties, UserService userService, UserRepository users) {
        this.properties = properties;
        this.userService = userService;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seedDemoUsers()) {
            return;
        }
        seed("admin", "admin@smartportx.local", "Port Administrator", "Admin@12345", RoleName.ADMIN);
        seed("ymanager", "ymanager@smartportx.local", "Yard Manager", "Yard@12345", RoleName.YARD_MANAGER);
        seed("gator", "gator@smartportx.local", "Gate Operator", "Gate@12345", RoleName.GATE_OPERATOR);
        seed("toperator", "toperator@smartportx.local", "Terminal Operator", "Term@12345", RoleName.TERMINAL_OP);
        seed("viewer", "viewer@smartportx.local", "Read Only User", "View@12345", RoleName.READONLY);
    }

    private void seed(String username, String email, String displayName, String password, RoleName role) {
        if (users.existsByUsernameIgnoreCase(username)) {
            return;
        }
        userService.create(new CreateUserRequest(username, email, displayName, password, role, Status.ACTIVE), "system");
    }
}