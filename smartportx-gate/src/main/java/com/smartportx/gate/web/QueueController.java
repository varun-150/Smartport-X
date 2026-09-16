package com.smartportx.gate.web;

import com.smartportx.gate.service.GateQueueService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gate/queue")
public class QueueController {

    private final GateQueueService queue;

    public QueueController(GateQueueService queue) {
        this.queue = queue;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public GateQueueService.QueueView currentQueue() {
        return queue.currentView();
    }
}