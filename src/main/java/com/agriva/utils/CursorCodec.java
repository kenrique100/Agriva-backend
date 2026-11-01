package com.agriva.utils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Encodes/decodes the cursor token used by keyset pagination.
 * <p>
 * Format (before base64): "{sortValue}|{id}"
 *   - LocalDate      → ISO_LOCAL_DATE       (2026-10-15)
 *   - LocalDateTime  → ISO_LOCAL_DATE_TIME  (2026-10-15T14:30:00)
 *   - UUID           → canonical form
 * <p>
 * The | separator is safe because ISO dates and UUIDs never contain it.
 */
public final class CursorCodec {

    private CursorCodec() {}

    public static String encode(LocalDate sortValue, UUID id) {
        return encodeRaw(sortValue.toString(), id);
    }

    public static String encode(LocalDateTime sortValue, UUID id) {
        return encodeRaw(sortValue.toString(), id);
    }

    private static String encodeRaw(String value, UUID id) {
        String raw = value + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decodeDate(String cursor) {
        String[] parts = splitRaw(cursor);
        return new Decoded(LocalDate.parse(parts[0]), UUID.fromString(parts[1]));
    }

    public static Decoded decodeDateTime(String cursor) {
        String[] parts = splitRaw(cursor);
        return new Decoded(LocalDateTime.parse(parts[0]), UUID.fromString(parts[1]));
    }

    private static String[] splitRaw(String cursor) {
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String[] parts = raw.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed cursor");
        }
        return parts;
    }

    /** Decoded (sortValue, id) pair. sortValue is a String — callers parse as needed. */
    public record Decoded(Object sortValue, UUID id) {}
}