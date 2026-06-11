package com.rdas.service;

import com.rdas.config.CacheConfig.CacheNames;
import com.rdas.dto.CountryFilterRequest;
import com.rdas.dto.CountryResponse;
import com.rdas.dto.PagedResponse;
import com.rdas.exception.ResourceNotFoundException;
import com.rdas.exception.SoapServiceUnavailableException;
import com.rdas.integration.CountryInfoSoapClient;
import com.rdas.model.ContinentData;
import com.rdas.model.CountryData;
import com.rdas.model.CurrencyData;
import com.rdas.model.LanguageData;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CountryService {

    private final CountryInfoSoapClient soapClient;

    // ─── Country Operations ───────────────────────────────────────────────────

    /**
     * Returns paginated, filtered, sorted list of countries.
     * All filtering happens in-memory on cached data — zero SOAP calls per consumer request.
     */
    public PagedResponse<CountryResponse> searchCountries(CountryFilterRequest filter, Pageable pageable) {
        List<CountryData> allCountries = getAllCountries();

        List<CountryData> filtered = allCountries.stream()
                .filter(c -> matchesName(c, filter.name()))
                .filter(c -> matchesContinent(c, filter.continent()))
                .filter(c -> matchesCurrency(c, filter.currency()))
                .filter(c -> matchesLanguage(c, filter.language()))
                .collect(Collectors.toList());

        // Apply sorting
        filtered = applySorting(filtered, pageable);

        // Apply pagination manually (we're working with in-memory list)
        int total = filtered.size();
        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), total);

        List<CountryData> pageContent = (start >= total)
                ? List.of()
                : filtered.subList(start, end);

        Page<CountryResponse> page = new PageImpl<>(
                pageContent.stream().map(this::toResponse).toList(),
                pageable,
                total
        );

        return PagedResponse.from(page);
    }

    @Cacheable(value = CacheNames.COUNTRY_BY_CODE, key = "#isoCode.toUpperCase()")
    @CircuitBreaker(name = "soapService", fallbackMethod = "fallbackGetCountry")
    public CountryResponse getCountryByCode(String isoCode) {
        List<CountryData> all = getAllCountries();
        return all.stream()
                .filter(c -> c.isoCode().equalsIgnoreCase(isoCode))
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Country", isoCode));
    }

    @Cacheable(value = CacheNames.COUNTRIES_ALL)
    @CircuitBreaker(name = "soapService", fallbackMethod = "fallbackGetAllCountries")
    public List<CountryData> getAllCountries() {
        log.info("Cache miss — fetching all countries from SOAP");
        List<ContinentData> continents = soapClient.listContinents();
        Map<String, CurrencyData> currencyMap  = buildCurrencyMap();
        Map<String, LanguageData> languageMap  = buildLanguageMap();

        return continents.parallelStream()
                .flatMap(continent -> {
                    List<CountryData> basic = soapClient.listCountriesByContinent(continent.code());
                    return basic.stream().map(country -> enrichCountry(country, currencyMap, languageMap));
                })
                .collect(Collectors.toList());
    }

    // ─── Currency Operations ──────────────────────────────────────────────────

    @Cacheable(value = CacheNames.CURRENCIES_ALL)
    @CircuitBreaker(name = "soapService", fallbackMethod = "fallbackGetCurrencies")
    public List<CurrencyData> getAllCurrencies() {
        log.info("Cache miss — fetching currencies from SOAP");
        return soapClient.listCurrencies();
    }

    @Cacheable(value = CacheNames.CURRENCY_COUNTRIES, key = "#currencyCode.toUpperCase()")
    @CircuitBreaker(name = "soapService", fallbackMethod = "fallbackGetCurrencyCountries")
    public List<CountryResponse> getCountriesByCurrency(String currencyCode) {
        List<String> isoCodes = soapClient.getCountriesUsingCurrency(currencyCode.toUpperCase());
        List<CountryData> all = getAllCountries();
        Map<String, CountryData> lookup = all.stream()
                .collect(Collectors.toMap(c -> c.isoCode().toUpperCase(), Function.identity()));
        return isoCodes.stream()
                .map(code -> lookup.get(code.toUpperCase()))
                .filter(c -> c != null)
                .map(this::toResponse)
                .toList();
    }

    // ─── Continent Operations ─────────────────────────────────────────────────

    @Cacheable(value = CacheNames.CONTINENTS_ALL)
    @CircuitBreaker(name = "soapService", fallbackMethod = "fallbackGetContinents")
    public List<ContinentData> getAllContinents() {
        log.info("Cache miss — fetching continents from SOAP");
        return soapClient.listContinents();
    }

    // ─── Language Operations ──────────────────────────────────────────────────

    @Cacheable(value = CacheNames.LANGUAGES_ALL)
    @CircuitBreaker(name = "soapService", fallbackMethod = "fallbackGetLanguages")
    public List<LanguageData> getAllLanguages() {
        log.info("Cache miss — fetching languages from SOAP");
        return soapClient.listLanguages();
    }

    // ─── Cache Warm & Refresh ─────────────────────────────────────────────────

    /**
     * Called at startup by CacheWarmupRunner.
     * Eagerly populates all caches so the first consumer request is always a cache hit.
     */
    public void warmCache() {
        try {
            getAllContinents();
            getAllCurrencies();
            getAllLanguages();
            getAllCountries();
            log.info("Cache warm complete — all reference data loaded");
        } catch (Exception e) {
            log.error("Cache warm failed — service will attempt lazy load on first request: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled refresh every 12 hours.
     * Evicts all caches then re-warms — transparent to consumers.
     */
    @Scheduled(cron = "${cache.refresh.cron:0 0 */12 * * *}")
    @Caching(evict = {
            @CacheEvict(value = CacheNames.COUNTRIES_ALL,      allEntries = true),
            @CacheEvict(value = CacheNames.COUNTRY_BY_CODE,    allEntries = true),
            @CacheEvict(value = CacheNames.CURRENCIES_ALL,     allEntries = true),
            @CacheEvict(value = CacheNames.CURRENCY_COUNTRIES, allEntries = true),
            @CacheEvict(value = CacheNames.CONTINENTS_ALL,     allEntries = true),
            @CacheEvict(value = CacheNames.LANGUAGES_ALL,      allEntries = true)
    })
    public void scheduledCacheRefresh() {
        log.info("Scheduled cache refresh triggered");
        warmCache();
    }

    /**
     * Manual cache eviction — triggered by ops via AdminController.
     */
    @Caching(evict = {
            @CacheEvict(value = CacheNames.COUNTRIES_ALL,      allEntries = true),
            @CacheEvict(value = CacheNames.COUNTRY_BY_CODE,    allEntries = true),
            @CacheEvict(value = CacheNames.CURRENCIES_ALL,     allEntries = true),
            @CacheEvict(value = CacheNames.CURRENCY_COUNTRIES, allEntries = true),
            @CacheEvict(value = CacheNames.CONTINENTS_ALL,     allEntries = true),
            @CacheEvict(value = CacheNames.LANGUAGES_ALL,      allEntries = true)
    })
    public void evictAllCaches() {
        log.info("Manual cache eviction requested");
    }

    // ─── Fallbacks ────────────────────────────────────────────────────────────

    public List<CountryData> fallbackGetAllCountries(Exception ex) {
        log.warn("Circuit open for getAllCountries — returning empty list: {}", ex.getMessage());
        throw new SoapServiceUnavailableException("Country data is temporarily unavailable");
    }

    public CountryResponse fallbackGetCountry(String isoCode, Exception ex) {
        log.warn("Circuit open for getCountryByCode({}) — {}", isoCode, ex.getMessage());
        throw new SoapServiceUnavailableException("Country data is temporarily unavailable");
    }

    public List<CurrencyData> fallbackGetCurrencies(Exception ex) {
        log.warn("Circuit open for getAllCurrencies: {}", ex.getMessage());
        throw new SoapServiceUnavailableException("Currency data is temporarily unavailable");
    }

    public List<CountryResponse> fallbackGetCurrencyCountries(String currencyCode, Exception ex) {
        log.warn("Circuit open for getCountriesByCurrency({}): {}", currencyCode, ex.getMessage());
        throw new SoapServiceUnavailableException("Currency data is temporarily unavailable");
    }

    public List<ContinentData> fallbackGetContinents(Exception ex) {
        log.warn("Circuit open for getAllContinents: {}", ex.getMessage());
        throw new SoapServiceUnavailableException("Continent data is temporarily unavailable");
    }

    public List<LanguageData> fallbackGetLanguages(Exception ex) {
        log.warn("Circuit open for getAllLanguages: {}", ex.getMessage());
        throw new SoapServiceUnavailableException("Language data is temporarily unavailable");
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private CountryData enrichCountry(CountryData basic,
                                      Map<String, CurrencyData> currencyMap,
                                      Map<String, LanguageData> languageMap) {
        try {
            String capital  = soapClient.getCapitalCity(basic.isoCode());
            String flagUrl  = soapClient.getCountryFlag(basic.isoCode());
            String phone    = soapClient.getPhoneCode(basic.isoCode());
            CurrencyData currency = soapClient.getCountryCurrency(basic.isoCode());

            return new CountryData(
                    basic.isoCode(),
                    basic.name(),
                    capital,
                    basic.continentCode(),
                    basic.continentName(),
                    currency != null ? currency.isoCode() : null,
                    currency != null ? currency.name()    : null,
                    null,   // language ISO — not available per-country from this SOAP service
                    null,
                    flagUrl,
                    phone
            );
        } catch (Exception e) {
            log.warn("Failed to enrich country {}: {} — returning basic record", basic.isoCode(), e.getMessage());
            return basic;
        }
    }

    private Map<String, CurrencyData> buildCurrencyMap() {
        return soapClient.listCurrencies().stream()
                .collect(Collectors.toMap(CurrencyData::isoCode, Function.identity()));
    }

    private Map<String, LanguageData> buildLanguageMap() {
        return soapClient.listLanguages().stream()
                .collect(Collectors.toMap(LanguageData::isoCode, Function.identity()));
    }

    private boolean matchesName(CountryData c, String name) {
        return name == null || c.name().toLowerCase().contains(name.toLowerCase());
    }

    private boolean matchesContinent(CountryData c, String continent) {
        return continent == null || continent.equalsIgnoreCase(c.continentCode());
    }

    private boolean matchesCurrency(CountryData c, String currency) {
        return currency == null || currency.equalsIgnoreCase(c.currencyIsoCode());
    }

    private boolean matchesLanguage(CountryData c, String language) {
        return language == null || language.equalsIgnoreCase(c.languageIsoCode());
    }

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("name", "isoCode", "continentName", "currencyName");

    private List<CountryData> applySorting(List<CountryData> countries, Pageable pageable) {
        if (!pageable.getSort().isSorted()) return countries;

        Comparator<CountryData> comparator = null;
        for (var order : pageable.getSort()) {
            String property = order.getProperty();
            if (!ALLOWED_SORT_FIELDS.contains(property)) {
                log.warn("Ignoring unsupported sort field: {}", property);
                continue;
            }
            Comparator<CountryData> fieldComparator = switch (property) {
                case "isoCode"       -> Comparator.comparing(CountryData::isoCode, String.CASE_INSENSITIVE_ORDER);
                case "continentName" -> Comparator.comparing(c -> nullSafe(c.continentName()));
                case "currencyName"  -> Comparator.comparing(c -> nullSafe(c.currencyName()));
                default              -> Comparator.comparing(c -> nullSafe(c.name()));
            };
            if (order.isDescending()) fieldComparator = fieldComparator.reversed();
            comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
        }
        if (comparator == null) return countries;
        return countries.stream().sorted(comparator).toList();
    }

    private String nullSafe(String value) {
        return value != null ? value.toLowerCase() : "";
    }

    private CountryResponse toResponse(CountryData c) {
        return new CountryResponse(
                c.isoCode(), c.name(), c.capital(),
                c.continentCode(), c.continentName(),
                c.currencyIsoCode(), c.currencyName(),
                c.languageIsoCode(), c.languageName(),
                c.flagUrl(), c.phoneCode()
        );
    }
}
