package com.splitbuys.backend.common.web;

import java.util.List;

/** One page of a newest-first list. {@code nextCursor} is null on the last page; pass it back as {@code ?cursor=}. */
public record PageResponse<T>(List<T> items, String nextCursor) {
}
