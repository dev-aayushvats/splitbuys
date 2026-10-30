package com.splitbuys.backend;

import com.splitbuys.backend.support.UsesPostgres;

/** The same Ledger API tests, run against PostgreSQL instead of SQLite. */
@UsesPostgres
class PostgresLedgerApiTest extends LedgerApiTest {
}
