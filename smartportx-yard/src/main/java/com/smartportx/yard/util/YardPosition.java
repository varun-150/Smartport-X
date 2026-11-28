package com.smartportx.yard.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YardPosition {

    private static final Pattern POSITION_PATTERN =
            Pattern.compile("^([A-Z0-9]+)-B(\\d+)-R(\\d+)-T(\\d+)$");

    private YardPosition() {
    }

    public static String format(String blockCode, int bay, int row, int tier) {
        return String.format("%s-B%02d-R%02d-T%02d", blockCode, bay, row, tier);
    }

    public static Coordinates parse(String position) {
        Matcher matcher = POSITION_PATTERN.matcher(position == null ? "" : position.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid yard position: " + position);
        }
        return new Coordinates(matcher.group(1), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)));
    }

    public record Coordinates(String blockCode, int bay, int row, int tier) {
    }
}