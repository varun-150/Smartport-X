package com.smartportx.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmartPortxCommonTest {

    @Test
    void markerHolderCannotBeInstantiated() throws Exception {
        var constructor = SmartPortxCommon.class.getDeclaredConstructor();
        assertThat(constructor.canAccess(null)).as("utility class must have private constructor").isFalse();
    }
}