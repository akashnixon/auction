package com.auction.auctionservice.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public final class IdGenerator {

    private IdGenerator() {
    }

    public static String auctionId(String itemName) {
        String slug = slugify(itemName, 12);
        String suffix = shortCode();
        return String.format("auc-%s-%s", slug, suffix);
    }

    private static String slugify(String value, int maxLength) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");

        if (normalized.isBlank()) {
            return "item";
        }

        return normalized.length() <= maxLength
            ? normalized
            : normalized.substring(0, maxLength).replaceAll("-+$", "");
    }

    private static String shortCode() {
        long value = Math.abs(UUID.randomUUID().getMostSignificantBits());
        String encoded = Long.toString(value, 36).toLowerCase(Locale.ROOT);
        return encoded.substring(Math.max(0, encoded.length() - 5));
    }
}
