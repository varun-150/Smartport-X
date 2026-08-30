package com.smartportx.container.web.dto;

import java.util.List;

public record ContainersPage(Pagination pagination, List<ContainerResponse> data) {
}