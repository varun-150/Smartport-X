package com.smartportx.container.web.dto;

import java.util.List;

public record MovementsPage(Pagination pagination, List<MovementResponse> data) {
}