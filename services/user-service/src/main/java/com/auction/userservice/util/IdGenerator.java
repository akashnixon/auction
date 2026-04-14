package com.auction.userservice.util;

import java.text.Normalizer;
import java.util.Locale;

public final class IdGenerator {

    private IdGenerator() {
    }

    public static String userIdFromUsername(String username) {
        return "user-" + slugify(username, 32);
    }

    private static String slugify(String value, int maxLength) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");

        if (normalized.isBlank()) {
            return "account";
        }

        return normalized.length() <= maxLength
            ? normalized
            : normalized.substring(0, maxLength).replaceAll("-+$", "");
    }
}
