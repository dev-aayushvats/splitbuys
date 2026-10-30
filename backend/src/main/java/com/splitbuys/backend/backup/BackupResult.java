package com.splitbuys.backend.backup;

import java.time.Instant;
import java.util.Map;

/** What a finished backup contains. {@code rows} is the number of rows dumped per table. */
public record BackupResult(String file, long sizeBytes, Instant createdAt, String schemaVersion, Map<String, Long> rows) {
}
