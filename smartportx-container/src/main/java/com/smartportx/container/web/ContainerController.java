package com.smartportx.container.web;

import com.smartportx.container.domain.ContainerStatus;
import com.smartportx.container.domain.ContainerType;
import com.smartportx.container.domain.MovementType;
import com.smartportx.container.service.ContainerQueryService;
import com.smartportx.container.service.ContainerService;
import com.smartportx.container.web.dto.ContainerResponse;
import com.smartportx.container.web.dto.ContainersPage;
import com.smartportx.container.web.dto.ContainersQuery;
import com.smartportx.container.web.dto.EventResponse;
import com.smartportx.container.web.dto.EventsPage;
import com.smartportx.container.web.dto.EventsQuery;
import com.smartportx.container.web.dto.MovementResponse;
import com.smartportx.container.web.dto.MovementsPage;
import com.smartportx.container.web.dto.MovementsQuery;
import com.smartportx.container.web.dto.RegisterContainerRequest;
import com.smartportx.container.web.dto.RegisterMovementRequest;
import com.smartportx.container.web.dto.SearchContainersRequest;
import com.smartportx.container.web.dto.StatusChangeRequest;
import com.smartportx.container.web.dto.StatusChangeResponse;
import com.smartportx.container.web.dto.StatusHistoryResponse;
import com.smartportx.container.web.dto.UpdateContainerRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/containers")
public class ContainerController {

    private final ContainerService containerService;
    private final ContainerQueryService queryService;

    public ContainerController(ContainerService containerService, ContainerQueryService queryService) {
        this.containerService = containerService;
        this.queryService = queryService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TERMINAL_OP')")
    public ResponseEntity<ContainerResponse> register(@Valid @RequestBody RegisterContainerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(containerService.register(request));
    }

    @GetMapping
    public ContainersPage list(@RequestParam(required = false) Integer limit,
                               @RequestParam(required = false) String cursor,
                               @RequestParam(defaultValue = "createdAt,desc") String sort,
                               @RequestParam(name = "filter[status]", required = false) ContainerStatus status,
                               @RequestParam(name = "filter[type]", required = false) ContainerType type,
                               @RequestParam(name = "filter[owner]", required = false) String owner,
                               @RequestParam(name = "filter[hasDangerousGoods]", required = false) Boolean hasDangerousGoods,
                               @RequestParam(name = "filter[location]", required = false) String location,
                               @RequestParam(name = "filter[createdAfter]", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
                               @RequestParam(name = "filter[createdBefore]", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore) {
        ContainersQuery query = new ContainersQuery(limit, cursor, sort, status, type, owner,
                hasDangerousGoods, location, createdAfter, createdBefore);
        return queryService.list(query);
    }

    @PostMapping("/search")
    public ContainersPage search(@Valid @RequestBody SearchContainersRequest request) {
        return queryService.search(request);
    }

    @GetMapping("/number/{containerNumber}")
    public ContainerResponse getByNumber(@PathVariable String containerNumber) {
        return queryService.getByNumber(containerNumber);
    }

    @GetMapping("/{id}")
    public ContainerResponse get(@PathVariable UUID id) {
        return queryService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TERMINAL_OP')")
    public ContainerResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody UpdateContainerRequest request) {
        return containerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TERMINAL_OP')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        containerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'TERMINAL_OP')")
    public MovementResponse recordMovement(@PathVariable UUID id,
                                           @Valid @RequestBody RegisterMovementRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        return containerService.recordMovement(id, request, performedBy(jwt));
    }

    @GetMapping("/{id}/movements")
    public MovementsPage movements(@PathVariable UUID id,
                                   @RequestParam(required = false) Integer limit,
                                   @RequestParam(required = false) String cursor,
                                   @RequestParam(name = "filter[from]", required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                   @RequestParam(name = "filter[to]", required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                   @RequestParam(name = "filter[type]", required = false) MovementType type) {
        return queryService.movements(id, new MovementsQuery(limit, cursor, from, to, type));
    }

    @GetMapping("/{id}/events")
    public EventsPage events(@PathVariable UUID id,
                             @RequestParam(required = false) Integer limit,
                             @RequestParam(required = false) String cursor,
                             @RequestParam(name = "filter[eventType]", required = false) String eventType) {
        return queryService.events(id, new EventsQuery(limit, cursor, eventType));
    }

    @GetMapping("/{id}/status-history")
    public List<StatusHistoryResponse> statusHistory(@PathVariable UUID id) {
        return queryService.statusHistory(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TERMINAL_OP')")
    public StatusChangeResponse changeStatus(@PathVariable UUID id,
                                             @Valid @RequestBody StatusChangeRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        return containerService.changeStatus(id, request, UUID.fromString(jwt.getSubject()));
    }

    private static String performedBy(Jwt jwt) {
        String username = jwt.getClaimAsString("username");
        return username == null || username.isBlank() ? jwt.getSubject() : username;
    }
}