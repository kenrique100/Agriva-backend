package com.agriva.model.dto.common;

import java.util.List;

/**
 * Cursor (keyset) pagination envelope.
 * <p>
 * The client passes {@code cursor} back to get the next page. When
 * {@code hasNext} is false, {@code nextCursor} is null and the caller
 * knows to stop.
 * <p>
 * The cursor token is opaque — base64 of "sortValue::id" — so the
 * client never has to know what the sort column is. If you ever change
 * the sort field, the token format changes but the client is unaffected.
 */
public record CursorPageResponse<T>(
        List<T> content,
        String nextCursor,
        boolean hasNext,
        int size
) {
}