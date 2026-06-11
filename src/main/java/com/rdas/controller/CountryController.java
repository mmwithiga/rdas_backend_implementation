package com.rdas.controller;

import com.rdas.dto.*;
import com.rdas.model.ContinentData;
import com.rdas.model.CurrencyData;
import com.rdas.model.LanguageData;
import com.rdas.service.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reference Data", description = "Country, currency, language and continent reference data")
public class CountryController {

    private final CountryService countryService;

    // ─── Countries ────────────────────────────────────────────────────────────

    @GetMapping("/countries")
    @Operation(
        summary = "Search countries",
        description = "Returns a paginated, filterable, sortable list of countries. " +
                      "All filters are optional and combinable."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid filter or pagination parameters"),
        @ApiResponse(responseCode = "503", description = "Reference data temporarily unavailable")
    })
    public ResponseEntity<PagedResponse<CountryResponse>> searchCountries(
            @Valid CountryFilterRequest filter,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            @Parameter(description = "Pagination: page, size, sort (e.g. sort=name,asc)")
            Pageable pageable) {
        return ResponseEntity.ok(countryService.searchCountries(filter, pageable));
    }

    @GetMapping("/countries/{isoCode}")
    @Operation(summary = "Get country by ISO code", description = "Returns full details for a single country")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "404", description = "Country not found"),
        @ApiResponse(responseCode = "503", description = "Reference data temporarily unavailable")
    })
    public ResponseEntity<CountryResponse> getCountry(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z]{2}$", message = "ISO code must be exactly 2 letters")
            String isoCode) {
        return ResponseEntity.ok(countryService.getCountryByCode(isoCode));
    }

    @GetMapping("/countries/{isoCode}/currency")
    @Operation(summary = "Get currency for a specific country")
    public ResponseEntity<CountryResponse> getCountryWithCurrency(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z]{2}$", message = "ISO code must be exactly 2 letters")
            String isoCode) {
        return ResponseEntity.ok(countryService.getCountryByCode(isoCode));
    }

    // ─── Currencies ───────────────────────────────────────────────────────────

    @GetMapping("/currencies")
    @Operation(summary = "List all currencies", description = "Returns all available currencies")
    public ResponseEntity<List<CurrencyData>> getAllCurrencies() {
        return ResponseEntity.ok(countryService.getAllCurrencies());
    }

    @GetMapping("/currencies/{code}/countries")
    @Operation(
        summary = "Countries using a currency",
        description = "Returns all countries that use the specified ISO currency code"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid currency code"),
        @ApiResponse(responseCode = "503", description = "Reference data temporarily unavailable")
    })
    public ResponseEntity<List<CountryResponse>> getCountriesByCurrency(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency code must be exactly 3 letters")
            String code) {
        return ResponseEntity.ok(countryService.getCountriesByCurrency(code));
    }

    // ─── Continents ───────────────────────────────────────────────────────────

    @GetMapping("/continents")
    @Operation(summary = "List all continents")
    public ResponseEntity<List<ContinentData>> getAllContinents() {
        return ResponseEntity.ok(countryService.getAllContinents());
    }

    // ─── Languages ────────────────────────────────────────────────────────────

    @GetMapping("/languages")
    @Operation(summary = "List all languages")
    public ResponseEntity<List<LanguageData>> getAllLanguages() {
        return ResponseEntity.ok(countryService.getAllLanguages());
    }
}
