package com.smartportx.identity.service;

import com.smartportx.identity.domain.User;
import com.smartportx.identity.error.InvalidQueryException;
import com.smartportx.identity.repository.UserRepository;
import com.smartportx.identity.web.dto.Status;
import com.smartportx.identity.web.dto.UserResponse;
import com.smartportx.identity.web.dto.UserResponses;
import com.smartportx.identity.web.dto.UsersPage;
import com.smartportx.identity.web.dto.UsersQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class UserQueryService {

    private final UserRepository users;

    public UserQueryService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UsersPage list(UsersQuery query) {
        int limit = query.effectiveLimit();
        CursorDirection direction = parseSort(query.sort());
        Specification<User> filter = buildSpec(query, false, direction);
        long total = users.count(filter);
        Sort order = direction == CursorDirection.DESC
                ? Sort.by("createdAt").descending().and(Sort.by("id").descending())
                : Sort.by("createdAt").ascending().and(Sort.by("id").ascending());
        Page<User> page = users.findAll(buildSpec(query, true, direction), PageRequest.of(0, limit + 1, order));
        boolean hasMore = page.getContent().size() > limit;
        List<User> items = hasMore ? page.getContent().subList(0, limit) : page.getContent();
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            nextCursor = UserSpecs.encodeCursor(items.get(items.size() - 1));
        }
        List<UserResponse> data = items.stream().map(UserResponses::from).toList();
        return new UsersPage(new UsersPage.Pagination(nextCursor, hasMore, total, limit), data);
    }

    private static CursorDirection parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return CursorDirection.DESC;
        }
        String[] parts = sort.split(",");
        if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
            throw new InvalidQueryException("sort must be '<field>,<direction>'");
        }
        String field = parts[0].trim();
        String direction = parts[1].trim();
        if (!"createdAt".equals(field)) {
            throw new InvalidQueryException("unsupported sort field '" + field + "' (only createdAt)");
        }
        return switch (direction) {
            case "asc" -> CursorDirection.ASC;
            case "desc" -> CursorDirection.DESC;
            default -> throw new InvalidQueryException("unsupported sort direction '" + direction + "' (asc|desc)");
        };
    }

    private enum CursorDirection { ASC, DESC }

    private Specification<User> buildSpec(UsersQuery query, boolean withCursor, CursorDirection direction) {
        Specification<User> spec = null;
        if (query.role() != null) {
            spec = add(spec, UserSpecs.role(query.role()));
        }
        if (query.status() != null) {
            spec = add(spec, UserSpecs.status(query.status()));
        }
        if (query.search() != null && !query.search().isBlank()) {
            spec = add(spec, UserSpecs.search(query.search().trim()));
        }
        if (withCursor && query.cursor() != null && !query.cursor().isBlank()) {
            spec = add(spec, UserSpecs.afterCursor(query.cursor(), direction));
        }
        return spec;
    }

    private static Specification<User> add(Specification<User> current, Specification<User> next) {
        return current == null ? next : current.and(next);
    }

    private static final class UserSpecs {

        private UserSpecs() {
        }

        static Specification<User> role(com.smartportx.identity.domain.RoleName role) {
            return (root, query, cb) -> cb.equal(root.get("role"), role);
        }

        static Specification<User> status(Status status) {
            return (root, query, cb) -> switch (status) {
                case INACTIVE -> cb.isFalse(root.get("enabled"));
                case LOCKED -> cb.and(
                        cb.isTrue(root.get("enabled")),
                        cb.isNotNull(root.get("lockoutUntil")),
                        lockoutInFuture(root, cb));
                case ACTIVE -> cb.and(
                        cb.isTrue(root.get("enabled")),
                        cb.or(
                                cb.isNull(root.get("lockoutUntil")),
                                cb.not(lockoutInFuture(root, cb))));
            };
        }

        static Specification<User> search(String term) {
            String like = "%" + term.toLowerCase() + "%";
            return (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), like),
                    cb.like(cb.lower(root.get("displayName")), like),
                    cb.like(cb.lower(root.get("email")), like));
        }

        static Specification<User> afterCursor(String cursor, CursorDirection direction) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int separator = raw.lastIndexOf(':');
                Instant createdAt = Instant.parse(raw.substring(0, separator));
                UUID id = UUID.fromString(raw.substring(separator + 1));
                if (direction == CursorDirection.DESC) {
                    return (root, query, cb) -> cb.or(
                            cb.lessThan(root.get("createdAt"), createdAt),
                            cb.and(cb.equal(root.get("createdAt"), createdAt),
                                    cb.lessThan(root.get("id"), id)));
                }
                return (root, query, cb) -> cb.or(
                        cb.greaterThan(root.get("createdAt"), createdAt),
                        cb.and(cb.equal(root.get("createdAt"), createdAt),
                                cb.greaterThan(root.get("id"), id)));
            } catch (RuntimeException e) {
                return (root, query, cb) -> cb.disjunction();
            }
        }

        private static String encodeCursor(User last) {
            String raw = last.getCreatedAt() + ":" + last.getId();
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        private static Predicate lockoutInFuture(Root<User> root, CriteriaBuilder cb) {
            return cb.greaterThan(root.get("lockoutUntil").as(Instant.class), Instant.now());
        }
    }
}