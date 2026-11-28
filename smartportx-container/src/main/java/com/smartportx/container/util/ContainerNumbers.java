package com.smartportx.container.util;

import java.util.Locale;

public final class ContainerNumbers {

    private ContainerNumbers() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String candidate) {
        if (candidate == null || candidate.length() != 11) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (!isLetter(candidate.charAt(i))) {
                return false;
            }
        }
        for (int i = 4; i < 11; i++) {
            if (!isDigit(candidate.charAt(i))) {
                return false;
            }
        }
        return checkDigit(candidate, 0, 10) == candidate.charAt(10) - '0';
    }

    public static int checkDigit(CharSequence firstTen) {
        if (firstTen == null || firstTen.length() != 10) {
            throw new IllegalArgumentException("container number prefix must be 10 characters");
        }
        for (int i = 0; i < 10; i++) {
            char c = firstTen.charAt(i);
            if (i < 4 ? !isLetter(c) : !isDigit(c)) {
                throw new IllegalArgumentException("invalid container number prefix");
            }
        }
        return checkDigit(firstTen, 0, 10);
    }

    private static int checkDigit(CharSequence value, int from, int to) {
        long sum = 0;
        int power = 1;
        for (int i = from; i < to; i++) {
            sum += (long) valueOf(value.charAt(i)) * power;
            power *= 2;
        }
        int digit = (int) (sum % 11);
        return digit == 10 ? 0 : digit;
    }

    private static int valueOf(char c) {
        if (isDigit(c)) {
            return c - '0';
        }
        return switch (c) {
            case 'A' -> 10;
            case 'B' -> 12;
            case 'C' -> 13;
            case 'D' -> 14;
            case 'E' -> 15;
            case 'F' -> 16;
            case 'G' -> 17;
            case 'H' -> 18;
            case 'I' -> 19;
            case 'J' -> 20;
            case 'K' -> 21;
            case 'L' -> 23;
            case 'M' -> 24;
            case 'N' -> 25;
            case 'O' -> 26;
            case 'P' -> 27;
            case 'Q' -> 28;
            case 'R' -> 29;
            case 'S' -> 30;
            case 'T' -> 31;
            case 'U' -> 32;
            case 'V' -> 34;
            case 'W' -> 35;
            case 'X' -> 36;
            case 'Y' -> 37;
            case 'Z' -> 38;
            default -> 0;
        };
    }

    private static boolean isLetter(char c) {
        return c >= 'A' && c <= 'Z';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}