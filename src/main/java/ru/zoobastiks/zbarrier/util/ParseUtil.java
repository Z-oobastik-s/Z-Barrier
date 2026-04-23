package ru.zoobastiks.zbarrier.util;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

public final class ParseUtil {
    private ParseUtil() {
    }

    public static OptionalDouble parseDouble(String value) {
        try {
            return OptionalDouble.of(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }
    }

    public static OptionalLong parseLong(String value) {
        try {
            return OptionalLong.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    public static OptionalInt parseInt(String value) {
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }
}
