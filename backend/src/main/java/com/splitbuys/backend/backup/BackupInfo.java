package com.splitbuys.backend.backup;

import java.time.Instant;

/** A backup file on the server. */
public record BackupInfo(String file, long sizeBytes, Instant createdAt) {
}
