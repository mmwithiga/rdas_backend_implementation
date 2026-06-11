package com.rdas.startup;

import com.rdas.service.CountryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupRunner implements ApplicationRunner {

    private final CountryService countryService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== RDAS Cache Warmup Starting ===");
        long start = System.currentTimeMillis();
        countryService.warmCache();
        long elapsed = System.currentTimeMillis() - start;
        log.info("=== RDAS Cache Warmup Complete in {}ms ===", elapsed);
    }
}
