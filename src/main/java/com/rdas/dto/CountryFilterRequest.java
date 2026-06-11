package com.rdas.dto;

import jakarta.validation.constraints.Size;

public record CountryFilterRequest(
        @Size(min = 1, max = 100, message = "Name filter must be between 1 and 100 characters")
        String name,

        @Size(min = 2, max = 2, message = "Continent code must be exactly 2 characters")
        String continent,

        @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
        String currency,

        @Size(min = 2, max = 3, message = "Language code must be 2-3 characters")
        String language
) {
    // Canonical constructor — normalize all inputs to uppercase/trimmed
    public CountryFilterRequest {
        name      = name      != null ? name.trim()               : null;
        continent = continent != null ? continent.trim().toUpperCase() : null;
        currency  = currency  != null ? currency.trim().toUpperCase()  : null;
        language  = language  != null ? language.trim().toUpperCase()  : null;
    }

    public boolean hasFilters() {
        return name != null || continent != null || currency != null || language != null;
    }
}
