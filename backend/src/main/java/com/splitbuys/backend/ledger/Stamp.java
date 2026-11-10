package com.splitbuys.backend.ledger;

import java.time.Instant;

/** A row's id and creation time: all that is needed to order rows from different tables before loading them. */
record Stamp(long id, Instant createdAt) {
}
