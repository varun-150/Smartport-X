package com.smartportx.container;

import com.smartportx.container.util.ContainerNumbers;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestContainerNumbers {

    private static final AtomicInteger SERIAL = new AtomicInteger(
            10_000 + ThreadLocalRandom.current().nextInt(0, 900_000));

    private TestContainerNumbers() {
    }

    public static String next() {
        int value = SERIAL.getAndIncrement();
        if (value > 999_999) {
            SERIAL.set(10_000 + ThreadLocalRandom.current().nextInt(0, 900_000));
            value = SERIAL.getAndIncrement();
        }
        String firstTen = "TSTU" + String.format("%06d", value);
        return firstTen + ContainerNumbers.checkDigit(firstTen);
    }

    public static String badCheckDigit() {
        return "MSKU1234567";
    }
}