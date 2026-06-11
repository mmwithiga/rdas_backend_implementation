package com.rdas.model;

import java.io.Serializable;

public record CountryData(
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
) implements Serializable {}
