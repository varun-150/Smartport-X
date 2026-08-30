package com.smartportx.container.service;

import com.smartportx.common.error.ApiException;
import com.smartportx.common.error.NotFoundException;
import com.smartportx.container.domain.Container;
import com.smartportx.container.domain.ContainerEvent;
import com.smartportx.container.domain.ContainerMovement;
import com.smartportx.container.domain.ContainerStatusHistory;
import com.smartportx.container.domain.ContainerType;
import com.smartportx.container.repository.ContainerEventRepository;
import com.smartportx.container.repository.ContainerMovementRepository;
import com.smartportx.container.repository.ContainerRepository;
import com.smartportx.container.repository.ContainerStatusHistoryRepository;
import com.smartportx.container.util.ContainerNumbers;
import com.smartportx.container.web.dto.ContainerResponse;
import com.smartportx.container.web.dto.ContainersPage;
import com.smartportx.container.web.dto.ContainersQuery;
import com.smartportx.container.web.dto.EventResponse;
import com.smartportx.container.web.dto.EventsPage;
import com.smartportx.container.web.dto.EventsQuery;
import com.smartportx.container.web.dto.MovementResponse;
import com.smartportx.container.web.dto.MovementsPage;
import com.smartportx.container.web.dto.MovementsQuery;
import com.smartportx.container.web.dto.Pagination;
import com.smartportx.container.web.dto.SearchContainersRequest;
import com.smartportx.container.web.dto.StatusHistoryResponse;
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
public class ContainerQueryService {

    private final ContainerRepository containers;
    private final ContainerMovementRepository movements;
    private final ContainerEventRepository events;
    private final ContainerStatusHistoryRepository history;

    public ContainerQueryService(ContainerRepository containers, ContainerMovementRepository movements,
                                 ContainerEventRepository events, ContainerStatusHistoryRepository history) {
        this.containers = containers;
        this.movements = movements;
        this.events = events;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public ContainersPage list(ContainersQuery query) {
        int limit = query.effectiveLimit();
        CursorDirection direction = parseContainerSort(query.sort());
        Specification<Container> filter = buildSpec(query, false, direction);
        long total = containers.count(filter);
        Sort order = direction == CursorDirection.DESC
                ? Sort.by("createdAt").descending().and(Sort.by("id").descending())
                : Sort.by("createdAt").ascending().and(Sort.by("id").ascending());
        Page<Container> page = containers.findAll(buildSpec(query, true, direction),
                PageRequest.of(0, limit + 1, order));
        boolean hasMore = page.getContent().size() > limit;
        List<Container> items = hasMore ? page.getContent().subList(0, limit) : page.getContent();
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            nextCursor = ContainerSpecs.encodeCursor(items.get(items.size() - 1));
        }
        List<ContainerResponse> data = items.stream().map(ContainerResponse::from).toList();
        return new ContainersPage(new Pagination(nextCursor, hasMore, total, limit), data);
    }

    @Transactional(readOnly = true)
    public ContainerResponse get(UUID id) {
        return ContainerResponse.from(fetchActive(id));
    }

    @Transactional(readOnly = true)
    public ContainerResponse getByNumber(String raw) {
        String number = ContainerNumbers.normalize(raw);
        Container container = containers.findByContainerNumber(number)
                .filter(c -> c.getRemovedAt() == null)
                .orElseThrow(() -> new NotFoundException("Container " + number + " not found"));
        return ContainerResponse.from(container);
    }

    @Transactional(readOnly = true)
    public ContainersPage search(SearchContainersRequest request) {
        if (request.customsStatus() != null && !request.customsStatus().isBlank()) {
            throw new ApiException(400, "invalid_query",
                    "filter 'customsStatus' is not supported by this service");
        }
        int limit = request.effectiveLimit();
        Specification<Container> spec = buildSearchSpec(request);
        long total = containers.count(spec);
        Sort order = searchOrder(request.sort());
        List<Container> items = containers.findAll(spec, PageRequest.of(0, limit, order)).getContent();
        List<ContainerResponse> data = items.stream().map(ContainerResponse::from).toList();
        return new ContainersPage(new Pagination(null, false, total, limit), data);
    }

    @Transactional(readOnly = true)
    public MovementsPage movements(UUID containerId, MovementsQuery query) {
        Container container = containers.findById(containerId)
                .orElseThrow(() -> new NotFoundException("Container " + containerId + " not found"));
        int limit = query.effectiveLimit();
        Specification<ContainerMovement> spec = MovementSpecs.active(containerId);
        if (query.from() != null) {
            spec = and(spec, MovementSpecs.from(query.from()));
        }
        if (query.to() != null) {
            spec = and(spec, MovementSpecs.to(query.to()));
        }
        if (query.type() != null) {
            spec = and(spec, MovementSpecs.type(query.type()));
        }
        if (query.cursor() != null && !query.cursor().isBlank()) {
            spec = and(spec, MovementSpecs.afterCursor(query.cursor()));
        }
        long total = movements.count(MovementSpecs.active(containerId));
        Sort order = Sort.by("timestamp").descending().and(Sort.by("id").descending());
        Page<ContainerMovement> page = movements.findAll(spec, PageRequest.of(0, limit + 1, order));
        boolean hasMore = page.getContent().size() > limit;
        List<ContainerMovement> items = hasMore ? page.getContent().subList(0, limit) : page.getContent();
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            nextCursor = MovementSpecs.encodeCursor(items.get(items.size() - 1));
        }
        List<MovementResponse> data = items.stream()
                .map(m -> MovementResponse.from(container, m)).toList();
        return new MovementsPage(new Pagination(nextCursor, hasMore, total, limit), data);
    }

    @Transactional(readOnly = true)
    public EventsPage events(UUID containerId, EventsQuery query) {
        Container container = containers.findById(containerId)
                .orElseThrow(() -> new NotFoundException("Container " + containerId + " not found"));
        int limit = query.effectiveLimit();
        Specification<ContainerEvent> spec = EventSpecs.active(containerId);
        if (query.eventType() != null && !query.eventType().isBlank()) {
            spec = and(spec, EventSpecs.type(query.eventType()));
        }
        if (query.cursor() != null && !query.cursor().isBlank()) {
            spec = and(spec, EventSpecs.afterCursor(query.cursor()));
        }
        long total = events.count(EventSpecs.active(containerId));
        Sort order = Sort.by("timestamp").descending().and(Sort.by("id").descending());
        Page<ContainerEvent> page = events.findAll(spec, PageRequest.of(0, limit + 1, order));
        boolean hasMore = page.getContent().size() > limit;
        List<ContainerEvent> items = hasMore ? page.getContent().subList(0, limit) : page.getContent();
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            nextCursor = EventSpecs.encodeCursor(items.get(items.size() - 1));
        }
        List<EventResponse> data = items.stream()
                .map(e -> new EventResponse(e.getId(), e.getEventType(), e.getContainerId(),
                        container.getContainerNumber(), e.getTimestamp(), e.getEventData(),
                        null))
                .toList();
        return new EventsPage(new Pagination(nextCursor, hasMore, total, limit), data);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> statusHistory(UUID containerId) {
        Container container = containers.findById(containerId)
                .orElseThrow(() -> new NotFoundException("Container " + containerId + " not found"));
        return history.findByContainerIdOrderByChangedAtDesc(containerId).stream()
                .map(h -> StatusHistoryResponse.from(container.getContainerNumber(), h))
                .toList();
    }

    private Container fetchActive(UUID id) {
        return containers.findById(id)
                .filter(c -> c.getRemovedAt() == null)
                .orElseThrow(() -> new NotFoundException("Container " + id + " not found"));
    }

    private static Specification<Container> buildSpec(ContainersQuery query, boolean withCursor,
                                                      CursorDirection direction) {
        Specification<Container> spec = ContainerSpecs.active();
        if (query.status() != null) {
            spec = and(spec, ContainerSpecs.status(query.status()));
        }
        if (query.type() != null) {
            spec = and(spec, ContainerSpecs.type(query.type()));
        }
        if (query.owner() != null && !query.owner().isBlank()) {
            spec = and(spec, ContainerSpecs.owner(query.owner()));
        }
        if (query.hasDangerousGoods() != null) {
            spec = and(spec, ContainerSpecs.dangerousGoods(query.hasDangerousGoods()));
        }
        if (query.location() != null && !query.location().isBlank()) {
            spec = and(spec, ContainerSpecs.locationPrefix(query.location()));
        }
        if (query.createdAfter() != null) {
            spec = and(spec, ContainerSpecs.createdAfter(query.createdAfter()));
        }
        if (query.createdBefore() != null) {
            spec = and(spec, ContainerSpecs.createdBefore(query.createdBefore()));
        }
        if (withCursor && query.cursor() != null && !query.cursor().isBlank()) {
            spec = and(spec, ContainerSpecs.afterCursor(query.cursor(), direction));
        }
        return spec;
    }

    private static CursorDirection parseContainerSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return CursorDirection.DESC;
        }
        String[] parts = sort.split(",");
        if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
            throw new ApiException(400, "invalid_query", "sort must be '<field>,<direction>'");
        }
        if (!"createdAt".equals(parts[0].trim())) {
            throw new ApiException(400, "invalid_query",
                    "unsupported sort field '" + parts[0].trim() + "' (only createdAt)");
        }
        String direction = parts[1].trim();
        return switch (direction) {
            case "asc" -> CursorDirection.ASC;
            case "desc" -> CursorDirection.DESC;
            default -> throw new ApiException(400, "invalid_query",
                    "unsupported sort direction '" + direction + "' (asc|desc)");
        };
    }

    private static Specification<Container> buildSearchSpec(SearchContainersRequest request) {
        Specification<Container> spec = ContainerSpecs.active();
        if (request.containerNumber() != null && !request.containerNumber().isBlank()) {
            String like = ContainerNumbers.normalize(request.containerNumber()).replace("*", "%");
            spec = and(spec, ContainerSpecs.like("containerNumber", like));
        }
        if (request.status() != null && !request.status().isEmpty()) {
            spec = and(spec, ContainerSpecs.statusIn(request.status()));
        }
        if (request.type() != null && !request.type().isEmpty()) {
            spec = and(spec, ContainerSpecs.typeIn(request.type()));
        }
        if (request.owner() != null && !request.owner().isBlank()) {
            spec = and(spec, ContainerSpecs.owner(request.owner()));
        }
        if (request.weight() != null) {
            if (request.weight().min() != null && request.weight().max() != null
                    && request.weight().min() > request.weight().max()) {
                throw new ApiException(400, "invalid_query", "weight.min must be <= weight.max");
            }
            if (request.weight().min() != null) {
                spec = and(spec, ContainerSpecs.weightMin(request.weight().min()));
            }
            if (request.weight().max() != null) {
                spec = and(spec, ContainerSpecs.weightMax(request.weight().max()));
            }
        }
        if (request.dangerousGoods() != null) {
            spec = and(spec, ContainerSpecs.dangerousGoods(request.dangerousGoods()));
        }
        if (request.location() != null) {
            if (request.location().block() != null && !request.location().block().isBlank()) {
                spec = and(spec, ContainerSpecs.locationPrefix(request.location().block()));
            }
            if (request.location().fromDate() != null) {
                spec = and(spec, ContainerSpecs.createdAfter(request.location().fromDate()));
            }
            if (request.location().toDate() != null) {
                spec = and(spec, ContainerSpecs.createdBefore(request.location().toDate()));
            }
        }
        return spec;
    }

    private static Sort searchOrder(String sort) {
        String field = "createdAt";
        var direction = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                throw new ApiException(400, "invalid_query", "sort must be '<field>,<direction>'");
            }
            field = switch (parts[0].trim()) {
                case "createdAt" -> "createdAt";
                case "weight" -> "weightKg";
                case "containerNumber" -> "containerNumber";
                default -> throw new ApiException(400, "invalid_query",
                        "unsupported sort field '" + parts[0].trim()
                                + "' (createdAt|weight|containerNumber)");
            };
            direction = switch (parts[1].trim()) {
                case "asc" -> Sort.Direction.ASC;
                case "desc" -> Sort.Direction.DESC;
                default -> throw new ApiException(400, "invalid_query",
                        "unsupported sort direction '" + parts[1].trim() + "' (asc|desc)");
            };
        }
        return Sort.by(direction, field);
    }

    private static <T> Specification<T> and(Specification<T> current, Specification<T> next) {
        return current == null ? next : current.and(next);
    }

    private enum CursorDirection { ASC, DESC }

    private static final class ContainerSpecs {

        private ContainerSpecs() {
        }

        static Specification<Container> active() {
            return (root, query, cb) -> cb.isNull(root.get("removedAt"));
        }

        static Specification<Container> status(com.smartportx.container.domain.ContainerStatus status) {
            return (root, query, cb) -> cb.equal(root.get("status"), status);
        }

        static Specification<Container> statusIn(List<com.smartportx.container.domain.ContainerStatus> statuses) {
            return (root, query, cb) -> root.get("status").in(statuses);
        }

        static Specification<Container> type(ContainerType type) {
            return (root, query, cb) -> cb.equal(root.get("containerType"), type);
        }

        static Specification<Container> typeIn(List<ContainerType> types) {
            return (root, query, cb) -> root.get("containerType").in(types);
        }

        static Specification<Container> owner(String owner) {
            String like = "%" + owner.toLowerCase() + "%";
            return (root, query, cb) -> cb.like(cb.lower(root.get("owner")), like);
        }

        static Specification<Container> dangerousGoods(boolean value) {
            return (root, query, cb) -> cb.equal(root.get("dangerousGoods"), value);
        }

        static Specification<Container> locationPrefix(String prefix) {
            return (root, query, cb) -> cb.like(root.get("currentLocation"), prefix + "%");
        }

        static Specification<Container> like(String field, String like) {
            return (root, query, cb) -> cb.like(root.get(field), like);
        }

        static Specification<Container> weightMin(double min) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("weightKg"), min);
        }

        static Specification<Container> weightMax(double max) {
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("weightKg"), max);
        }

        static Specification<Container> createdAfter(Instant after) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), after);
        }

        static Specification<Container> createdBefore(Instant before) {
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), before);
        }

        static Specification<Container> afterCursor(String cursor, CursorDirection direction) {
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

        static String encodeCursor(Container last) {
            String raw = last.getCreatedAt() + ":" + last.getId();
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class MovementSpecs {

        private MovementSpecs() {
        }

        static Specification<ContainerMovement> active(UUID containerId) {
            return (root, query, cb) -> cb.equal(root.get("containerId"), containerId);
        }

        static Specification<ContainerMovement> from(Instant from) {
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from);
        }

        static Specification<ContainerMovement> to(Instant to) {
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to);
        }

        static Specification<ContainerMovement> type(com.smartportx.container.domain.MovementType type) {
            return (root, query, cb) -> cb.equal(root.get("movementType"), type);
        }

        static Specification<ContainerMovement> afterCursor(String cursor) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int separator = raw.lastIndexOf(':');
                Instant timestamp = Instant.parse(raw.substring(0, separator));
                UUID id = UUID.fromString(raw.substring(separator + 1));
                return (root, query, cb) -> cb.or(
                        cb.lessThan(root.get("timestamp"), timestamp),
                        cb.and(cb.equal(root.get("timestamp"), timestamp),
                                cb.lessThan(root.get("id"), id)));
            } catch (RuntimeException e) {
                return (root, query, cb) -> cb.disjunction();
            }
        }

        static String encodeCursor(ContainerMovement last) {
            String raw = last.getTimestamp() + ":" + last.getId();
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class EventSpecs {

        private EventSpecs() {
        }

        static Specification<ContainerEvent> active(UUID containerId) {
            return (root, query, cb) -> cb.equal(root.get("containerId"), containerId);
        }

        static Specification<ContainerEvent> type(String eventType) {
            return (root, query, cb) -> cb.equal(root.get("eventType"), eventType);
        }

        static Specification<ContainerEvent> afterCursor(String cursor) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int separator = raw.lastIndexOf(':');
                Instant timestamp = Instant.parse(raw.substring(0, separator));
                UUID id = UUID.fromString(raw.substring(separator + 1));
                return (root, query, cb) -> cb.or(
                        cb.lessThan(root.get("timestamp"), timestamp),
                        cb.and(cb.equal(root.get("timestamp"), timestamp),
                                cb.lessThan(root.get("id"), id)));
            } catch (RuntimeException e) {
                return (root, query, cb) -> cb.disjunction();
            }
        }

        static String encodeCursor(ContainerEvent last) {
            String raw = last.getTimestamp() + ":" + last.getId();
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}