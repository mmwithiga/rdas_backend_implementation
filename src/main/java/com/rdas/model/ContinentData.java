package com.rdas.model;

import java.io.Serializable;

public record ContinentData(
        String code,
        String name
) implements Serializable {}
