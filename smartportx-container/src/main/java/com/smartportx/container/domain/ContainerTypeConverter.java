package com.smartportx.container.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ContainerTypeConverter implements AttributeConverter<ContainerType, String> {

    @Override
    public String convertToDatabaseColumn(ContainerType attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ContainerType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ContainerType.from(dbData);
    }
}