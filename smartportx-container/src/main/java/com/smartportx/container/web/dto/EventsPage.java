package com.smartportx.container.web.dto;

import java.util.List;

public record EventsPage(Pagination pagination, List<EventResponse> data) {
}