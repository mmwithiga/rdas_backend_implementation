package com.rdas.model;

import java.io.Serializable;

public record LanguageData(
        String isoCode,
        String name
) implements Serializable {}
