package com.rdas.controller;

import com.rdas.service.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Operational endpoints for cache management")
public class AdminController {

    private final CountryService countryService;

    @PostMapping("/cache/refresh")
    @Operation(
        summary = "Refresh all caches",
        description = "Evicts all cached reference data and re-fetches from the SOAP service. " +
                      "Use when reference data has changed and you don't want to wait for scheduled refresh."
    )
    public ResponseEntity<Map<String, Object>> refreshCache() {
        log.info("Manual cache refresh requested");
        long start = System.currentTimeMillis();
        countryService.evictAllCaches();
        countryService.warmCache();
        long elapsed = System.currentTimeMillis() - start;
        return ResponseEntity.ok(Map.of(
                "status", "refreshed",
                "timestamp", Instant.now().toString(),
                "durationMs", elapsed
        ));
    }
}
