package com.rdas.model;

import java.io.Serializable;

public record CurrencyData(
        String isoCode,
        String name
) implements Serializable {}
