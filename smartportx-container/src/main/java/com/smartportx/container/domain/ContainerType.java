package com.smartportx.container.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.LinkedHashSet;
import java.util.Set;

public enum ContainerType {

    _20DC("20DC"), _40DC("40DC"), _40HC("40HC"), _45HC("45HC"),
    _20RF("20RF"), _40RF("40RF"), _20TK("20TK"), _40TK("40TK");

    private final String value;

    ContainerType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ContainerType from(String value) {
        for (ContainerType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unsupported container type '" + value + "'");
    }

    public static Set<String> supportedValues() {
        Set<String> values = new LinkedHashSet<>();
        for (ContainerType type : values()) {
            values.add(type.value);
        }
        return values;
    }
}