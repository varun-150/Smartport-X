package com.smartportx.identity.web;

import com.smartportx.identity.domain.RoleName;
import com.smartportx.identity.service.UserQueryService;
import com.smartportx.identity.service.UserService;
import com.smartportx.identity.web.dto.CreateUserRequest;
import com.smartportx.identity.web.dto.Status;
import com.smartportx.identity.web.dto.UpdateUserRequest;
import com.smartportx.identity.web.dto.UserResponse;
import com.smartportx.identity.web.dto.UsersPage;
import com.smartportx.identity.web.dto.UsersQuery;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;
    private final UserQueryService userQueryService;

    public UserAdminController(UserService userService, UserQueryService userQueryService) {
        this.userService = userService;
        this.userQueryService = userQueryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
        return userService.create(request, username(jwt));
    }

    @GetMapping
    public UsersPage list(@RequestParam(required = false) Integer limit,
                          @RequestParam(required = false) String cursor,
                          @RequestParam(required = false, name = "filter[role]") RoleName role,
                          @RequestParam(required = false, name = "filter[status]") Status status,
                          @RequestParam(required = false, name = "filter[search]") String search,
                          @RequestParam(required = false) String sort) {
        return userQueryService.list(new UsersQuery(limit, cursor, role, status, search, sort));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
        return userService.update(id, request, username(jwt));
    }

    @PatchMapping("/{id}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.disable(id, username(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        userService.remove(id, username(jwt));
    }

    private String username(Jwt jwt) {
        return jwt.getClaimAsString("username");
    }
}