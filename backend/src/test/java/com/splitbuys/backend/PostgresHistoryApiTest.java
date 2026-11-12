package com.splitbuys.backend;

import com.splitbuys.backend.support.UsesPostgres;

/** The same history API tests, run against PostgreSQL instead of SQLite. */
@UsesPostgres
class PostgresHistoryApiTest extends HistoryApiTest {
}
