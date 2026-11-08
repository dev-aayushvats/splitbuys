package com.splitbuys.backend;

import com.splitbuys.backend.support.UsesPostgres;

/** The same idempotency tests against PostgreSQL: the racing-duplicates case depends on the database's unique index and aborted-transaction rules. */
@UsesPostgres
class PostgresIdempotencyApiTest extends IdempotencyApiTest {
}
