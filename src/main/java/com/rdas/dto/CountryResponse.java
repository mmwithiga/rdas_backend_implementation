package com.rdas.dto;

import java.util.List;

public record CountryResponse(
        String isoCode,
        String name,
        String capital,
        String continentCode,
        String continentName,
        String currencyIsoCode,
        String currencyName,
        String languageIsoCode,
        String languageName,
        String flagUrl,
        String phoneCode
) {}
