package com.yegkim.task_reloader_api.web;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @Operation(summary = "Health probe for uptime monitoring")
    @GetMapping("/healthz")
    public String health() {
        return "ok";
    }
}

